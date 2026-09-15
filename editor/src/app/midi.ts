import { IDENTITY_REQUEST, MidiService, browserRequestAccess, isIdentityReply, parseIdentityReply } from '../midi';
import { activeGlobalChannel, createFollower, isPacerMessage } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { useEditor } from '../store/editor';
import { useMonitor } from '../store/monitor';
import { useUi } from '../store/ui';

/** The single Web MIDI service of the app. Nothing is requested until `connectMidi()` runs. */
export const midi = new MidiService(browserRequestAccess());

midi.onMonitor((event) => useMonitor.getState().add(event));

// ---------------------------------------------------------------------------------------------
// Device identity: one Universal Identity Request per newly opened port pair
// ---------------------------------------------------------------------------------------------

let identifiedPorts: string | null = null;

async function identify(ports: string): Promise<void> {
  const device = useDevice.getState();
  if (device.operation) {
    identifiedPorts = null; // try again on the next state change
    return;
  }
  device.setIdentity({ status: 'pending' });
  try {
    const result = await midi.request(IDENTITY_REQUEST, {
      accept: isIdentityReply,
      expected: 1,
      firstReplyTimeoutMs: 1500,
      settleMs: 20,
    });
    if (identifiedPorts !== ports) return;
    const identity = result.messages.map(parseIdentityReply).find((id) => id !== null) ?? null;
    useDevice.getState().setIdentity(identity ? { status: 'ok', identity } : { status: 'none' });
  } catch {
    if (identifiedPorts === ports) useDevice.getState().setIdentity({ status: 'none' });
  }
}

midi.subscribe((snapshot) => {
  useDevice.getState().setMidi(snapshot);
  const ports = isConnected(snapshot) ? `${snapshot.inputId}|${snapshot.outputId}` : null;
  if (ports === null) {
    if (identifiedPorts !== null) {
      identifiedPorts = null;
      useDevice.getState().setIdentity({ status: 'unknown' });
    }
  } else if (ports !== identifiedPorts) {
    identifiedPorts = ports;
    setTimeout(() => void identify(ports), 150);
  }
});

// ---------------------------------------------------------------------------------------------
// Hardware follow: press a switch on the Pacer to select it in the editor
// ---------------------------------------------------------------------------------------------

const follower = createFollower();

midi.onMessage((message) => {
  const ui = useUi.getState();
  if (!ui.follow || useDevice.getState().operation) return;
  if (message[0] === 0xf0 && (isPacerMessage(message) || isIdentityReply(message))) return;
  const editor = useEditor.getState();
  const preset = editor.preview ? editor.preview.preset : editor.slots[editor.selectedSlot].preset;
  const globals = editor.globals.device ?? editor.globals.working;
  const key = follower.match(preset, message, activeGlobalChannel(globals));
  if (!key) return;
  if (ui.view === 'editor' && (editor.selection.kind !== 'control' || editor.selection.key !== key)) {
    editor.select({ kind: 'control', key });
  }
  ui.setFlash(key);
});

// ---------------------------------------------------------------------------------------------

const GRANTED_KEY = 'pacer-studio.midi-connected';

/** Auto-connect on start-up only after the user connected once (never on a first visit). */
export function shouldAutoConnect(): boolean {
  try {
    return localStorage.getItem(GRANTED_KEY) === '1';
  } catch {
    return false;
  }
}

export async function connectMidi(): Promise<void> {
  await midi.connect();
  const access = midi.snapshot.access;
  try {
    if (access === 'ready') localStorage.setItem(GRANTED_KEY, '1');
    else if (access === 'denied') localStorage.removeItem(GRANTED_KEY);
  } catch {
    // ignore
  }
}

export async function disconnectMidi(): Promise<void> {
  await midi.disconnect();
  try {
    localStorage.removeItem(GRANTED_KEY);
  } catch {
    // ignore
  }
}
