/**
 * Global settings (SysEx target 0x05).
 *
 * The Pacer sends 37 global messages in a full backup:
 *   idx 1..4 (global configs) × obj 0x01 settings, 0x23 footswitch mode, 0x25 relay mode, 0x26 encoder, 0x27 expression
 *   idx 0 (current state)     × 17 messages of obj 0x01 (active preset, current preset/config, program/bank per channel)
 *
 * Messages are kept generically as element lists so unknown elements (e.g. 0x63) survive a round trip byte for byte.
 * Element names come from Nektar's SysEx document (reference/pacer-editor/sysex.md); most value meanings are unverified.
 */
import { messageChecksumValid } from './checksum';
import { CMD_GET, CMD_SET, TARGET_GLOBAL, type EnumOption } from './constants';
import { buildPacerMessage, isPacerMessage, readElements, splitSysex } from './sysex';

export const GLOBAL_CURRENT_INDEX = 0;
export const GLOBAL_CONFIG_INDEXES: readonly number[] = [1, 2, 3, 4];
export const GLOBAL_MESSAGE_COUNT = 37;

export const GLOBAL_OBJ = {
  SETTINGS: 0x01,
  FOOTSWITCH_MODE: 0x23,
  RELAY_MODE: 0x25,
  ENCODER: 0x26,
  EXPRESSION: 0x27,
} as const;

export const GLOBAL_ELM = {
  CHANNEL: 0x01,
  MIDI_SOURCE: 0x09,
  PATCH_UP_DOWN: 0x30,
  DIM_LEVEL: 0x62,
  ACTIVE_PRESET: 0x1a,
  CURRENT_USER_PRESET: 0x1e,
  CURRENT_GLOBAL_CONFIG: 0x31,
  PROGRAM_FIRST: 0x32,
  BANK_A_FIRST: 0x42,
  BANK_B_FIRST: 0x52,
} as const;

export interface GlobalElement {
  elm: number;
  value: number;
}

export interface GlobalMessage {
  index: number;
  obj: number;
  elements: GlobalElement[];
}

/** All known global messages in device order. Treat as immutable. */
export interface GlobalSettings {
  messages: GlobalMessage[];
}

export function emptyGlobals(): GlobalSettings {
  return { messages: [] };
}

/**
 * A global message is identified by index, object and its first element: the current state (idx 0) sends 17
 * messages that all use obj 0x01 but start at different elements (0x1E, 0x32, 0x33, …).
 */
const sameSlot = (a: GlobalMessage, b: GlobalMessage) =>
  a.index === b.index && a.obj === b.obj && a.elements[0]?.elm === b.elements[0]?.elm;

/** Parse the global messages (target 0x05, SET) out of any list of messages; other messages are ignored. */
export function parseGlobalMessages(messages: readonly Uint8Array[]): GlobalSettings {
  const out: GlobalMessage[] = [];
  for (const msg of messages) {
    if (!isPacerMessage(msg) || msg[5] !== CMD_SET || msg[6] !== TARGET_GLOBAL || msg.length < 12) continue;
    if (!messageChecksumValid(msg)) continue;
    const parsed: GlobalMessage = {
      index: msg[7],
      obj: msg[8],
      elements: readElements(msg, 9, msg.length - 2).map(({ elm, value }) => ({ elm, value })),
    };
    const existing = out.findIndex((m) => sameSlot(m, parsed));
    if (existing >= 0) out[existing] = parsed;
    else out.push(parsed);
  }
  return { messages: out };
}

export function parseGlobalDump(bytes: ArrayLike<number>): GlobalSettings {
  return parseGlobalMessages(splitSysex(bytes));
}

/** Replace/add messages of `incoming` into `base` (by index + object). */
export function mergeGlobals(base: GlobalSettings | null, incoming: GlobalSettings): GlobalSettings {
  if (!base) return incoming;
  const messages = base.messages.slice();
  for (const m of incoming.messages) {
    const i = messages.findIndex((x) => sameSlot(x, m));
    if (i >= 0) messages[i] = m;
    else messages.push(m);
  }
  return { messages };
}

export function findGlobalMessage(g: GlobalSettings | null, index: number, obj: number): GlobalMessage | undefined {
  return g?.messages.find((m) => m.index === index && m.obj === obj);
}

export function getGlobalValue(g: GlobalSettings | null, index: number, obj: number, elm: number): number | undefined {
  for (const m of g?.messages ?? []) {
    if (m.index !== index || m.obj !== obj) continue;
    const e = m.elements.find((x) => x.elm === elm);
    if (e) return e.value;
  }
  return undefined;
}

