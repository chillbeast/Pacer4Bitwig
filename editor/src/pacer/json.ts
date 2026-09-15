import { CONTROLS, CONTROL_KEYS, PRESET_MIDI_COUNT, STEP_COUNT, type ControlKey } from './constants';
import {
  clampByte,
  clampChannel,
  createPreset,
  normalizeName,
  type DataBytes,
  type Led,
  type MidiSetting,
  type Preset,
  type Step,
} from './model';
import { isValidSlot, slotIndexFromLabel, slotLabel } from './slots';

export const JSON_FORMAT = 'pacer-studio';
export const JSON_VERSION = 1;

export type ControlLabels = Partial<Record<ControlKey, string>>;

export interface JsonPresetEntry {
  index: number;
  preset: Preset;
  labels?: ControlLabels;
}

export interface JsonDocument {
  format: typeof JSON_FORMAT;
  version: number;
  exportedAt: string;
  presets: { slot: string; index: number; preset: Preset; labels?: ControlLabels }[];
}

export class JsonImportError extends Error {
  override name = 'JsonImportError';
}

export function exportJson(entries: readonly JsonPresetEntry[], now = new Date()): string {
  const doc: JsonDocument = {
    format: JSON_FORMAT,
    version: JSON_VERSION,
    exportedAt: now.toISOString(),
    presets: entries.map((e) => ({
      slot: slotLabel(e.index),
      index: e.index,
      preset: e.preset,
      ...(e.labels && Object.keys(e.labels).length > 0 ? { labels: e.labels } : {}),
    })),
  };
  return JSON.stringify(doc, null, 2);
}

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function num(v: unknown, path: string, clamp: (n: number) => number = clampByte): number {
  if (typeof v !== 'number' || !Number.isFinite(v)) throw new JsonImportError(`${path}: expected a number`);
  return clamp(v);
}

function bool(v: unknown, path: string): boolean {
  if (typeof v === 'boolean') return v;
  if (v === 0 || v === 1) return v === 1;
  throw new JsonImportError(`${path}: expected a boolean`);
}

function data(v: unknown, path: string): DataBytes {
  if (!Array.isArray(v) || v.length !== 3) throw new JsonImportError(`${path}: expected 3 data bytes`);
  return [num(v[0], `${path}[0]`), num(v[1], `${path}[1]`), num(v[2], `${path}[2]`)];
}

function step(v: unknown, path: string): Step {
  if (!isRecord(v)) throw new JsonImportError(`${path}: expected an object`);
  return {
    channel: num(v.channel, `${path}.channel`, clampChannel),
    msgType: num(v.msgType, `${path}.msgType`),
    data: data(v.data, `${path}.data`),
    active: bool(v.active, `${path}.active`),
  };
}

function led(v: unknown, path: string): Led {
  if (!isRecord(v)) throw new JsonImportError(`${path}: expected an object`);
  return {
    midiCtrl: bool(v.midiCtrl, `${path}.midiCtrl`),
    onColor: num(v.onColor, `${path}.onColor`),
    offColor: num(v.offColor, `${path}.offColor`),
    num: num(v.num, `${path}.num`),
  };
}

function midiSetting(v: unknown, path: string): MidiSetting {
  if (!isRecord(v)) throw new JsonImportError(`${path}: expected an object`);
  return {
    channel: num(v.channel, `${path}.channel`, clampChannel),
    msgType: num(v.msgType, `${path}.msgType`),
    data: data(v.data, `${path}.data`),
  };
}

/** Validate an untrusted value as a Preset. Throws JsonImportError with a path on failure. */
export function validatePreset(value: unknown, path = 'preset'): Preset {
  if (!isRecord(value)) throw new JsonImportError(`${path}: expected an object`);
  if (typeof value.name !== 'string') throw new JsonImportError(`${path}.name: expected a string`);
  const preset = createPreset(normalizeName(value.name));
  if (!isRecord(value.controls)) throw new JsonImportError(`${path}.controls: expected an object`);
  for (const def of CONTROLS) {
    const raw = value.controls[def.key];
    const cpath = `${path}.controls.${def.key}`;
    if (!isRecord(raw)) throw new JsonImportError(`${cpath}: missing`);
    const control = preset.controls[def.key];
    control.mode = num(raw.mode, `${cpath}.mode`);
    if (!Array.isArray(raw.steps) || raw.steps.length !== STEP_COUNT) {
      throw new JsonImportError(`${cpath}.steps: expected ${STEP_COUNT} steps`);
    }
    control.steps = raw.steps.map((s, i) => step(s, `${cpath}.steps[${i}]`));
    if (def.hasLeds) {
      if (!Array.isArray(raw.leds) || raw.leds.length !== STEP_COUNT) {
        throw new JsonImportError(`${cpath}.leds: expected ${STEP_COUNT} LED configs`);
      }
      control.leds = raw.leds.map((l, i) => led(l, `${cpath}.leds[${i}]`));
    }
  }
  if (!Array.isArray(value.midi) || value.midi.length !== PRESET_MIDI_COUNT) {
    throw new JsonImportError(`${path}.midi: expected ${PRESET_MIDI_COUNT} settings`);
  }
  preset.midi = value.midi.map((m, i) => midiSetting(m, `${path}.midi[${i}]`));
  return preset;
}

function labels(value: unknown): ControlLabels | undefined {
  if (!isRecord(value)) return undefined;
  const out: ControlLabels = {};
  for (const key of CONTROL_KEYS) {
    const v = value[key];
    if (typeof v === 'string' && v.length > 0) out[key] = v.slice(0, 24);
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

export function importJson(text: string): JsonPresetEntry[] {
  let doc: unknown;
  try {
    doc = JSON.parse(text);
  } catch {
    throw new JsonImportError('Not a valid JSON file');
  }
  if (!isRecord(doc) || doc.format !== JSON_FORMAT) {
    throw new JsonImportError('Not a Pacer Studio JSON file');
  }
  if (typeof doc.version !== 'number' || doc.version > JSON_VERSION) {
    throw new JsonImportError(`Unsupported file version ${String(doc.version)}`);
  }
  if (!Array.isArray(doc.presets)) throw new JsonImportError('presets: expected an array');
  return doc.presets.map((entry, i) => {
    const path = `presets[${i}]`;
    if (!isRecord(entry)) throw new JsonImportError(`${path}: expected an object`);
    let index: number | null = typeof entry.index === 'number' ? entry.index : null;
    if (index === null && typeof entry.slot === 'string') index = slotIndexFromLabel(entry.slot);
    if (index === null || !isValidSlot(index)) throw new JsonImportError(`${path}: invalid slot`);
    const result: JsonPresetEntry = { index, preset: validatePreset(entry.preset, `${path}.preset`) };
    const l = labels(entry.labels);
    if (l) result.labels = l;
    return result;
  });
}
