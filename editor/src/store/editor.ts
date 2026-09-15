import { create } from 'zustand';
import {
  CONTROL_BY_KEY,
  D6_INDEX,
  LAST_STORED_PRESET,
  MSG,
  SLOT_COUNT,
  clampByte,
  clampChannel,
  clonePreset,
  createPreset,
  diffParts,
  globalWriteParts,
  globalsEqual,
  msgTypeInfo,
  setGlobalValue as setGlobalElement,
  normalizeName,
  presetsEqual,
  slotLabel,
  type ControlKey,
  type ControlLabels,
  type DataBytes,
  type EncodedPart,
  type GlobalSettings,
  type GlobalWritePart,
  type Led,
  type MidiSetting,
  type Preset,
  type Step,
} from '../pacer';

export type SlotSource = 'device' | 'file' | 'template' | 'new' | 'copy';

export interface Slot {
  /** Working copy shown in the editor. */
  preset: Preset | null;
  /** Last clean point (device read, file import or successful write) — drives the "edited" dot. */
  base: Preset | null;
  /** Last known content of this slot on the Pacer (read from or written to the device). */
  device: Preset | null;
  source: SlotSource | null;
  /** Editor-only switch labels (saved in JSON exports, never sent to the Pacer). */
  labels: ControlLabels;
  /** False when the data came from an incomplete dump (missing parts use defaults). */
  complete: boolean;
}

export type Selection = { kind: 'control'; key: ControlKey } | { kind: 'preset' };

export interface GlobalsSlot {
  working: GlobalSettings | null;
  base: GlobalSettings | null;
  device: GlobalSettings | null;
  source: 'device' | 'file' | null;
}

const EMPTY_GLOBALS: GlobalsSlot = { working: null, base: null, device: null, source: null };

export interface PresetPreview {
  preset: Preset;
  labels: ControlLabels;
  slot: number;
  title: string;
}

interface HistoryEntry {
  label: string;
  slots: Slot[];
  globals: GlobalsSlot;
  selectedSlot: number;
  selection: Selection;
  coalesceKey?: string;
  time: number;
}

export interface LoadEntry {
  index: number;
  preset: Preset;
  labels?: ControlLabels;
  complete?: boolean;
}

export interface EditorState {
  slots: Slot[];
  globals: GlobalsSlot;
  /** Edit one element of a global config (idx 1..4). */
  setGlobal: (index: number, obj: number, elm: number, value: number) => void;
  /** Load global settings from a file or the device (replaces the working copy). */
  loadGlobals: (settings: GlobalSettings, source: 'device' | 'file') => void;
  /** Record global settings as present on the device (after a write or read-back). */
  setDeviceGlobals: (settings: GlobalSettings | null, updateBase: boolean) => void;
  selectedSlot: number;
  selection: Selection;
  clipboard: { preset: Preset; labels: ControlLabels; from: number } | null;
  preview: PresetPreview | null;
  past: HistoryEntry[];
  future: HistoryEntry[];

  selectSlot: (index: number) => void;
  select: (selection: Selection) => void;
  /** Edit a slot's working preset (creating a blank one if the slot is empty). */
  edit: (index: number, label: string, recipe: (draft: Preset) => void, coalesceKey?: string) => void;
  setName: (index: number, name: string) => void;
  setControlMode: (index: number, key: ControlKey, mode: number) => void;
  updateStep: (index: number, key: ControlKey, step: number, patch: Partial<Step>) => void;
  setStepType: (index: number, key: ControlKey, step: number, msgType: number) => void;
  updateLed: (index: number, key: ControlKey, step: number, patch: Partial<Led>) => void;
  updateMidi: (index: number, setting: number, patch: Partial<MidiSetting>) => void;
  setMidiType: (index: number, setting: number, msgType: number) => void;
  setLabel: (index: number, key: ControlKey, label: string) => void;

  loadPresets: (entries: readonly LoadEntry[], source: Exclude<SlotSource, 'device'>, label: string) => void;
  loadFromDevice: (entries: readonly LoadEntry[]) => void;
  markWritten: (index: number, preset: Preset) => void;
  forgetDevice: () => void;

  newPreset: (index: number) => void;
  copySlot: (index: number) => void;
  pasteSlot: (index: number) => void;
  copySlotTo: (from: number, to: number) => void;
  swapSlots: (a: number, b: number) => void;
  duplicateSlot: (index: number) => number | null;

