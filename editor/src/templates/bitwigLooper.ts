/**
 * "Bitwig Looper" template — implements docs/PACER-MAP.md (the shared contract with the Bitwig extension).
 */
import {
  CONTROL_MODE_ALL,
  LED_COLOR_OFF,
  MSG,
  type ControlKey,
} from '../pacer/constants';
import type { ControlLabels } from '../pacer/json';
import { createLed, createMidiSetting, createPreset, createStep, type Preset, type Step } from '../pacer/model';

export type LooperLedMode = 'two-colour' | 'multi-colour';
/** The Pacer presets of the PACER Looper extension: they share this layout and differ in the preset-loaded value. */
export type BitwigPresetKind = 'looper' | 'fx';

export const LOOPER_CHANNEL = 16;
/** D1 */
export const LOOPER_DEFAULT_SLOT = 0x13;
export const LOOPER_PRESET_NAME = 'LOOPS';

/** Switch order used by the colour-slot CC formula (switchIndex 0..9). */
export const LOOPER_SWITCHES: readonly ControlKey[] = [
  'SW1', 'SW2', 'SW3', 'SW4', 'SW5', 'SW6', 'SWA', 'SWB', 'SWC', 'SWD',
];
export const LOOPER_FOOTSWITCHES: readonly ControlKey[] = ['FS1', 'FS2', 'FS3', 'FS4'];
export const LOOPER_EXPRESSION: readonly ControlKey[] = ['EXP1', 'EXP2'];

export const LOOPER_SWITCH_CC_BASE = 102;
export const LOOPER_FOOTSWITCH_CC_BASE = 112;
export const LOOPER_EXPRESSION_CC_BASE = 116;
export const LOOPER_PRESET_LOADED_CC = 119;
export const LOOPER_COLOUR_SLOT_CC_BASE = 20;
/**
 * Value of the preset-loaded CC: tells the extension which preset and LED variant is loaded. `kind × 16 + variant`
 * (variant 1 two-colour, 2 multi-colour); 127 is the legacy looper two-colour value.
 */
export const BITWIG_PRESET_LOADED_VALUE: Readonly<Record<BitwigPresetKind, Readonly<Record<LooperLedMode, number>>>> = {
  looper: { 'two-colour': 127, 'multi-colour': 2 },
  fx: { 'two-colour': 17, 'multi-colour': 18 },
};
export const LOOPER_PRESET_LOADED_VALUE = BITWIG_PRESET_LOADED_VALUE.looper;
/** SW1..SW4 are loop tracks. */
export const LOOPER_LOOP_SWITCH_COUNT = 4;

export const LOOPER_COLOURS = {
  off: 0x00,
  red: 0x03,
  amber: 0x07,
  green: 0x0d,
  blue: 0x11,
  purple: 0x15,
  white: 0x17,
} as const;

/** Multi-colour mode: on colour of steps 1..6 (colour slots). */
export const LOOPER_SLOT_COLOURS: readonly number[] = [
  LOOPER_COLOURS.white,
  LOOPER_COLOURS.red,
  LOOPER_COLOURS.green,
  LOOPER_COLOURS.amber,
  LOOPER_COLOURS.blue,
  LOOPER_COLOURS.purple,
];
export const LOOPER_SLOT_NAMES: readonly string[] = ['white', 'red', 'green', 'amber', 'blue', 'purple'];

/** Action CC (step 1) of every control. */
export const LOOPER_ACTION_CC: Readonly<Record<ControlKey, number>> = {
  SW1: 102, SW2: 103, SW3: 104, SW4: 105, SW5: 106, SW6: 107,
  SWA: 108, SWB: 109, SWC: 110, SWD: 111,
  FS1: 112, FS2: 113, FS3: 114, FS4: 115,
  EXP1: 116, EXP2: 117,
};

/** Colour-slot CC: `20 + switchIndex * 5 + (step - 2)`, step 2..6. */
export function colourSlotCc(switchIndex: number, step: number): number {
  if (switchIndex < 0 || switchIndex > 9 || step < 2 || step > 6) {
    throw new RangeError(`No colour slot for switch ${switchIndex} step ${step}`);
  }
  return LOOPER_COLOUR_SLOT_CC_BASE + switchIndex * 5 + (step - 2);
}

export interface TapHold {
  tap: string;
  hold?: string;
}

