import { create } from 'zustand';
import { describeMidiMessage, type MidiKind } from '../pacer';

export interface MonitorEntry {
  id: number;
  direction: 'in' | 'out';
  time: number;
  port: string;
  data: Uint8Array;
  kind: MidiKind;
  summary: string;
  badChecksum: boolean;
}

export type MonitorFilter = 'all' | 'sysex' | 'channel';

export interface MonitorState {
  entries: MonitorEntry[];
  total: number;
  paused: boolean;
  filter: MonitorFilter;
  showRealtime: boolean;
  add: (event: { direction: 'in' | 'out'; time: number; port: string; data: Uint8Array }) => void;
  clear: () => void;
  setPaused: (paused: boolean) => void;
  setFilter: (filter: MonitorFilter) => void;
  setShowRealtime: (show: boolean) => void;
}

const MAX_ENTRIES = 600;
const FLUSH_MS = 120;

let nextId = 1;
let buffer: MonitorEntry[] = [];
let flushTimer: ReturnType<typeof setTimeout> | null = null;

export const useMonitor = create<MonitorState>()((set, get) => ({
  entries: [],
  total: 0,
  paused: false,
  filter: 'all',
  showRealtime: false,

  add: (event) => {
    if (get().paused) return;
    const description = describeMidiMessage(event.data);
    if (description.kind === 'realtime' && !get().showRealtime) return;
    buffer.push({
      id: nextId++,
      ...event,
      kind: description.kind,
      summary: description.summary,
      badChecksum: description.badChecksum ?? false,
    });
    if (flushTimer === null) {
      flushTimer = setTimeout(() => {
        flushTimer = null;
        const added = buffer;
        buffer = [];
        set((s) => ({ entries: [...s.entries, ...added].slice(-MAX_ENTRIES), total: s.total + added.length }));
      }, FLUSH_MS);
    }
  },
  clear: () => {
    buffer = [];
    set({ entries: [], total: 0 });
  },
  setPaused: (paused) => set({ paused }),
  setFilter: (filter) => set({ filter }),
  setShowRealtime: (showRealtime) => set({ showRealtime }),
}));