  startPreview: (preview: PresetPreview) => void;
  updatePreview: (preview: PresetPreview) => void;
  cancelPreview: () => void;
  applyPreview: () => void;

  undo: () => void;
  redo: () => void;
}

const MAX_HISTORY = 200;
const COALESCE_MS = 1200;

function emptySlot(): Slot {
  return { preset: null, base: null, device: null, source: null, labels: {}, complete: true };
}

export function initialSlots(): Slot[] {
  return Array.from({ length: SLOT_COUNT }, emptySlot);
}

function pushHistory(state: EditorState, label: string, coalesceKey?: string): HistoryEntry[] {
  const now = Date.now();
  const last = state.past[state.past.length - 1];
  if (coalesceKey && last && last.coalesceKey === coalesceKey && now - last.time < COALESCE_MS) {
    return [...state.past.slice(0, -1), { ...last, time: now }];
  }
  const entry: HistoryEntry = {
    label,
    slots: state.slots,
    globals: state.globals,
    selectedSlot: state.selectedSlot,
    selection: state.selection,
    coalesceKey,
    time: now,
  };
  return [...state.past.slice(-(MAX_HISTORY - 1)), entry];
}

/** History restores working copies; device knowledge and clean points are external facts and stay. */
function restoreSlots(snapshot: readonly Slot[], current: readonly Slot[]): Slot[] {
  return snapshot.map((s, i) =>
    s.device === current[i].device && s.base === current[i].base
      ? s
      : { ...s, device: current[i].device, base: current[i].base },
  );
}

function restoreGlobals(snapshot: GlobalsSlot, current: GlobalsSlot): GlobalsSlot {
  return { ...snapshot, base: current.base, device: current.device };
}

function clampData(data: readonly number[]): DataBytes {
  return [clampByte(data[0]), clampByte(data[1]), clampByte(data[2])];
}

/** Sensible data bytes when switching message type. */
export function defaultDataFor(msgType: number, previous: { msgType: number; data: readonly number[] }): DataBytes {
  const prevInfo = msgTypeInfo(previous.msgType);
  const keepCc =
    previous.msgType !== MSG.OFF && prevInfo.fields[0].kind === 'cc' && msgTypeInfo(msgType).fields[0].kind === 'cc';
  const cc = keepCc ? previous.data[0] : 0;
  switch (msgType) {
    case MSG.SW_CC_TRIGGER:
    case MSG.SW_CC_TOGGLE:
    case MSG.LOAD_CC:
      return [cc, 127, 0];
    case MSG.SW_CC_STEP:
    case MSG.AD_CC:
      return [cc, 0, 127];
    case MSG.SW_NOTE:
    case MSG.SW_NOTE_TOGGLE:
      return [60, 127, 0];
    case MSG.SW_PROGRAM_STEP:
      return [0, 0, 127];
    case MSG.SW_MMC:
      return [127, 2, 0];
    case MSG.SW_PRESET_SELECT:
      return [3, 0, 0];
    case MSG.SW_STEP_SELECT:
      return [0, 1, 0];
    case MSG.AD_PITCH_BEND:
    case MSG.AD_AFTERTOUCH:
      return [0, 0, 127];
    case MSG.AD_NRPN_COARSE:
    case MSG.AD_NRPN_FINE:
      return [127, 0, 0];
    case MSG.OFF:
      return [previous.data[0], previous.data[1], previous.data[2]];
    default:
      return [0, 0, 0];
  }
}

