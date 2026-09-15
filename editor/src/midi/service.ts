import { CMD_SET, isPacerMessage } from '../pacer';
import { MidiError } from './errors';
import { isPacerPort, isPacerPrimaryPort, pickPort } from './ports';
import { MidiStreamParser } from './stream';
import { collectReplies, sendQueue, type CollectOptions, type CollectResult, type SendQueueOptions } from './transfer';

// Minimal structural views of the Web MIDI API (so the service can be tested with fakes).
export interface MidiPortLike {
  readonly id: string;
  readonly name: string | null;
  readonly manufacturer: string | null;
  readonly state: string;
  readonly connection: string;
  open(): Promise<unknown>;
  close(): Promise<unknown>;
}
export interface MidiMessageEventLike {
  readonly data: Uint8Array | null;
}
export interface MidiInputLike extends MidiPortLike {
  onmidimessage: ((event: MidiMessageEventLike) => void) | null;
}
export interface MidiOutputLike extends MidiPortLike {
  send(data: number[]): void;
}
export interface PortMapLike<T> {
  forEach(callback: (port: T, key: string) => void): void;
}
export interface MidiAccessLike {
  readonly inputs: PortMapLike<MidiInputLike>;
  readonly outputs: PortMapLike<MidiOutputLike>;
  onstatechange: ((event: { readonly port: MidiPortLike | null }) => void) | null;
  readonly sysexEnabled?: boolean;
}
export type RequestAccess = () => Promise<MidiAccessLike>;

export type AccessState = 'idle' | 'unsupported' | 'requesting' | 'ready' | 'denied' | 'error';
export type PortStatus = 'none' | 'opening' | 'open' | 'busy' | 'disconnected';

export interface PortInfo {
  id: string;
  name: string;
  manufacturer: string;
  connected: boolean;
  pacer: boolean;
  primary: boolean;
}

export interface MidiSnapshot {
  access: AccessState;
  accessError: string | null;
  inputs: PortInfo[];
  outputs: PortInfo[];
  inputId: string | null;
  outputId: string | null;
  inputName: string | null;
  outputName: string | null;
  inputStatus: PortStatus;
  outputStatus: PortStatus;
  portError: string | null;
}

export interface MonitorEvent {
  direction: 'in' | 'out';
  data: Uint8Array;
  time: number;
  port: string;
}

export const INITIAL_MIDI_SNAPSHOT: MidiSnapshot = {
  access: 'idle',
  accessError: null,
  inputs: [],
  outputs: [],
  inputId: null,
  outputId: null,
  inputName: null,
  outputName: null,
  inputStatus: 'none',
  outputStatus: 'none',
  portError: null,
};

/** Web MIDI access with SysEx, or null when the browser has no Web MIDI. */
export function browserRequestAccess(): RequestAccess | null {
  if (typeof navigator === 'undefined' || typeof navigator.requestMIDIAccess !== 'function') return null;
  return () => navigator.requestMIDIAccess({ sysex: true }) as unknown as Promise<MidiAccessLike>;
}

function listPorts<T>(map: PortMapLike<T>): T[] {
  const out: T[] = [];
  map.forEach((port) => out.push(port));
  return out;
}

function portInfo(p: MidiPortLike): PortInfo {
  const name = p.name ?? p.id;
  return {
    id: p.id,
    name,
    manufacturer: p.manufacturer ?? '',
    connected: p.state !== 'disconnected',
    pacer: isPacerPort(name),
    primary: isPacerPrimaryPort(name),
  };
}

function errorName(err: unknown): string {
  return typeof err === 'object' && err !== null && 'name' in err ? String((err as { name: unknown }).name) : '';
}

/** Pacer replies are SET messages. */
export function isPacerReply(message: Uint8Array): boolean {
  return isPacerMessage(message) && message[5] === CMD_SET;
}

export class MidiService {
  private access: MidiAccessLike | null = null;
  private input: MidiInputLike | null = null;
  private output: MidiOutputLike | null = null;
  private inputError: string | null = null;
  private outputError: string | null = null;
  private preferredInputName: string | null = null;
  private preferredOutputName: string | null = null;
  private readonly parser = new MidiStreamParser();
  private readonly stateListeners = new Set<(snapshot: MidiSnapshot) => void>();
  private readonly messageListeners = new Set<(message: Uint8Array) => void>();
  private readonly monitorListeners = new Set<(event: MonitorEvent) => void>();
  private snap: MidiSnapshot = INITIAL_MIDI_SNAPSHOT;
  private readonly requestAccess: RequestAccess | null;
  private readonly now: () => number;

  constructor(requestAccess: RequestAccess | null, now: () => number = () => Date.now()) {
    this.requestAccess = requestAccess;
    this.now = now;
  }

  get snapshot(): MidiSnapshot {
    return this.snap;
  }

  get supported(): boolean {
    return this.requestAccess !== null;
  }

  subscribe(listener: (snapshot: MidiSnapshot) => void): () => void {
    this.stateListeners.add(listener);
    return () => this.stateListeners.delete(listener);
  }