/** PACER Looper default actions (docs/LOOPER.md section 5). Editor-side only, never sent to the Pacer. */
export const LOOPER_TAP_HOLD: Readonly<Record<ControlKey, TapHold>> = {
  SW1: { tap: 'Loop 1', hold: 'Delete loop' },
  SW2: { tap: 'Loop 2', hold: 'Delete loop' },
  SW3: { tap: 'Loop 3', hold: 'Delete loop' },
  SW4: { tap: 'Loop 4', hold: 'Delete loop' },
  SW5: { tap: 'Undo', hold: 'Redo' },
  SW6: { tap: 'Play/stop all', hold: 'Clear row' },
  SWA: { tap: 'Row −', hold: 'Tracks ←' },
  SWB: { tap: 'Row +', hold: 'Tracks →' },
  SWC: { tap: 'Overdub', hold: 'Metronome' },
  SWD: { tap: 'Tap tempo', hold: 'Play/stop' },
  FS1: { tap: 'One-button looper', hold: 'Clear last' },
  FS2: { tap: 'Play/stop all', hold: 'Clear row' },
  FS3: { tap: '–' },
  FS4: { tap: '–' },
  EXP1: { tap: 'Selected track volume' },
  EXP2: { tap: 'Master volume' },
};

/** One-line role per control, e.g. "Undo · hold: Redo". */
export const LOOPER_ROLES: Readonly<Record<ControlKey, string>> = Object.fromEntries(
  Object.entries(LOOPER_TAP_HOLD).map(([key, r]) => [key, r.hold ? `${r.tap} · hold: ${r.hold}` : r.tap]),
) as Record<ControlKey, string>;

/**
 * True when every control's step 1 carries the looper action CC on channel 16 (the layout of both template variants),
 * regardless of colours or labels.
 */
export function isLooperLayout(preset: Preset | null): boolean {
  return looperChannelOf(preset) !== null;
}

/**
 * The looper MIDI channel of a preset laid out like the template (every control's step 1 carries its action CC on
 * one shared channel), or null when the preset is not a looper layout.
 */
export function looperChannelOf(preset: Preset | null): number | null {
  if (!preset) return null;
  let channel: number | null = null;
  for (const key of Object.keys(LOOPER_ACTION_CC) as ControlKey[]) {
    const step = preset.controls[key].steps[0];
    const type = key.startsWith('EXP') ? MSG.AD_CC : MSG.SW_CC_TRIGGER;
    if (!step.active || step.msgType !== type || step.data[0] !== LOOPER_ACTION_CC[key]) return null;
    if (step.channel < 1 || step.channel > 16) return null;
    if (channel === null) channel = step.channel;
    else if (step.channel !== channel) return null;
  }
  return channel;
}

export interface PresetAnnouncement {
  kind: BitwigPresetKind;
  mode: LooperLedMode;
}

/**
 * Preset and LED variant a preset-loaded CC value announces (docs/PACER-MAP.md): 127 = looper two-colour, otherwise
 * `kind × 16 + variant`. Unknown kinds count as the looper, like in the extension; null for an unknown variant.
 */
export function decodePresetLoadedValue(value: number): PresetAnnouncement | null {
  if (value === LOOPER_PRESET_LOADED_VALUE['two-colour']) return { kind: 'looper', mode: 'two-colour' };
  const variant = value % 16;
  if (variant !== 1 && variant !== 2) return null;
  return { kind: value >> 4 === 1 ? 'fx' : 'looper', mode: variant === 2 ? 'multi-colour' : 'two-colour' };
}

/** Preset and LED variant announced by the preset-loaded message of a looper-layout preset, or null. */
export function presetAnnouncementOf(preset: Preset | null): PresetAnnouncement | null {
  const channel = looperChannelOf(preset);
  if (!preset || channel === null) return null;
  const m = preset.midi[0];
  if (m.msgType !== MSG.LOAD_CC || m.channel !== channel || m.data[0] !== LOOPER_PRESET_LOADED_CC) return null;
  return decodePresetLoadedValue(m.data[1]);
}

/** What differs between the presets of `BitwigPresetKind`; everything else follows docs/PACER-MAP.md. */
export interface BitwigPresetSpec {
  kind: BitwigPresetKind;
  name: string;
  /** Two-colour: on colour of step 1 for switch index 0..9 (SW1..SW6, SWA..SWD). */
  twoColourOn: (switchIndex: number) => number;
}

const LOOPER_SPEC: BitwigPresetSpec = {
  kind: 'looper',
  name: LOOPER_PRESET_NAME,
  twoColourOn: (s) => (s < LOOPER_LOOP_SWITCH_COUNT ? LOOPER_COLOURS.red : LOOPER_COLOURS.white),
};

/**
 * Build the template. `channel` (1–16, default 16) replaces every channel-16 value — steps, pedals and the
 * preset-loaded CC; CC numbers and everything else stay the same. It must match the extension's
 * "Looper MIDI channel" setting.
 */
