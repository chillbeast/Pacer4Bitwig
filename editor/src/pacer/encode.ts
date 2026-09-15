import {
  CMD_SET,
  CONTROLS,
  CONTROL_BY_KEY,
  ELM_CONTROL_MODE,
  ELM_LED_FIRST,
  OBJ_NAME,
  OBJ_PRESET_MIDI,
  PRESET_NAME_LENGTH,
  TARGET_PRESET,
  type ControlKey,
} from './constants';
import { clampByte, clampChannel, normalizeName, type Led, type MidiSetting, type Preset, type Step } from './model';
import { buildPacerMessage, concatMessages } from './sysex';

export type PartRef =
  | { kind: 'name' }
  | { kind: 'mode'; control: ControlKey }
  | { kind: 'step'; control: ControlKey; step: number }
  | { kind: 'led'; control: ControlKey; step: number }
  | { kind: 'midi'; setting: number };

export interface EncodedPart {
  part: PartRef;
  bytes: Uint8Array;
}

function assertIndex(index: number): void {
  if (!Number.isInteger(index) || index < 0 || index > 24) {
    throw new RangeError(`Invalid preset index ${index}`);
  }
}

/** `elm, 1, value, 0x00` — the last element of a message has no trailing 0x00. */
function pushElement(body: number[], elm: number, value: number, last = false): void {
  body.push(elm, 0x01, value);
  if (!last) body.push(0x00);
}

export function encodeName(name: string, index: number): Uint8Array {
  assertIndex(index);
  const text = normalizeName(name);
  const body = [CMD_SET, TARGET_PRESET, index, OBJ_NAME, 0x01, PRESET_NAME_LENGTH];
  for (let i = 0; i < PRESET_NAME_LENGTH; i++) body.push(text.charCodeAt(i));
  return buildPacerMessage(body);
}

export function encodeControlMode(control: ControlKey, mode: number, index: number): Uint8Array {
  assertIndex(index);
  const obj = CONTROL_BY_KEY[control].obj;
  return buildPacerMessage([CMD_SET, TARGET_PRESET, index, obj, ELM_CONTROL_MODE, 0x01, clampByte(mode)]);
}

/** @param stepIndex 0-based step index (0..5) */
export function encodeStep(control: ControlKey, stepIndex: number, step: Step, index: number): Uint8Array {
  assertIndex(index);
  const obj = CONTROL_BY_KEY[control].obj;
  const base = stepIndex * 6;
  const body = [CMD_SET, TARGET_PRESET, index, obj];
  pushElement(body, base + 1, clampChannel(step.channel));
  pushElement(body, base + 2, clampByte(step.msgType));
  pushElement(body, base + 3, clampByte(step.data[0]));
  pushElement(body, base + 4, clampByte(step.data[1]));
  pushElement(body, base + 5, clampByte(step.data[2]));
  pushElement(body, base + 6, step.active ? 1 : 0, true);
  return buildPacerMessage(body);
}

/** @param stepIndex 0-based step index (0..5) */
export function encodeLed(control: ControlKey, stepIndex: number, led: Led, index: number): Uint8Array {
  assertIndex(index);
  const def = CONTROL_BY_KEY[control];
  if (!def.hasLeds) throw new Error(`${def.label} has no LEDs`);
  const base = ELM_LED_FIRST + stepIndex * 4;
  const body = [CMD_SET, TARGET_PRESET, index, def.obj];
  pushElement(body, base, led.midiCtrl ? 1 : 0);
  pushElement(body, base + 1, clampByte(led.onColor));
  pushElement(body, base + 2, clampByte(led.offColor));
  pushElement(body, base + 3, clampByte(led.num), true);
  return buildPacerMessage(body);
}

/**
 * Preset "on load" MIDI message. Mirrors the device dump format: channel, type, data 1..3
 * (the original editor also sent an "active" element 6, which the Pacer never dumps).
 * @param settingIndex 0-based (0..15)
 */
