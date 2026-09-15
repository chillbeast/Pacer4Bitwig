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

/** Looper role per control (default layout), from PACER-MAP.md. */
export const LOOPER_ROLES: Readonly<Record<ControlKey, string>> = {
  SW1: 'Loop track 1',
  SW2: 'Loop track 2',
  SW3: 'Loop track 3',
  SW4: 'Loop track 4',
  SW5: 'Undo · hold: Redo',
  SW6: 'Play/stop all loops · hold: clear row',
  SWA: 'Previous scene row · hold: tracks ←',
  SWB: 'Next scene row · hold: tracks →',
  SWC: 'Launcher overdub · hold: metronome',
  SWD: 'Tap tempo · hold: transport play/stop',
  FS1: 'Smart loop on the selected track',
  FS2: 'Undo',
  FS3: '(unassigned)',
  FS4: '(unassigned)',
  EXP1: 'Selected track volume',
  EXP2: 'Master volume',
};

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
  FS1: 'SMART',
  FS2: 'UNDO',
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

export function buildBitwigLooperPreset(mode: LooperLedMode): Preset {
  const multi = mode === 'multi-colour';
  const preset = createPreset(LOOPER_PRESET_NAME);

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
      const onColor = s < LOOPER_LOOP_SWITCH_COUNT ? LOOPER_COLOURS.red : LOOPER_COLOURS.white;
      control.leds = [createLed({ midiCtrl: true, onColor, offColor: LED_COLOR_OFF, num: 0 })];
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

  preset.midi[0] = createMidiSetting({
    channel: LOOPER_CHANNEL,
    msgType: MSG.LOAD_CC,
    data: [LOOPER_PRESET_LOADED_CC, 127, 0],
  });

  return preset;
}