export function buildBitwigLooperPreset(mode: LooperLedMode, channel: number = LOOPER_CHANNEL): Preset {
  return buildBitwigPreset(LOOPER_SPEC, mode, channel);
}

/** Build a preset laid out like the looper template (see `buildBitwigLooperPreset`) from its spec. */
export function buildBitwigPreset(spec: BitwigPresetSpec, mode: LooperLedMode, channel: number = LOOPER_CHANNEL): Preset {
  if (!Number.isInteger(channel) || channel < 1 || channel > 16) throw new RangeError(`Invalid MIDI channel ${channel}`);
  const preset = buildDefaultChannelPreset(spec, mode);
  if (channel === LOOPER_CHANNEL) return preset;
  for (const control of Object.values(preset.controls)) {
    for (const step of control.steps) if (step.channel === LOOPER_CHANNEL) step.channel = channel;
  }
  for (const setting of preset.midi) if (setting.channel === LOOPER_CHANNEL) setting.channel = channel;
  return preset;
}

/** Short labels shown on the switch screens in the editor (not stored on the Pacer). */
export const LOOPER_LABELS: ControlLabels = {
  SW1: 'LOOP 1',
  SW2: 'LOOP 2',
  SW3: 'LOOP 3',
  SW4: 'LOOP 4',
  SW5: 'UNDO',
  SW6: 'PLAY ALL',
  SWA: 'PREV ROW',
  SWB: 'NEXT ROW',
  SWC: 'OVERDUB',
  SWD: 'TAP',
  FS1: '1-BTN LOOP',
  FS2: 'PLAY ALL',
  FS3: '—',
  FS4: '—',
  EXP1: 'TRK VOL',
  EXP2: 'MASTER',
};

function ccTrigger(cc: number): Step {
  return createStep({ channel: LOOPER_CHANNEL, msgType: MSG.SW_CC_TRIGGER, data: [cc, 127, 0], active: true });
}

/** Same shape as the factory's unused switch steps. */
function unusedSwitchStep(data: [number, number, number] = [0, 127, 0]): Step {
  return createStep({ channel: 0, msgType: MSG.OFF, data, active: false });
}

function buildDefaultChannelPreset(spec: BitwigPresetSpec, mode: LooperLedMode): Preset {
  const multi = mode === 'multi-colour';
  const preset = createPreset(spec.name);

  LOOPER_SWITCHES.forEach((key, s) => {
    const control = preset.controls[key];
    control.mode = CONTROL_MODE_ALL;
    control.steps = [ccTrigger(LOOPER_SWITCH_CC_BASE + s)];
    for (let step = 2; step <= 6; step++) {
      control.steps.push(multi ? ccTrigger(colourSlotCc(s, step)) : unusedSwitchStep());
    }
    if (multi) {
      control.leds = LOOPER_SLOT_COLOURS.map((onColor) =>
        createLed({ midiCtrl: true, onColor, offColor: LED_COLOR_OFF, num: 0 }),
      );
    } else {
      control.leds = [createLed({ midiCtrl: true, onColor: spec.twoColourOn(s), offColor: LED_COLOR_OFF, num: 0 })];
      for (let step = 2; step <= 6; step++) {
        // "Only step 1 carries LED config"
        control.leds.push(createLed({ midiCtrl: false, onColor: LED_COLOR_OFF, offColor: LED_COLOR_OFF, num: 0 }));
      }
    }
  });

  LOOPER_FOOTSWITCHES.forEach((key, f) => {
    const control = preset.controls[key];
    control.mode = CONTROL_MODE_ALL;
    control.steps = [ccTrigger(LOOPER_FOOTSWITCH_CC_BASE + f)];
    for (let step = 2; step <= 6; step++) control.steps.push(unusedSwitchStep());
  });

  LOOPER_EXPRESSION.forEach((key, e) => {
    const control = preset.controls[key];
    control.mode = CONTROL_MODE_ALL;
    control.steps = [
      createStep({
        channel: LOOPER_CHANNEL,
        msgType: MSG.AD_CC,
        data: [LOOPER_EXPRESSION_CC_BASE + e, 0, 127],
        active: true,
      }),
    ];
    // Same shape as the factory's unused pedal steps
    for (let step = 2; step <= 6; step++) {
      control.steps.push(createStep({ channel: 0, msgType: MSG.AD_CC, data: [0, 0, 127], active: false }));
    }
  });

  // The preset-loaded value also tells the extension which preset and LED variant this is.
  preset.midi[0] = createMidiSetting({
    channel: LOOPER_CHANNEL,
    msgType: MSG.LOAD_CC,
    data: [LOOPER_PRESET_LOADED_CC, BITWIG_PRESET_LOADED_VALUE[spec.kind][mode], 0],
  });

  return preset;
}