export const useEditor = create<EditorState>()((set, get) => {
  const editSlot = (
    index: number,
    label: string,
    recipe: (draft: Preset, slot: Slot) => void,
    coalesceKey?: string,
  ) => {
    set((state) => {
      const slot = state.slots[index];
      if (!slot) return state;
      const draft = slot.preset ? clonePreset(slot.preset) : createPreset('');
      recipe(draft, slot);
      const slots = state.slots.slice();
      slots[index] = { ...slot, preset: draft, source: slot.source ?? 'new' };
      return { slots, past: pushHistory(state, label, coalesceKey), future: [] };
    });
  };

  const assignSlots = (label: string, assign: (slots: Slot[]) => void) => {
    set((state) => {
      const slots = state.slots.slice();
      assign(slots);
      return { slots, past: pushHistory(state, label), future: [] };
    });
  };

  return {
    slots: initialSlots(),
    globals: EMPTY_GLOBALS,

    setGlobal: (index, obj, elm, value) =>
      set((state) => {
        const working = state.globals.working;
        if (!working) return state;
        const next = setGlobalElement(working, index, obj, elm, value);
        return {
          globals: { ...state.globals, working: next },
          past: pushHistory(state, `Global config ${index}`, `global:${index}:${obj}:${elm}`),
          future: [],
        };
      }),

    loadGlobals: (settings, source) =>
      set((state) => ({
        globals: {
          working: settings,
          base: settings,
          device: source === 'device' ? settings : state.globals.device,
          source,
        },
        past: pushHistory(state, source === 'device' ? 'Read global settings' : 'Import global settings'),
        future: [],
      })),

    setDeviceGlobals: (settings, updateBase) =>
      set((state) => ({
        globals: { ...state.globals, device: settings, base: updateBase && settings ? settings : state.globals.base },
      })),

    selectedSlot: 19,
    selection: { kind: 'control', key: 'SW1' },
    clipboard: null,
    preview: null,
    past: [],
    future: [],

    selectSlot: (index) => {
      if (index >= 0 && index < SLOT_COUNT) set({ selectedSlot: index });
    },
    select: (selection) => set({ selection }),

    edit: (index, label, recipe, coalesceKey) => editSlot(index, label, recipe, coalesceKey),

    setName: (index, name) =>
      editSlot(index, 'Rename preset', (p) => {
        p.name = normalizeName(name);
      }, `name:${index}`),

    setControlMode: (index, key, mode) =>
      editSlot(index, `${CONTROL_BY_KEY[key].label} control mode`, (p) => {
        p.controls[key].mode = clampByte(mode);
      }),

    updateStep: (index, key, step, patch) =>
      editSlot(
        index,
        `${CONTROL_BY_KEY[key].label} step ${step + 1}`,
        (p) => {
          const s = p.controls[key].steps[step];
          if (patch.channel !== undefined) s.channel = clampChannel(patch.channel);
          if (patch.msgType !== undefined) s.msgType = clampByte(patch.msgType);
          if (patch.data !== undefined) s.data = clampData(patch.data);
          if (patch.active !== undefined) s.active = patch.active;
        },
        `step:${index}:${key}:${step}:${Object.keys(patch).join(',')}`,
      ),

    setStepType: (index, key, step, msgType) =>
      editSlot(index, `${CONTROL_BY_KEY[key].label} step ${step + 1} type`, (p) => {
        const s = p.controls[key].steps[step];
        if (s.msgType === msgType) return;
        s.data = defaultDataFor(msgType, s);
        s.msgType = msgType;
        s.active = msgType !== MSG.OFF;
      }),

    updateLed: (index, key, step, patch) =>
      editSlot(
        index,
        `${CONTROL_BY_KEY[key].label} LED ${step + 1}`,
        (p) => {
          const leds = p.controls[key].leds;
          if (!leds) return;
          const l = leds[step];
          if (patch.midiCtrl !== undefined) l.midiCtrl = patch.midiCtrl;
          if (patch.onColor !== undefined) l.onColor = clampByte(patch.onColor);
          if (patch.offColor !== undefined) l.offColor = clampByte(patch.offColor);
          if (patch.num !== undefined) l.num = clampByte(patch.num);
        },
      ),

    updateMidi: (index, setting, patch) =>
      editSlot(
        index,
        `On-load MIDI ${setting + 1}`,
        (p) => {
          const m = p.midi[setting];
          if (patch.channel !== undefined) m.channel = clampChannel(patch.channel);
          if (patch.msgType !== undefined) m.msgType = clampByte(patch.msgType);
          if (patch.data !== undefined) m.data = clampData(patch.data);
        },
        `midi:${index}:${setting}:${Object.keys(patch).join(',')}`,
      ),

    setMidiType: (index, setting, msgType) =>
      editSlot(index, `On-load MIDI ${setting + 1} type`, (p) => {
        const m = p.midi[setting];
        if (m.msgType === msgType) return;
        m.data = defaultDataFor(msgType, m);
        m.msgType = msgType;
      }),

    setLabel: (index, key, label) =>
      set((state) => {
        const slot = state.slots[index];
        if (!slot) return state;
        const labels = { ...slot.labels };
        const text = label.slice(0, 12);
        if (text.trim()) labels[key] = text;
        else delete labels[key];
        const slots = state.slots.slice();
        slots[index] = { ...slot, labels };
        return { slots, past: pushHistory(state, `Label ${CONTROL_BY_KEY[key].label}`, `label:${index}:${key}`), future: [] };
      }),

    loadPresets: (entries, source, label) =>
      assignSlots(label, (slots) => {
        for (const e of entries) {
          const slot = slots[e.index];
          if (!slot) continue;
          slots[e.index] = {
            ...slot,
            preset: e.preset,
            base: source === 'file' ? e.preset : slot.base,
            source,
            labels: e.labels ?? (source === 'file' ? {} : slot.labels),
            complete: e.complete ?? true,
          };
        }
      }),

    loadFromDevice: (entries) =>
      assignSlots(entries.length === 1 ? `Read ${slotLabel(entries[0].index)} from Pacer` : 'Read all from Pacer', (slots) => {
        for (const e of entries) {
          const slot = slots[e.index];
          if (!slot) continue;
          slots[e.index] = {
            ...slot,
            preset: e.preset,
            base: e.preset,
            device: e.complete === false ? null : e.preset,
            source: 'device',
            complete: e.complete ?? true,
          };
        }
      }),

    markWritten: (index, preset) =>
      set((state) => {
        const slot = state.slots[index];
        if (!slot) return state;
        const slots = state.slots.slice();
        slots[index] = { ...slot, device: preset, base: slot.preset === preset ? preset : slot.base };
        // keep history entries consistent: they must not resurrect an outdated device state
        return { slots };
      }),

    forgetDevice: () =>
      set((state) => ({ slots: state.slots.map((s) => (s.device ? { ...s, device: null } : s)) })),

    newPreset: (index) =>
      assignSlots(`New preset in ${slotLabel(index)}`, (slots) => {
        slots[index] = { ...slots[index], preset: createPreset('INIT'), source: 'new', labels: {}, complete: true };
      }),

    copySlot: (index) => {
      const slot = get().slots[index];
      if (!slot?.preset) return;
      set({ clipboard: { preset: slot.preset, labels: slot.labels, from: index } });
    },

    pasteSlot: (index) => {
      const clip = get().clipboard;
      if (!clip) return;
      assignSlots(`Paste into ${slotLabel(index)}`, (slots) => {
        slots[index] = { ...slots[index], preset: clip.preset, labels: clip.labels, source: 'copy', complete: true };
      });
    },

    copySlotTo: (from, to) => {
      const src = get().slots[from];
      if (!src?.preset || from === to) return;
      assignSlots(`Copy ${slotLabel(from)} → ${slotLabel(to)}`, (slots) => {
        slots[to] = { ...slots[to], preset: src.preset, labels: src.labels, source: 'copy', complete: src.complete };
      });
    },

    swapSlots: (a, b) => {
      const state = get();
      if (a === b || !state.slots[a] || !state.slots[b]) return;
      assignSlots(`Swap ${slotLabel(a)} ⇄ ${slotLabel(b)}`, (slots) => {
        const sa = slots[a];
        const sb = slots[b];
        slots[a] = { ...sa, preset: sb.preset, labels: sb.labels, complete: sb.complete, source: sb.preset ? 'copy' : sa.source };
        slots[b] = { ...sb, preset: sa.preset, labels: sa.labels, complete: sa.complete, source: sa.preset ? 'copy' : sb.source };
      });
    },

    duplicateSlot: (index) => {
      const state = get();
      if (!state.slots[index]?.preset) return null;
      // next empty stored slot after this one (skipping D6, which cannot be read back); else the next slot
      let target: number | null = null;
      for (let k = 1; k <= LAST_STORED_PRESET; k++) {
        const candidate = ((index - 1 + k + LAST_STORED_PRESET) % LAST_STORED_PRESET) + 1;
        if (candidate !== index && candidate !== D6_INDEX && !state.slots[candidate].preset) {
          target = candidate;
          break;
        }
      }
      if (target === null) target = index >= LAST_STORED_PRESET ? 1 : index + 1;
      get().copySlotTo(index, target);
      set({ selectedSlot: target });
      return target;
    },

    startPreview: (preview) => set({ preview }),
    updatePreview: (preview) => set({ preview }),
    cancelPreview: () => set({ preview: null }),
    applyPreview: () => {
      const preview = get().preview;
      if (!preview) return;
      set((state) => {
        const slots = state.slots.slice();
        const slot = slots[preview.slot];
        slots[preview.slot] = { ...slot, preset: preview.preset, labels: preview.labels, source: 'template', complete: true };
        return {
          slots,
          past: pushHistory(state, `Apply ${preview.title} to ${slotLabel(preview.slot)}`),
          future: [],
          preview: null,
          selectedSlot: preview.slot,
        };
      });
    },

    undo: () =>
      set((state) => {
        const entry = state.past[state.past.length - 1];
        if (!entry) return state;
        const current: HistoryEntry = {
          label: entry.label,
          slots: state.slots,
          globals: state.globals,
          selectedSlot: state.selectedSlot,
          selection: state.selection,
          time: Date.now(),
        };
        return {
          slots: restoreSlots(entry.slots, state.slots),
          globals: restoreGlobals(entry.globals, state.globals),
          selectedSlot: entry.selectedSlot,
          selection: entry.selection,
          past: state.past.slice(0, -1),
          future: [...state.future, current],
        };
      }),

    redo: () =>
      set((state) => {
        const entry = state.future[state.future.length - 1];
        if (!entry) return state;
        const current: HistoryEntry = {
          label: entry.label,
          slots: state.slots,
          globals: state.globals,
          selectedSlot: state.selectedSlot,
          selection: state.selection,
          time: Date.now(),
        };
        return {
          slots: restoreSlots(entry.slots, state.slots),
          globals: restoreGlobals(entry.globals, state.globals),
          selectedSlot: entry.selectedSlot,
          selection: entry.selection,
          past: [...state.past, current],
          future: state.future.slice(0, -1),
        };
      }),
  };
});