  onMessage(listener: (message: Uint8Array) => void): () => void {
    this.messageListeners.add(listener);
    return () => this.messageListeners.delete(listener);
  }

  onMonitor(listener: (event: MonitorEvent) => void): () => void {
    this.monitorListeners.add(listener);
    return () => this.monitorListeners.delete(listener);
  }

  private update(patch: Partial<MidiSnapshot>): void {
    this.snap = { ...this.snap, ...patch, portError: this.inputError ?? this.outputError };
    for (const l of this.stateListeners) l(this.snap);
  }

  async connect(): Promise<void> {
    if (!this.requestAccess) {
      this.update({
        access: 'unsupported',
        accessError: 'This browser has no Web MIDI support. Use Chrome, Edge or Opera on desktop.',
      });
      return;
    }
    if (this.access) {
      this.refreshPorts();
      await this.reconcile();
      return;
    }
    this.update({ access: 'requesting', accessError: null });
    try {
      const access = await this.requestAccess();
      this.access = access;
      access.onstatechange = (event) => this.handleStateChange(event.port);
      this.update({ access: 'ready' });
      this.refreshPorts();
      await this.reconcile();
    } catch (err) {
      const name = errorName(err);
      if (name === 'SecurityError' || name === 'NotAllowedError') {
        this.update({
          access: 'denied',
          accessError:
            'MIDI access was denied. Allow "MIDI devices" (with SysEx) for this site in the browser settings, then reload.',
        });
      } else if (name === 'NotSupportedError') {
        this.update({ access: 'unsupported', accessError: 'Web MIDI with SysEx is not supported here.' });
      } else {
        this.update({
          access: 'error',
          accessError: err instanceof Error ? err.message : 'Could not access MIDI devices.',
        });
      }
    }
  }

  async disconnect(): Promise<void> {
    await this.openInput(null);
    await this.openOutput(null);
    if (this.access) this.access.onstatechange = null;
    this.access = null;
    this.inputError = null;
    this.outputError = null;
    this.update({ ...INITIAL_MIDI_SNAPSHOT });
  }

  /** Retry opening the selected (or auto-detected) ports, e.g. after closing another MIDI app. */
  async retry(): Promise<void> {
    if (!this.access) return this.connect();
    this.refreshPorts();
    if (this.snap.inputStatus !== 'open') {
      const port = this.input;
      this.input = null;
      await this.openInput(port ?? pickPort(this.connectedInputs(), this.preferredInputName) ?? null);
    }
    if (this.snap.outputStatus !== 'open') {
      const port = this.output;
      this.output = null;
      await this.openOutput(port ?? pickPort(this.connectedOutputs(), this.preferredOutputName) ?? null);
    }
  }

  async selectInput(id: string | null): Promise<void> {
    const port = id && this.access ? listPorts(this.access.inputs).find((p) => p.id === id) : undefined;
    this.preferredInputName = port?.name ?? null;
    await this.openInput(port ?? null);
  }

  async selectOutput(id: string | null): Promise<void> {
    const port = id && this.access ? listPorts(this.access.outputs).find((p) => p.id === id) : undefined;
    this.preferredOutputName = port?.name ?? null;
    await this.openOutput(port ?? null);
  }

  get canSend(): boolean {
    return this.output !== null && this.snap.outputStatus === 'open';
  }

  get canReceive(): boolean {
    return this.input !== null && this.snap.inputStatus === 'open';
  }

  send(message: Uint8Array): void {
    const output = this.output;
    if (!output || this.snap.outputStatus !== 'open') {
      throw new MidiError('no-output', 'No MIDI output is open. Connect the Pacer and select its port.');
    }
    this.emitMonitor('out', message, output);
    try {
      output.send(Array.from(message));
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      throw new MidiError('send-failed', `Sending failed: ${msg}`);
    }
  }

  /** Send a Control Change. `channel` is 1..16. */
  sendControlChange(channel: number, controller: number, value: number): void {
    this.send(Uint8Array.of(0xb0 | ((channel - 1) & 0x0f), controller & 0x7f, value & 0x7f));
  }

  /** Send a request and collect the Pacer's SysEx replies. */
  request(requestMessage: Uint8Array, options: CollectOptions = {}): Promise<CollectResult> {
    if (!this.canReceive) {
      return Promise.reject(new MidiError('no-input', 'No MIDI input is open. Connect the Pacer and select its port.'));
    }
    return collectReplies(
      (listener) => this.onMessage(listener),
      () => this.send(requestMessage),
      { accept: isPacerReply, ...options },
    );
  }

  sendAll(messages: readonly Uint8Array[], options: SendQueueOptions = {}): Promise<void> {
    return sendQueue(messages, (m) => this.send(m), options);
  }

  // -------------------------------------------------------------------------------------------

  private connectedInputs(): MidiInputLike[] {
    return this.access ? listPorts(this.access.inputs).filter((p) => p.state !== 'disconnected') : [];
  }

