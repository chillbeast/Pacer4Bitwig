import {
  CONTROLS,
  CONTROL_BY_KEY,
  LED_COLOR_DEFAULT,
  MSG,
  PRESET_MIDI_COUNT,
  PRESET_NAME_LENGTH,
  STEP_COUNT,
  type ControlKey,
} from './constants';

/** Three 7-bit data bytes of a step / preset MIDI message. */
export type DataBytes = [number, number, number];

export interface Step {
  /** 0 = global channel, 1..16 = MIDI channel. */
  channel: number;
  msgType: number;
  data: DataBytes;
  active: boolean;
}

export interface Led {
  /** LED on/off colour follows the step's own MIDI message received over USB. */
  midiCtrl: boolean;
  onColor: number;
  offColor: number;
  /** 0 default, 1 bottom, 2 middle, 3 top. */
  num: number;
}

export interface Control {
  mode: number;
  /** Always 6 steps. */
  steps: Step[];
  /** 6 LED configs for stompswitches, null for footswitch jacks and expression pedals. */
  leds: Led[] | null;
}

export interface MidiSetting {
  channel: number;
  msgType: number;
  data: DataBytes;
}

export type Controls = Record<ControlKey, Control>;

export interface Preset {
  /** Always exactly 5 printable ASCII characters (space padded). */
  name: string;
  controls: Controls;
  /** Always 16 "on preset load" messages. */
  midi: MidiSetting[];
}

export function clampByte(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(127, Math.max(0, Math.round(value)));
}

export function clampChannel(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(16, Math.max(0, Math.round(value)));
}

/** Keep printable ASCII only, at most 5 characters, no padding. For text inputs. */
export function sanitizeNameInput(value: string): string {
  let out = '';
  for (const ch of value) {
    if (out.length >= PRESET_NAME_LENGTH) break;
    const code = ch.charCodeAt(0);
    if (ch.length === 1 && code >= 0x20 && code <= 0x7e) out += ch;
  }
  return out;
}

/** Canonical stored form of a preset name: 5 printable ASCII characters, space padded. */
export function normalizeName(value: string): string {
  let out = '';
  for (const ch of value) {
    if (out.length >= PRESET_NAME_LENGTH) break;
    const code = ch.charCodeAt(0);
    out += ch.length === 1 && code >= 0x20 && code <= 0x7e ? ch : ' ';
  }
  return out.padEnd(PRESET_NAME_LENGTH, ' ');
}

export function displayName(name: string): string {
  return name.trimEnd();
}

export function createStep(init: Partial<Step> = {}): Step {
  return {
    channel: init.channel ?? 0,
    msgType: init.msgType ?? MSG.OFF,
    data: init.data ? [init.data[0], init.data[1], init.data[2]] : [0, 0, 0],
    active: init.active ?? false,
  };
}

export function createLed(init: Partial<Led> = {}): Led {
  return {
    midiCtrl: init.midiCtrl ?? false,
    onColor: init.onColor ?? LED_COLOR_DEFAULT,
    offColor: init.offColor ?? LED_COLOR_DEFAULT,
    num: init.num ?? 0,
  };
}

export function createMidiSetting(init: Partial<MidiSetting> = {}): MidiSetting {
  return {
    channel: init.channel ?? 0,
    msgType: init.msgType ?? MSG.OFF,
    data: init.data ? [init.data[0], init.data[1], init.data[2]] : [0, 0, 0],
  };
}

export function createControl(key: ControlKey): Control {
  const def = CONTROL_BY_KEY[key];
  return {
    mode: 0,
    steps: Array.from({ length: STEP_COUNT }, () => createStep()),
    leds: def.hasLeds ? Array.from({ length: STEP_COUNT }, () => createLed()) : null,
  };
}

export function createPreset(name = ''): Preset {
  const controls = {} as Controls;
  for (const c of CONTROLS) controls[c.key] = createControl(c.key);
  return {
    name: normalizeName(name),
    controls,
    midi: Array.from({ length: PRESET_MIDI_COUNT }, () => createMidiSetting()),
  };
}

export function cloneStep(s: Step): Step {
  return { channel: s.channel, msgType: s.msgType, data: [s.data[0], s.data[1], s.data[2]], active: s.active };
}

export function cloneLed(l: Led): Led {
  return { midiCtrl: l.midiCtrl, onColor: l.onColor, offColor: l.offColor, num: l.num };
}

export function cloneControl(c: Control): Control {
  return { mode: c.mode, steps: c.steps.map(cloneStep), leds: c.leds ? c.leds.map(cloneLed) : null };
}

export function clonePreset(p: Preset): Preset {
  const controls = {} as Controls;
  for (const c of CONTROLS) controls[c.key] = cloneControl(p.controls[c.key]);
  return {
    name: p.name,
    controls,
    midi: p.midi.map((m) => ({ channel: m.channel, msgType: m.msgType, data: [m.data[0], m.data[1], m.data[2]] })),
  };
}

const keyCache = new WeakMap<Preset, string>();

/**
 * Canonical fingerprint of a preset. Presets are treated as immutable once created,
 * so the fingerprint is cached per object.
 */
export function presetKey(p: Preset): string {
  const cached = keyCache.get(p);
  if (cached !== undefined) return cached;
  const parts: (string | number)[] = [normalizeName(p.name)];
  for (const c of CONTROLS) {
    const ctl = p.controls[c.key];
    parts.push(ctl.mode);
    for (const s of ctl.steps) parts.push(s.channel, s.msgType, s.data[0], s.data[1], s.data[2], s.active ? 1 : 0);
    if (ctl.leds) for (const l of ctl.leds) parts.push(l.midiCtrl ? 1 : 0, l.onColor, l.offColor, l.num);
  }
  for (const m of p.midi) parts.push(m.channel, m.msgType, m.data[0], m.data[1], m.data[2]);
  const key = parts.join(',');
  keyCache.set(p, key);
  return key;
}

export function presetsEqual(a: Preset | null | undefined, b: Preset | null | undefined): boolean {
  if (a === b) return true;
  if (!a || !b) return false;
  return presetKey(a) === presetKey(b);
}

/** Index of the first active step, or 0 when no step is active. */
export function primaryStepIndex(control: Control): number {
  const i = control.steps.findIndex((s) => s.active && s.msgType !== MSG.OFF);
  return i < 0 ? 0 : i;
}

export function activeStepCount(control: Control): number {
  return control.steps.filter((s) => s.active && s.msgType !== MSG.OFF).length;
}