/**
 * Record what is actually on the Pacer (e.g. from a backup or a read-back after writing) without touching
 * working copies. With `updateBase`, the "edited" state is measured against the device content.
 */
export function setDeviceTruth(entries: readonly { index: number; preset: Preset | null }[], updateBase: boolean): void {
  useEditor.setState((state) => {
    const slots = state.slots.slice();
    for (const e of entries) {
      const slot = slots[e.index];
      if (!slot) continue;
      slots[e.index] = { ...slot, device: e.preset, base: updateBase && e.preset ? e.preset : slot.base };
    }
    return { slots };
  });
}

// ---------------------------------------------------------------------------------------------
// Derived data (memoised on immutable objects)
// ---------------------------------------------------------------------------------------------

export function isSlotEdited(slot: Slot): boolean {
  return slot.preset !== null && !presetsEqual(slot.preset, slot.base);
}

export function isSlotInSync(slot: Slot): boolean {
  return slot.preset !== null && slot.device !== null && presetsEqual(slot.preset, slot.device);
}

const pendingCache = new WeakMap<Preset, { device: Preset | null; index: number; parts: EncodedPart[] }>();

/** Messages needed to write this slot's working copy to the Pacer (full preset when the device content is unknown). */
export function slotWriteParts(slot: Slot, index: number): EncodedPart[] {
  if (!slot.preset) return [];
  const cached = pendingCache.get(slot.preset);
  if (cached && cached.device === slot.device && cached.index === index) return cached.parts;
  const parts = diffParts(slot.device, slot.preset, index);
  pendingCache.set(slot.preset, { device: slot.device, index, parts });
  return parts;
}

/** Pending messages for "Send changes": edited slots only. */
export function slotPendingParts(slot: Slot, index: number): EncodedPart[] {
  return isSlotEdited(slot) ? slotWriteParts(slot, index) : [];
}

export function isGlobalsEdited(g: GlobalsSlot): boolean {
  return g.working !== null && !globalsEqual(g.working, g.base);
}

/** Global config messages to write: diff against the device when known, else every config message. */
export function globalWriteList(g: GlobalsSlot): GlobalWritePart[] {
  return g.working ? globalWriteParts(g.device, g.working) : [];
}

export function globalPendingParts(g: GlobalsSlot): GlobalWritePart[] {
  return isGlobalsEdited(g) ? globalWriteList(g) : [];
}

export function pendingSummary(slots: readonly Slot[]): { slots: number[]; messages: number } {
  const indexes: number[] = [];
  let messages = 0;
  slots.forEach((slot, index) => {
    const n = slotPendingParts(slot, index).length;
    if (n > 0) {
      indexes.push(index);
      messages += n;
    }
  });
  return { slots: indexes, messages };
}
