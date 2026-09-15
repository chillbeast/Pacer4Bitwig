/**
 * "Bitwig FX" template — the FX preset of docs/FX-PRESET.md. On the Pacer it is the Bitwig Looper preset
 * (docs/PACER-MAP.md) with its own name, preset-loaded value and two-colour LEDs; the extension gives the switches
 * their FX meaning.
 */
import type { ControlKey } from '../pacer/constants';
import type { ControlLabels } from '../pacer/json';
import type { Preset } from '../pacer/model';
import {
  BITWIG_PRESET_LOADED_VALUE,
  LOOPER_CHANNEL,
  LOOPER_COLOURS,
  buildBitwigPreset,
  type BitwigPresetSpec,
  type LooperLedMode,
  type TapHold,
} from './bitwigLooper';

/** D2 */
export const FX_DEFAULT_SLOT = 0x14;
export const FX_PRESET_NAME = 'FX';
export const FX_PRESET_LOADED_VALUE = BITWIG_PRESET_LOADED_VALUE.fx;
/** SW1..SW6 switch effects. */
export const FX_SWITCH_COUNT = 6;

const FX_SPEC: BitwigPresetSpec = {
  kind: 'fx',
  name: FX_PRESET_NAME,
  twoColourOn: (s) => (s < FX_SWITCH_COUNT ? LOOPER_COLOURS.green : LOOPER_COLOURS.white),
};

/** Build the template; `channel` works like in `buildBitwigLooperPreset` (the same Looper MIDI channel setting). */
export function buildBitwigFxPreset(mode: LooperLedMode, channel: number = LOOPER_CHANNEL): Preset {
  return buildBitwigPreset(FX_SPEC, mode, channel);
}

/** PACER Looper default actions of the FX preset (docs/FX-PRESET.md, layout). Editor-side only. */
export const FX_TAP_HOLD: Readonly<Record<ControlKey, TapHold>> = {
  SW1: { tap: 'FX 1', hold: 'FX 1 while held' },
  SW2: { tap: 'FX 2', hold: 'FX 2 while held' },
  SW3: { tap: 'FX 3', hold: 'FX 3 while held' },
  SW4: { tap: 'FX 4', hold: 'FX 4 while held' },
  SW5: { tap: 'FX 5', hold: 'FX 5 while held' },
  SW6: { tap: 'FX 6', hold: 'FX 6 while held' },
  SWA: { tap: 'Instrument A', hold: 'Assign track' },
  SWB: { tap: 'Instrument B', hold: 'Assign track' },
  SWC: { tap: 'Instrument C', hold: 'Assign track' },
  SWD: { tap: 'Next snapshot', hold: 'Store snapshot' },
  FS1: { tap: 'One-button looper', hold: 'Clear last' },
  FS2: { tap: 'Play/stop all', hold: 'Clear row' },
  FS3: { tap: 'Next instrument' },
  FS4: { tap: 'Next snapshot' },
  EXP1: { tap: 'Remote control 7' },
  EXP2: { tap: 'Remote control 8' },
};

/** Short labels shown on the switch screens in the editor (not stored on the Pacer). */
export const FX_LABELS: ControlLabels = {
  SW1: 'FX 1',
  SW2: 'FX 2',
  SW3: 'FX 3',
  SW4: 'FX 4',
  SW5: 'FX 5',
  SW6: 'FX 6',
  SWA: 'INSTR A',
  SWB: 'INSTR B',
  SWC: 'INSTR C',
  SWD: 'SNAPSHOT',
  FS1: '1-BTN LOOP',
  FS2: 'PLAY ALL',
  FS3: 'NEXT INSTR',
  FS4: 'SNAPSHOT',
  EXP1: 'REMOTE 7',
  EXP2: 'REMOTE 8',
};