/** Returns a new GlobalSettings with one element changed. Only existing elements can be changed. */
export function setGlobalValue(g: GlobalSettings, index: number, obj: number, elm: number, value: number): GlobalSettings {
  if (!Number.isInteger(value) || value < 0 || value > 127) throw new RangeError(`Invalid value ${value}`);
  if (index === GLOBAL_CURRENT_INDEX) throw new Error('The current global state (idx 0) is read-only');
  let found = false;
  const messages = g.messages.map((m) => {
    if (m.index !== index || m.obj !== obj) return m;
    return {
      ...m,
      elements: m.elements.map((e) => {
        if (e.elm !== elm) return e;
        found = true;
        return { elm, value };
      }),
    };
  });
  if (!found) throw new Error(`No global element idx ${index} obj 0x${obj.toString(16)} elm 0x${elm.toString(16)}`);
  return { messages };
}

/** `elm, 1, value, 0x00` per element, the last one without the trailing 0x00 — same shape as the device dump. */
export function encodeGlobalMessage(m: GlobalMessage): Uint8Array {
  const body = [CMD_SET, TARGET_GLOBAL, m.index, m.obj];
  m.elements.forEach((e, i) => {
    body.push(e.elm, 0x01, e.value);
    if (i < m.elements.length - 1) body.push(0x00);
  });
  return buildPacerMessage(body);
}

export function encodeGlobals(g: GlobalSettings): Uint8Array[] {
  return g.messages.map(encodeGlobalMessage);
}

function sameBytes(a: Uint8Array, b: Uint8Array): boolean {
  return a.length === b.length && a.every((v, i) => v === b[i]);
}

export interface GlobalWritePart {
  message: GlobalMessage;
  bytes: Uint8Array;
}

/**
 * Messages to write for the global configs 1..4. The current state (idx 0) is never written: writing it is known to
 * misbehave (the original editor's notes: "DOES NOT WORK. DO NOT SEND. NEED RESET AFTER.").
 * With no base (device content unknown) every config message is returned.
 */
export function globalWriteParts(base: GlobalSettings | null, edited: GlobalSettings): GlobalWritePart[] {
  const out: GlobalWritePart[] = [];
  for (const m of edited.messages) {
    if (!GLOBAL_CONFIG_INDEXES.includes(m.index)) continue;
    const bytes = encodeGlobalMessage(m);
    const previous = base?.messages.find((x) => sameSlot(x, m));
    if (!previous || !sameBytes(encodeGlobalMessage(previous), bytes)) out.push({ message: m, bytes });
  }
  return out;
}

const keyCache = new WeakMap<GlobalSettings, string>();

export function globalsKey(g: GlobalSettings): string {
  let key = keyCache.get(g);
  if (key === undefined) {
    key = g.messages.map((m) => `${m.index}/${m.obj}:${m.elements.map((e) => `${e.elm}=${e.value}`).join(',')}`).join('|');
    keyCache.set(g, key);
  }
  return key;
}

export function globalsEqual(a: GlobalSettings | null | undefined, b: GlobalSettings | null | undefined): boolean {
  if (a === b) return true;
  if (!a || !b) return false;
  return globalsKey(a) === globalsKey(b);
}

/** GET the global settings: `F0 00 01 77 7F 02 05 00 79 F7`. */
export function requestGlobals(): Uint8Array {
  return buildPacerMessage([CMD_GET, TARGET_GLOBAL, GLOBAL_CURRENT_INDEX]);
}

// ---------------------------------------------------------------------------------------------
// Field descriptions for the UI
// ---------------------------------------------------------------------------------------------

export type GlobalFieldKind = 'number' | 'enum' | 'msgType' | 'channel';

export interface GlobalFieldSpec {
  obj: number;
  elm: number;
  label: string;
  kind: GlobalFieldKind;
  options?: readonly EnumOption[];
  /** True when the value meaning is confirmed by data (not just the element name). */
  verified: boolean;
  note?: string;
}

export const RELAY_GLOBAL_MODE_OPTIONS: readonly EnumOption[] = [
  { value: 0, label: 'Auto detect' },
  { value: 1, label: 'Normally open' },
  { value: 2, label: 'Normally closed' },
  { value: 3, label: 'Latching' },
];

export const ACTIVE_PRESET_OPTIONS: readonly EnumOption[] = [
  { value: 0, label: 'User' },
  { value: 1, label: 'Track' },
  { value: 2, label: 'Transport' },
  { value: 3, label: 'Track long press' },
  { value: 4, label: 'Transport long press' },
];

