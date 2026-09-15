import { describe, expect, it } from 'vitest';
import {
  MidiService,
  type MidiAccessLike,
  type MidiInputLike,
  type MidiMessageEventLike,
  type MidiOutputLike,
  type MidiPortLike,
} from '../src/midi';
import { parseDump, requestPreset, splitSysex, concatMessages } from '../src/pacer';
import { fixture } from './helpers';

// Fake Web MIDI — nothing here touches real hardware.
class FakePort implements MidiPortLike {
  manufacturer = 'Nektar';
  state = 'connected';
  connection = 'closed';
  failOpen = false;
  constructor(
    readonly id: string,
    readonly name: string,
  ) {}
  async open() {
    if (this.failOpen) throw Object.assign(new Error('Port busy'), { name: 'InvalidAccessError' });
    this.connection = 'open';
    return this;
  }
  async close() {
    this.connection = 'closed';
    return this;
  }
}
class FakeInput extends FakePort implements MidiInputLike {
  onmidimessage: ((event: MidiMessageEventLike) => void) | null = null;
  emit(data: ArrayLike<number>) {
    this.onmidimessage?.({ data: Uint8Array.from(data) });
  }
}
class FakeOutput extends FakePort implements MidiOutputLike {
  sent: number[][] = [];
  onSend: ((data: number[]) => void) | null = null;
  send(data: number[]) {
    if (this.connection !== 'open') throw new Error('closed');
    this.sent.push(data);
    this.onSend?.(data);
  }
}
class FakeAccess implements MidiAccessLike {
  inputs = new Map<string, FakeInput>();
  outputs = new Map<string, FakeOutput>();
  onstatechange: ((event: { readonly port: MidiPortLike | null }) => void) | null = null;
  sysexEnabled = true;
}

const tick = () => new Promise((r) => setTimeout(r, 0));

function setup() {
  const access = new FakeAccess();
  const in1 = new FakeInput('in-1', 'PACER');
  const in2 = new FakeInput('in-2', 'MIDIIN2 (PACER)');
  const out1 = new FakeOutput('out-1', 'PACER');
  const out2 = new FakeOutput('out-2', 'MIDIOUT2 (PACER)');
  const synth = new FakeOutput('out-3', 'Microsoft GS Wavetable Synth');
  access.inputs.set(in2.id, in2).set(in1.id, in1);
  access.outputs.set(synth.id, synth).set(out2.id, out2).set(out1.id, out1);
  const service = new MidiService(async () => access);
  return { access, in1, in2, out1, out2, synth, service };
}

describe('MidiService', () => {
  it('reports missing Web MIDI support', async () => {
    const service = new MidiService(null);
    await service.connect();
    expect(service.snapshot.access).toBe('unsupported');
  });

  it('reports denied permission', async () => {
    const service = new MidiService(async () => {
      throw Object.assign(new Error('denied'), { name: 'SecurityError' });
    });
    await service.connect();
    expect(service.snapshot.access).toBe('denied');
    expect(service.snapshot.accessError).toMatch(/denied/i);
  });

  it('auto-selects the Pacer port 1 for input and output', async () => {
    const { service } = setup();
    await service.connect();
    const s = service.snapshot;
    expect(s.access).toBe('ready');
    expect(s.inputName).toBe('PACER');
    expect(s.outputName).toBe('PACER');
    expect(s.inputStatus).toBe('open');
    expect(s.outputStatus).toBe('open');
    expect(s.inputs.find((p) => p.name === 'PACER')?.primary).toBe(true);
  });

  it('reports a busy port', async () => {
    const { service, in1 } = setup();
    in1.failOpen = true;
    await service.connect();
    expect(service.snapshot.inputStatus).toBe('busy');
    expect(service.snapshot.portError).toMatch(/busy/i);
    in1.failOpen = false;
    await service.retry();
    expect(service.snapshot.inputStatus).toBe('open');
  });

  it('allows manual port selection', async () => {
    const { service, synth } = setup();
    await service.connect();
    await service.selectOutput(synth.id);
    expect(service.snapshot.outputName).toBe('Microsoft GS Wavetable Synth');
    service.sendControlChange(16, 102, 127);
    expect(synth.sent).toEqual([[0xbf, 102, 127]]);
  });

  it('reassembles replies and collects a preset dump', async () => {
    const { service, in1, out1 } = setup();
    await service.connect();
    const dump = splitSysex(fixture('A1.factory.syx'));
    out1.onSend = () => {
      // deliver in awkward chunks
      const all = concatMessages(dump);
      for (let i = 0; i < all.length; i += 100) in1.emit(all.subarray(i, i + 100));
    };
    const monitor: string[] = [];
    service.onMonitor((e) => monitor.push(e.direction));
    const result = await service.request(requestPreset(1), { expected: 189, settleMs: 1 });
    expect(result.complete).toBe(true);
    expect(out1.sent[0]).toEqual(Array.from(requestPreset(1)));
    expect(parseDump(concatMessages(result.messages)).presets.get(1)?.complete).toBe(true);
    expect(monitor[0]).toBe('out');
    expect(monitor.filter((d) => d === 'in')).toHaveLength(189);
  });

  it('follows hot-unplug and re-plug', async () => {
    const { service, access, in1, out1 } = setup();
    await service.connect();

    access.inputs.delete(in1.id);
    access.outputs.delete(out1.id);
    in1.state = 'disconnected';
    out1.state = 'disconnected';
    access.onstatechange?.({ port: in1 });
    access.onstatechange?.({ port: out1 });
    await tick();
    expect(service.snapshot.inputStatus).toBe('disconnected');
    expect(service.snapshot.outputStatus).toBe('disconnected');
    expect(() => service.send(requestPreset(1))).toThrow(/No MIDI output/);

    const newIn = new FakeInput('in-9', 'PACER');
    const newOut = new FakeOutput('out-9', 'PACER');
    access.inputs.set(newIn.id, newIn);
    access.outputs.set(newOut.id, newOut);
    access.onstatechange?.({ port: newIn });
    await tick();
    access.onstatechange?.({ port: newOut });
    await tick();
    expect(service.snapshot.inputStatus).toBe('open');
    expect(service.snapshot.outputStatus).toBe('open');
    expect(service.snapshot.inputId).toBe('in-9');
    expect(service.snapshot.outputId).toBe('out-9');
  });

  it('refuses requests without an input', async () => {
    const service = new MidiService(async () => new FakeAccess());
    await service.connect();
    await expect(service.request(requestPreset(1))).rejects.toMatchObject({ code: 'no-input' });
  });
});
