import { create } from 'zustand';
import type { DeviceIdentity } from '../midi/identity';
import { INITIAL_MIDI_SNAPSHOT, type MidiSnapshot } from '../midi/service';

export type OperationKind = 'read' | 'write' | 'backup' | 'verify';

export interface Operation {
  kind: OperationKind;
  label: string;
  done: number;
  total: number;
  cancel?: () => void;
}

export interface SessionBackup {
  bytes: Uint8Array;
  messages: number;
  time: number;
  downloaded: boolean;
}

export type IdentityState =
  | { status: 'unknown' }
  | { status: 'pending' }
  | { status: 'ok'; identity: DeviceIdentity }
  | { status: 'none' };

export interface DeviceState {
  midi: MidiSnapshot;
  operation: Operation | null;
  /** Full backup read from the Pacer during this session. */
  backup: SessionBackup | null;
  /** The user explicitly chose to write without a backup in this session. */
  backupSkipped: boolean;
  writesThisSession: number;
  /** Universal Identity reply of the connected device. */
  identity: IdentityState;

  setMidi: (midi: MidiSnapshot) => void;
  setOperation: (operation: Operation | null) => void;
  progress: (done: number, total?: number) => void;
  setBackup: (backup: SessionBackup | null) => void;
  markBackupDownloaded: () => void;
  skipBackup: () => void;
  countWrite: () => void;
  setIdentity: (identity: IdentityState) => void;
}

export const useDevice = create<DeviceState>()((set) => ({
  midi: INITIAL_MIDI_SNAPSHOT,
  operation: null,
  backup: null,
  backupSkipped: false,
  writesThisSession: 0,
  identity: { status: 'unknown' },

  setMidi: (midi) => set({ midi }),
  setOperation: (operation) => set({ operation }),
  progress: (done, total) =>
    set((s) => (s.operation ? { operation: { ...s.operation, done, total: total ?? s.operation.total } } : s)),
  setBackup: (backup) => set({ backup }),
  markBackupDownloaded: () => set((s) => (s.backup ? { backup: { ...s.backup, downloaded: true } } : s)),
  skipBackup: () => set({ backupSkipped: true }),
  countWrite: () => set((s) => ({ writesThisSession: s.writesThisSession + 1 })),
  setIdentity: (identity) => set({ identity }),
}));

export function isConnected(midi: MidiSnapshot): boolean {
  return midi.inputStatus === 'open' && midi.outputStatus === 'open';
}