  private connectedOutputs(): MidiOutputLike[] {
    return this.access ? listPorts(this.access.outputs).filter((p) => p.state !== 'disconnected') : [];
  }

  private refreshPorts(): void {
    if (!this.access) return;
    this.update({
      inputs: listPorts(this.access.inputs).map(portInfo),
      outputs: listPorts(this.access.outputs).map(portInfo),
    });
  }

  private handleStateChange(port: MidiPortLike | null): void {
    this.refreshPorts();
    if (port && port.state === 'disconnected') {
      if (this.input && port.id === this.input.id) this.update({ inputStatus: 'disconnected' });
      if (this.output && port.id === this.output.id) this.update({ outputStatus: 'disconnected' });
    }
    void this.reconcile();
  }

  /** Keep the selected ports open across hot-plugs and auto-select the Pacer when nothing is selected. */
  private async reconcile(): Promise<void> {
    if (!this.access) return;

    const inputs = this.connectedInputs();
    if (!this.input) {
      const candidate = pickPort(inputs, this.preferredInputName);
      if (candidate) await this.openInput(candidate);
    } else if (this.snap.inputStatus === 'disconnected' || !inputs.includes(this.input)) {
      const current = this.input;
      const replacement = inputs.find((p) => p.id === current.id) ?? inputs.find((p) => p.name === current.name);
      if (replacement) {
        await this.openInput(replacement, true);
      } else if (this.snap.inputStatus !== 'disconnected') {
        this.update({ inputStatus: 'disconnected' });
      }
    }

    const outputs = this.connectedOutputs();
    if (!this.output) {
      const candidate = pickPort(outputs, this.preferredOutputName);
      if (candidate) await this.openOutput(candidate);
    } else if (this.snap.outputStatus === 'disconnected' || !outputs.includes(this.output)) {
      const current = this.output;
      const replacement = outputs.find((p) => p.id === current.id) ?? outputs.find((p) => p.name === current.name);
      if (replacement) {
        await this.openOutput(replacement, true);
      } else if (this.snap.outputStatus !== 'disconnected') {
        this.update({ outputStatus: 'disconnected' });
      }
    }
  }

  private async openInput(port: MidiInputLike | null, force = false): Promise<void> {
    const previous = this.input;
    if (previous && (previous !== port || force)) {
      previous.onmidimessage = null;
      if (previous !== port) {
        try {
          await previous.close();
        } catch {
          // ignore
        }
      }
    }
    this.input = port;
    this.parser.reset();
    this.inputError = null;
    if (!port) {
      this.update({ inputId: null, inputName: null, inputStatus: 'none' });
      return;
    }
    const name = port.name ?? port.id;
    this.update({ inputId: port.id, inputName: name, inputStatus: 'opening' });
    try {
      port.onmidimessage = (event) => this.handleInput(port, event);
      await port.open();
      if (this.input !== port) return;
      if (port.connection === 'open') {
        this.update({ inputStatus: 'open' });
      } else {
        this.inputError = `Input "${name}" could not be opened (${port.connection}).`;
        this.update({ inputStatus: 'busy' });
      }
    } catch {
      if (this.input !== port) return;
      port.onmidimessage = null;
      this.inputError = `Input "${name}" is busy or unavailable. Close other applications using it (e.g. Bitwig) and retry.`;
      this.update({ inputStatus: 'busy' });
    }
  }

  private async openOutput(port: MidiOutputLike | null, force = false): Promise<void> {
    const previous = this.output;
    if (previous && previous !== port && !force) {
      try {
        await previous.close();
      } catch {
        // ignore
      }
    }
    this.output = port;
    this.outputError = null;
    if (!port) {
      this.update({ outputId: null, outputName: null, outputStatus: 'none' });
      return;
    }
    const name = port.name ?? port.id;
    this.update({ outputId: port.id, outputName: name, outputStatus: 'opening' });
    try {
      await port.open();
      if (this.output !== port) return;
      if (port.connection === 'open') {
        this.update({ outputStatus: 'open' });
      } else {
        this.outputError = `Output "${name}" could not be opened (${port.connection}).`;
        this.update({ outputStatus: 'busy' });
      }
    } catch {
      if (this.output !== port) return;
      this.outputError = `Output "${name}" is busy or unavailable. Close other applications using it (e.g. Bitwig) and retry.`;
      this.update({ outputStatus: 'busy' });
    }
  }

  private handleInput(port: MidiInputLike, event: MidiMessageEventLike): void {
    if (port !== this.input || !event.data) return;
    for (const message of this.parser.push(event.data)) {
      this.emitMonitor('in', message, port);
      for (const l of this.messageListeners) l(message);
    }
  }

  private emitMonitor(direction: 'in' | 'out', data: Uint8Array, port: MidiPortLike): void {
    if (this.monitorListeners.size === 0) return;
    const event: MonitorEvent = { direction, data, time: this.now(), port: port.name ?? port.id };
    for (const l of this.monitorListeners) l(event);
  }
}