export function encodeMidiSetting(settingIndex: number, setting: MidiSetting, index: number): Uint8Array {
  assertIndex(index);
  const base = settingIndex * 6;
  const body = [CMD_SET, TARGET_PRESET, index, OBJ_PRESET_MIDI];
  pushElement(body, base + 1, clampChannel(setting.channel));
  pushElement(body, base + 2, clampByte(setting.msgType));
  pushElement(body, base + 3, clampByte(setting.data[0]));
  pushElement(body, base + 4, clampByte(setting.data[1]));
  pushElement(body, base + 5, clampByte(setting.data[2]), true);
  return buildPacerMessage(body);
}

/** Every message of a full preset, in the same order the Pacer dumps it (189 messages). */
export function encodePresetParts(preset: Preset, index: number): EncodedPart[] {
  const parts: EncodedPart[] = [{ part: { kind: 'name' }, bytes: encodeName(preset.name, index) }];
  for (const def of CONTROLS) {
    const control = preset.controls[def.key];
    parts.push({ part: { kind: 'mode', control: def.key }, bytes: encodeControlMode(def.key, control.mode, index) });
    control.steps.forEach((step, s) => {
      parts.push({ part: { kind: 'step', control: def.key, step: s }, bytes: encodeStep(def.key, s, step, index) });
    });
    if (def.hasLeds && control.leds) {
      control.leds.forEach((led, s) => {
        parts.push({ part: { kind: 'led', control: def.key, step: s }, bytes: encodeLed(def.key, s, led, index) });
      });
    }
  }
  preset.midi.forEach((setting, s) => {
    parts.push({ part: { kind: 'midi', setting: s }, bytes: encodeMidiSetting(s, setting, index) });
  });
  return parts;
}

export function encodePreset(preset: Preset, index: number): Uint8Array[] {
  return encodePresetParts(preset, index).map((p) => p.bytes);
}

function sameBytes(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

/**
 * Messages needed to turn `original` into `edited` on the device. With no original
 * (unknown device content), every message of the edited preset is returned.
 */
export function diffParts(original: Preset | null, edited: Preset, index: number): EncodedPart[] {
  const next = encodePresetParts(edited, index);
  if (!original) return next;
  const prev = encodePresetParts(original, index);
  return next.filter((part, i) => !sameBytes(part.bytes, prev[i].bytes));
}

export function diffMessages(original: Preset | null, edited: Preset, index: number): Uint8Array[] {
  return diffParts(original, edited, index).map((p) => p.bytes);
}

export function describePart(part: PartRef): string {
  switch (part.kind) {
    case 'name':
      return 'Preset name';
    case 'mode':
      return `${CONTROL_BY_KEY[part.control].label} · control mode`;
    case 'step':
      return `${CONTROL_BY_KEY[part.control].label} · step ${part.step + 1}`;
    case 'led':
      return `${CONTROL_BY_KEY[part.control].label} · LED ${part.step + 1}`;
    case 'midi':
      return `On-load MIDI ${part.setting + 1}`;
  }
}

export interface ControlDiff {
  /** Control key, or "name" / "midi" for preset-level parts. */
  group: ControlKey | 'name' | 'midi';
  label: string;
  parts: PartRef[];
}

/** Differences between two presets grouped per control (for side-by-side diff views). */
export function diffByControl(original: Preset | null, edited: Preset): ControlDiff[] {
  const groups = new Map<string, ControlDiff>();
  for (const { part } of diffParts(original, edited, 1)) {
    const group: ControlDiff['group'] = part.kind === 'name' ? 'name' : part.kind === 'midi' ? 'midi' : part.control;
    let entry = groups.get(group);
    if (!entry) {
      const label = group === 'name' ? 'Preset name' : group === 'midi' ? 'On-load MIDI' : CONTROL_BY_KEY[group].label;
      entry = { group, label, parts: [] };
      groups.set(group, entry);
    }
    entry.parts.push(part);
  }
  return [...groups.values()];
}

/** A .syx file with every given preset (ascending index) followed by raw global messages. */
export function encodeDump(presets: Iterable<readonly [number, Preset]>, globals: readonly Uint8Array[] = []): Uint8Array {
  const sorted = [...presets].sort((a, b) => a[0] - b[0]);
  const messages: Uint8Array[] = [];
  for (const [index, preset] of sorted) messages.push(...encodePreset(preset, index));
  messages.push(...globals);
  return concatMessages(messages);
}