export const GLOBAL_CONFIG_SECTIONS: readonly { title: string; fields: readonly GlobalFieldSpec[] }[] = [
  {
    title: 'MIDI',
    fields: [
      { obj: 0x01, elm: 0x01, label: 'Global MIDI channel', kind: 'number', verified: false, note: 'Used by steps set to "Global". 1 in factory data — probably 1-based.' },
      { obj: 0x01, elm: 0x09, label: 'MIDI jack source', kind: 'number', verified: false, note: 'Value meaning not documented.' },
      { obj: 0x01, elm: 0x30, label: 'Patch up/down function', kind: 'number', verified: false, note: 'Value meaning not documented.' },
    ],
  },
  {
    title: 'LEDs',
    fields: [
      { obj: 0x01, elm: 0x62, label: 'Dim LED brightness', kind: 'number', verified: false, note: 'Brightness level of dim LEDs; 4 in factory data, range unknown.' },
    ],
  },
  {
    title: 'Footswitch jacks',
    fields: [1, 2, 3, 4].map((n) => ({
      obj: 0x23,
      elm: n,
      label: `Footswitch mode ${n}`,
      kind: 'number' as const,
      verified: false,
      note: 'Element per jack input assumed; value meaning not documented.',
    })),
  },
  {
    title: 'Relays',
    fields: [1, 2, 3, 4].map((n) => ({
      obj: 0x25,
      elm: n,
      label: `Relay R${n} mode`,
      kind: 'enum' as const,
      options: RELAY_GLOBAL_MODE_OPTIONS,
      verified: false,
      note: 'Labels from the original editor; not verified.',
    })),
  },
  {
    title: 'Encoder',
    fields: [
      { obj: 0x26, elm: 0x01, label: 'Channel', kind: 'channel', verified: false },
      { obj: 0x26, elm: 0x02, label: 'Message type', kind: 'msgType', verified: false, note: '0x17 (Preset Select) in factory data.' },
      { obj: 0x26, elm: 0x03, label: 'Data 1', kind: 'number', verified: false },
      { obj: 0x26, elm: 0x04, label: 'Data 2', kind: 'number', verified: false, note: '3 (= A1) in factory data.' },
      { obj: 0x26, elm: 0x05, label: 'Data 3', kind: 'number', verified: false, note: '26 (= D6) in factory data.' },
    ],
  },
  {
    title: 'Expression pedals',
    fields: [1, 2].map((n) => ({
      obj: 0x27,
      elm: n,
      label: `Expression pedal ${n}`,
      kind: 'number' as const,
      verified: false,
      note: 'Undocumented value (1 in factory data).',
    })),
  },
];

/** Elements of a config message that no field describes (shown raw). */
export function unknownElements(g: GlobalSettings | null, index: number): { obj: number; elm: number; value: number }[] {
  const known = new Set(GLOBAL_CONFIG_SECTIONS.flatMap((s) => s.fields.map((f) => `${f.obj}/${f.elm}`)));
  const out: { obj: number; elm: number; value: number }[] = [];
  for (const m of g?.messages ?? []) {
    if (m.index !== index) continue;
    for (const e of m.elements) if (!known.has(`${m.obj}/${e.elm}`)) out.push({ obj: m.obj, elm: e.elm, value: e.value });
  }
  return out;
}

export interface GlobalCurrentState {
  activePreset: number | undefined;
  /** 0 = A1 … 23 = D6 */
  currentUserPreset: number | undefined;
  currentGlobalConfig: number | undefined;
  channels: { channel: number; program: number | undefined; bankA: number | undefined; bankB: number | undefined }[];
}

export function readCurrentState(g: GlobalSettings | null): GlobalCurrentState | null {
  const state = g?.messages.filter((m) => m.index === GLOBAL_CURRENT_INDEX) ?? [];
  if (state.length === 0) return null;
  const value = (elm: number) => {
    for (const m of state) {
      const e = m.elements.find((x) => x.elm === elm);
      if (e) return e.value;
    }
    return undefined;
  };
  return {
    activePreset: value(GLOBAL_ELM.ACTIVE_PRESET),
    currentUserPreset: value(GLOBAL_ELM.CURRENT_USER_PRESET),
    currentGlobalConfig: value(GLOBAL_ELM.CURRENT_GLOBAL_CONFIG),
    channels: Array.from({ length: 16 }, (_, i) => ({
      channel: i + 1,
      program: value(GLOBAL_ELM.PROGRAM_FIRST + i),
      bankA: value(GLOBAL_ELM.BANK_A_FIRST + i),
      bankB: value(GLOBAL_ELM.BANK_B_FIRST + i),
    })),
  };
}

/** Global MIDI channel of the active config (1..16), or null when unknown. Unverified numbering. */
export function activeGlobalChannel(g: GlobalSettings | null): number | null {
  const config = readCurrentState(g)?.currentGlobalConfig;
  if (config === undefined || config < 1 || config > 4) return null;
  const channel = getGlobalValue(g, config, GLOBAL_OBJ.SETTINGS, GLOBAL_ELM.CHANNEL);
  return channel !== undefined && channel >= 1 && channel <= 16 ? channel : null;
}
