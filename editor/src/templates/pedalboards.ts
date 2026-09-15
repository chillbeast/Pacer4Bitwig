/** Generic pedalboard templates. Message types and data layouts follow the factory presets. */
import { CONTROL_MODE_ALL, MSG, STOMPSWITCH_KEYS, type ControlKey } from '../pacer/constants';
import type { ControlLabels } from '../pacer/json';
import { createLed, createPreset, createStep, type Preset, type Step } from '../pacer/model';

export interface TemplateResult {
  preset: Preset;
  labels: ControlLabels;
}

/** SW1..SW6, SWA..SWD */
const SWITCHES: readonly ControlKey[] = STOMPSWITCH_KEYS;
const FOOTSWITCHES: readonly ControlKey[] = ['FS1', 'FS2', 'FS3', 'FS4'];

/** Bright colour codes (dim = code + 1): red, orange, amber, yellow, lime, green, teal, blue, lavender, purple. */
export const PEDALBOARD_COLOURS: readonly number[] = [0x03, 0x05, 0x07, 0x09, 0x0b, 0x0d, 0x0f, 0x11, 0x13, 0x15];

export const COLOUR = {
  red: 0x03,
  amber: 0x07,
  green: 0x0d,
  blue: 0x11,
  white: 0x17,
} as const;

const unusedSwitchStep = (): Step => createStep({ channel: 0, msgType: MSG.OFF, data: [0, 127, 0], active: false });
const unusedPedalStep = (): Step => createStep({ channel: 0, msgType: MSG.AD_CC, data: [0, 0, 127], active: false });

function setSingleStep(preset: Preset, key: ControlKey, step: Step, pedal = false): void {
  const control = preset.controls[key];
  control.mode = CONTROL_MODE_ALL;
  control.steps = [step];
  for (let i = 1; i < 6; i++) control.steps.push(pedal ? unusedPedalStep() : unusedSwitchStep());
}

/** Local LED toggle: step 1 on = bright colour, off = its dim variant. */
function setLed(preset: Preset, key: ControlKey, bright: number): void {
  const leds = preset.controls[key].leds;
  if (!leds) return;
  leds[0] = createLed({ midiCtrl: false, onColor: bright, offColor: bright + 1, num: 0 });
}

function expressionPedals(preset: Preset, channel: number): void {
  setSingleStep(preset, 'EXP1', createStep({ channel, msgType: MSG.AD_CC, data: [11, 0, 127], active: true }), true);
  setSingleStep(preset, 'EXP2', createStep({ channel, msgType: MSG.AD_CC, data: [7, 0, 127], active: true }), true);
}

export const CC_TOGGLE_CHANNEL = 1;
export const CC_TOGGLE_FIRST_CC = 20;

/** CC Toggle pedalboard on channel 1: switches CC 20–29, footswitch jacks CC 30–33, pedals CC 11 / CC 7. */
export function buildCcTogglePedalboard(): TemplateResult {
  const preset = createPreset('CCTGL');
  const labels: ControlLabels = {};
  SWITCHES.forEach((key, i) => {
    const cc = CC_TOGGLE_FIRST_CC + i;
    setSingleStep(preset, key, createStep({ channel: CC_TOGGLE_CHANNEL, msgType: MSG.SW_CC_TOGGLE, data: [cc, 127, 0], active: true }));
    setLed(preset, key, PEDALBOARD_COLOURS[i]);
    labels[key] = `FX ${i + 1}`;
  });
  FOOTSWITCHES.forEach((key, i) => {
    const cc = CC_TOGGLE_FIRST_CC + SWITCHES.length + i;
    setSingleStep(preset, key, createStep({ channel: CC_TOGGLE_CHANNEL, msgType: MSG.SW_CC_TOGGLE, data: [cc, 127, 0], active: true }));
    labels[key] = `CC ${cc}`;
  });
  expressionPedals(preset, CC_TOGGLE_CHANNEL);
  labels.EXP1 = 'EXPR';
  labels.EXP2 = 'VOLUME';
  return { preset, labels };
}

/** Program change pedalboard on channel 1: switches program 0–9, FS1/FS2 program step up/down (like factory PRGM). */
export function buildProgramPedalboard(): TemplateResult {
  const preset = createPreset('PROGS');
  const labels: ControlLabels = {};
  SWITCHES.forEach((key, i) => {
    setSingleStep(preset, key, createStep({ channel: 1, msgType: MSG.SW_PROGRAM_BANK, data: [i, 0, 0], active: true }));
    setLed(preset, key, COLOUR.green);
    labels[key] = `PC ${i}`;
  });
  setSingleStep(preset, 'FS1', createStep({ channel: 1, msgType: MSG.SW_PROGRAM_STEP, data: [0, 0, 127], active: true }));
  setSingleStep(preset, 'FS2', createStep({ channel: 1, msgType: MSG.SW_PROGRAM_STEP, data: [0, 127, 0], active: true }));
  labels.FS1 = 'PRG +';
  labels.FS2 = 'PRG −';
  expressionPedals(preset, 1);
  labels.EXP1 = 'EXPR';
  labels.EXP2 = 'VOLUME';
  return { preset, labels };
}

/** MIDI Machine Control command codes (MMC spec; stop/play/ffwd/rewind/record strobe also used by factory "MMC"). */
export const MMC = {
  stop: 1,
  play: 2,
  fastForward: 4,
  rewind: 5,
  recordStrobe: 6,
  pause: 9,
} as const;

export const MMC_ALL_DEVICES = 127;

/** MMC transport: rewind, fast forward, stop, play, record, pause; C/D preset down/up; FS1 play, FS2 stop. */
export function buildMmcTransport(): TemplateResult {
  const preset = createPreset('MMC');
  const labels: ControlLabels = {};
  const mmc = (command: number) =>
    createStep({ channel: 0, msgType: MSG.SW_MMC, data: [MMC_ALL_DEVICES, command, 0], active: true });
  const layout: [ControlKey, number, number, string][] = [
    ['SW1', MMC.rewind, COLOUR.blue, 'REW'],
    ['SW2', MMC.fastForward, COLOUR.blue, 'FFWD'],
    ['SW3', MMC.stop, COLOUR.white, 'STOP'],
    ['SW4', MMC.play, COLOUR.green, 'PLAY'],
    ['SW5', MMC.recordStrobe, COLOUR.red, 'REC'],
    ['SW6', MMC.pause, COLOUR.amber, 'PAUSE'],
  ];
  for (const [key, command, colour, label] of layout) {
    setSingleStep(preset, key, mmc(command));
    setLed(preset, key, colour);
    labels[key] = label;
  }
  // like the factory presets: C = preset down, D = preset up
  setSingleStep(preset, 'SWC', createStep({ channel: 0, msgType: MSG.SW_PRESET_INC_DEC, data: [1, 0, 0], active: true }));
  setSingleStep(preset, 'SWD', createStep({ channel: 0, msgType: MSG.SW_PRESET_INC_DEC, data: [0, 0, 0], active: true }));
  labels.SWC = 'PRESET −';
  labels.SWD = 'PRESET +';
  setSingleStep(preset, 'FS1', mmc(MMC.play));
  setSingleStep(preset, 'FS2', mmc(MMC.stop));
  labels.FS1 = 'PLAY';
  labels.FS2 = 'STOP';
  return { preset, labels };
}
