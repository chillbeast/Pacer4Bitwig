import { MidiService, browserRequestAccess } from '../midi';
import { useDevice } from '../store/device';
import { useMonitor } from '../store/monitor';

/** The single Web MIDI service of the app. Nothing is requested until `connectMidi()` runs. */
export const midi = new MidiService(browserRequestAccess());

midi.subscribe((snapshot) => useDevice.getState().setMidi(snapshot));
midi.onMonitor((event) => useMonitor.getState().add(event));

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
