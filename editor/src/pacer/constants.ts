/**
 * Nektar Pacer SysEx protocol constants.
 *
 * Protocol knowledge derived from the GPL-3.0-or-later "pacer-editor" by François Georgy
 * (https://github.com/francoisgeorgy/pacer-editor) and verified against the factory dumps.
 */

export const SYSEX_START = 0xf0;
export const SYSEX_END = 0xf7;

/** Nektar Technology manufacturer id. */
export const MANUFACTURER_ID = [0x00, 0x01, 0x77] as const;
/** Byte that follows the manufacturer id in every Pacer message. */
export const DEVICE_BYTE = 0x7f;
/** F0 00 01 77 7F */
export const PACER_HEADER = [SYSEX_START, ...MANUFACTURER_ID, DEVICE_BYTE] as const;
/** Offset of the command byte inside a full message (F0 included). */
export const CMD_OFFSET = PACER_HEADER.length;

export const CMD_SET = 0x01;
export const CMD_GET = 0x02;

export const TARGET_PRESET = 0x01;
export const TARGET_GLOBAL = 0x05;
export const TARGET_BACKUP = 0x7f;

export const OBJ_NAME = 0x01;
export const OBJ_PRESET_MIDI = 0x7e;
export const OBJ_ALL = 0x7f;

export const ELM_CONTROL_MODE = 0x60;
export const ELM_STEP_FIRST = 0x01;
export const ELM_STEP_LAST = 0x24;
export const ELM_LED_FIRST = 0x40;
export const ELM_LED_LAST = 0x57;
export const ELM_ALL = 0x7f;

export const STEP_COUNT = 6;
export const PRESET_MIDI_COUNT = 16;
export const PRESET_NAME_LENGTH = 5;

/** Preset index 0 is the "current" (working) preset; A1..D6 are 1..24. */
export const CURRENT_PRESET_INDEX = 0;
export const FIRST_STORED_PRESET = 1;
export const LAST_STORED_PRESET = 24;
export const SLOT_COUNT = 25;
/** The Pacer never answers a GET for D6. */
export const D6_INDEX = 0x18;

/** Number of messages the Pacer sends for one preset (name + controls + LEDs + preset MIDI). */
export const SINGLE_PRESET_MESSAGES = 189;
/** GET target preset / idx 0x7F. */
export const ALL_PRESETS_MESSAGES = 4536;
/** GET target 0x7F (full backup): 25 presets + 37 global messages. */
export const FULL_BACKUP_MESSAGES = 4762;

// ---------------------------------------------------------------------------------------------
// Controls
// ---------------------------------------------------------------------------------------------

export type ControlKey =
  | 'SW1' | 'SW2' | 'SW3' | 'SW4' | 'SW5' | 'SW6'
  | 'SWA' | 'SWB' | 'SWC' | 'SWD'
  | 'FS1' | 'FS2' | 'FS3' | 'FS4'
  | 'EXP1' | 'EXP2';

export type ControlKind = 'stompswitch' | 'footswitch' | 'expression';

export interface ControlDef {
  readonly key: ControlKey;
  /** SysEx object id. */
  readonly obj: number;
  readonly kind: ControlKind;
  /** "Switch A", "Footswitch 1", "Expression 1". */
  readonly label: string;
  /** "A", "1", "FS1", "EXP1". */
  readonly short: string;
  /** Only stompswitches carry LED configuration. */
  readonly hasLeds: boolean;
  /** Value used by Step Select / Step Inc-Dec messages to target this control. */
  readonly targetValue: number;
}

function def(
  key: ControlKey,
  obj: number,
  kind: ControlKind,
  label: string,
  short: string,
  targetValue: number,
): ControlDef {
  return { key, obj, kind, label, short, hasLeds: kind === 'stompswitch', targetValue };
}

/** All editable controls, in the order the Pacer dumps them. */
export const CONTROLS: readonly ControlDef[] = [
  def('SW1', 0x0d, 'stompswitch', 'Switch 1', '1', 0),
  def('SW2', 0x0e, 'stompswitch', 'Switch 2', '2', 1),
  def('SW3', 0x0f, 'stompswitch', 'Switch 3', '3', 2),
  def('SW4', 0x10, 'stompswitch', 'Switch 4', '4', 3),
  def('SW5', 0x11, 'stompswitch', 'Switch 5', '5', 4),
  def('SW6', 0x12, 'stompswitch', 'Switch 6', '6', 5),
  def('SWA', 0x14, 'stompswitch', 'Switch A', 'A', 7),
  def('SWB', 0x15, 'stompswitch', 'Switch B', 'B', 8),
  def('SWC', 0x16, 'stompswitch', 'Switch C', 'C', 9),
  def('SWD', 0x17, 'stompswitch', 'Switch D', 'D', 10),
  def('FS1', 0x18, 'footswitch', 'Footswitch 1', 'FS1', 11),
  def('FS2', 0x19, 'footswitch', 'Footswitch 2', 'FS2', 12),
  def('FS3', 0x1a, 'footswitch', 'Footswitch 3', 'FS3', 13),
  def('FS4', 0x1b, 'footswitch', 'Footswitch 4', 'FS4', 14),
  def('EXP1', 0x36, 'expression', 'Expression 1', 'EXP1', 15),
  def('EXP2', 0x37, 'expression', 'Expression 2', 'EXP2', 16),
];

export const CONTROL_KEYS: readonly ControlKey[] = CONTROLS.map((c) => c.key);

export const CONTROL_BY_KEY: Readonly<Record<ControlKey, ControlDef>> = Object.fromEntries(
  CONTROLS.map((c) => [c.key, c]),
) as Record<ControlKey, ControlDef>;

export const CONTROL_BY_OBJ: ReadonlyMap<number, ControlDef> = new Map(CONTROLS.map((c) => [c.obj, c]));

export const CONTROL_BY_TARGET: ReadonlyMap<number, ControlDef> = new Map(
  CONTROLS.map((c) => [c.targetValue, c]),
);

export const STOMPSWITCH_KEYS: readonly ControlKey[] = CONTROLS.filter((c) => c.kind === 'stompswitch').map(
  (c) => c.key,
);

// ---------------------------------------------------------------------------------------------
// Control modes
// ---------------------------------------------------------------------------------------------

export const CONTROL_MODE_ALL = 0x00;
export const CONTROL_MODE_SEQUENCE = 0x01;
export const CONTROL_MODE_EXTERNAL = 0x02;

export interface ControlModeInfo {
  readonly value: number;
  readonly name: string;
  readonly short: string;
  readonly description: string;
}

export const CONTROL_MODES: readonly ControlModeInfo[] = [
  {
    value: CONTROL_MODE_ALL,
    name: 'All steps at once',
    short: 'All',
    description: 'Every active step is sent on each press.',
  },
  {
    value: CONTROL_MODE_SEQUENCE,
    name: 'Sequence',
    short: 'Seq',
    description: 'Each press sends the next active step.',
  },
  {
    value: CONTROL_MODE_EXTERNAL,
    name: 'External step select',
    short: 'Ext',
    description: 'The step is chosen by another control (Step Select / Step Inc-Dec).',
  },
];

// ---------------------------------------------------------------------------------------------
// Message types
// ---------------------------------------------------------------------------------------------

export const MSG = {
  // analog (expression pedal) types
  AD_CC: 0x00,
  AD_PITCH_BEND: 0x01,
  AD_AFTERTOUCH: 0x02,
  AD_NRPN_COARSE: 0x03,
  AD_NRPN_FINE: 0x04,
  // switch types
  SW_CC_TRIGGER: 0x40,
  SW_NOTE: 0x43,
  SW_NOTE_TOGGLE: 0x44,
  SW_PROGRAM_BANK: 0x45,
  SW_PROGRAM_STEP: 0x46,
  SW_CC_TOGGLE: 0x47,
  SW_CC_STEP: 0x48,
  SW_MMC: 0x55,
  SW_PRESET_INC_DEC: 0x56,
  SW_NRPN_COARSE: 0x57,
  SW_NRPN_FINE: 0x58,
  SW_RELAY: 0x59,
  OFF: 0x61,
  SW_PRESET_SELECT: 0x62,
  SW_STEP_SELECT: 0x63,
  SW_STEP_INC_DEC: 0x64,
  /** CC as used by the preset "on load" MIDI messages. */
  LOAD_CC: 0x65,
  DAW_FUNCTION: 0x7e,
} as const;

export type FieldKind =
  | 'unused'
  | 'number'
  | 'cc'
  | 'note'
  | 'preset'
  | 'target'
  | 'incdec'
  | 'relayMode'
  | 'relay'
  | 'mmc'
  | 'step'
  | 'deviceId';

export interface FieldSpec {
  readonly label: string;
  readonly kind: FieldKind;
  readonly hint?: string;
}

export interface MsgTypeInfo {
  readonly value: number;
  readonly name: string;
  readonly short: string;
  readonly group: string;
  readonly fields: readonly [FieldSpec, FieldSpec, FieldSpec];
}

const U: FieldSpec = { label: 'not used', kind: 'unused' };
const n = (label: string, hint?: string): FieldSpec => ({ label, kind: 'number', hint });
const f = (label: string, kind: FieldKind, hint?: string): FieldSpec => ({ label, kind, hint });

function msg(
  value: number,
  name: string,
  short: string,
  group: string,
  fields: readonly [FieldSpec, FieldSpec, FieldSpec],
): MsgTypeInfo {
  return { value, name, short, group, fields };
}

export const MSG_TYPES: ReadonlyMap<number, MsgTypeInfo> = new Map(
  [
    msg(MSG.AD_CC, 'CC', 'CC', 'Continuous', [f('Controller', 'cc'), n('Min'), n('Max')]),
    msg(MSG.AD_PITCH_BEND, 'Pitch Bend', 'Pitch', 'Continuous', [U, n('Min'), n('Max')]),
    msg(MSG.AD_AFTERTOUCH, 'Channel Aftertouch', 'AT', 'Continuous', [U, n('Min'), n('Max')]),
    msg(MSG.AD_NRPN_COARSE, 'NRPN Coarse', 'NRPN C', 'Continuous', [n('Max'), n('NRPN LSB'), n('NRPN MSB')]),
    msg(MSG.AD_NRPN_FINE, 'NRPN Fine', 'NRPN F', 'Continuous', [n('Max'), n('NRPN LSB'), n('NRPN MSB')]),

    msg(MSG.SW_CC_TRIGGER, 'CC Trigger', 'CC Trig', 'Control Change', [f('Controller', 'cc'), n('Down'), n('Up')]),
    msg(MSG.SW_CC_TOGGLE, 'CC Toggle', 'CC Tgl', 'Control Change', [
      f('Controller', 'cc'),
      n('Value 1'),
      n('Value 2'),
    ]),
    msg(MSG.SW_CC_STEP, 'CC Step', 'CC Step', 'Control Change', [f('Controller', 'cc'), n('Start'), n('End')]),
    msg(MSG.SW_NOTE, 'Note', 'Note', 'Notes', [f('Note', 'note'), n('Velocity'), U]),
    msg(MSG.SW_NOTE_TOGGLE, 'Note Toggle', 'Note Tgl', 'Notes', [f('Note', 'note'), n('Velocity'), U]),
    msg(MSG.SW_PROGRAM_BANK, 'Program & Bank', 'Program', 'Program', [
      n('Program'),
      n('Bank LSB'),
      n('Bank MSB'),
    ]),
    msg(MSG.SW_PROGRAM_STEP, 'Program Step', 'Prg Step', 'Program', [U, n('Start'), n('End')]),
    msg(MSG.SW_NRPN_COARSE, 'NRPN Coarse', 'NRPN C', 'NRPN', [n('Value'), n('NRPN LSB'), n('NRPN MSB')]),
    msg(MSG.SW_NRPN_FINE, 'NRPN Fine', 'NRPN F', 'NRPN', [n('Value'), n('NRPN LSB'), n('NRPN MSB')]),
    msg(MSG.SW_MMC, 'MIDI Machine Control', 'MMC', 'Transport & hardware', [
      f('Device ID', 'deviceId', '127 = all devices'),
      f('Command', 'mmc'),
      U,
    ]),
    msg(MSG.SW_RELAY, 'Relay Output', 'Relay', 'Transport & hardware', [
      f('Mode', 'relayMode', 'Labels from the original editor; not verified on hardware'),
      f('Relay', 'relay'),
      U,
    ]),
    msg(MSG.SW_PRESET_SELECT, 'Preset Select', 'Preset', 'Pacer', [f('Preset', 'preset'), U, U]),
    msg(MSG.SW_PRESET_INC_DEC, 'Preset Inc/Dec', 'Preset ±', 'Pacer', [f('Direction', 'incdec'), U, U]),
    msg(MSG.SW_STEP_SELECT, 'Step Select', 'Step Sel', 'Pacer', [f('Target', 'target'), f('Step', 'step'), U]),
    msg(MSG.SW_STEP_INC_DEC, 'Step Inc/Dec', 'Step ±', 'Pacer', [
      f('Target', 'target'),
      f('Direction', 'incdec'),
      U,
    ]),
    msg(MSG.OFF, 'Off', 'Off', 'Off', [U, U, U]),
    msg(MSG.LOAD_CC, 'CC', 'CC', 'Control Change', [f('Controller', 'cc'), n('Value'), U]),
    msg(MSG.DAW_FUNCTION, 'DAW Function', 'DAW', 'Continuous', [n('Function'), U, U]),
  ].map((m) => [m.value, m]),
);

export function msgTypeInfo(value: number): MsgTypeInfo {
  return (
    MSG_TYPES.get(value) ?? {
      value,
      name: `Unknown (0x${value.toString(16).toUpperCase().padStart(2, '0')})`,
      short: `0x${value.toString(16).toUpperCase().padStart(2, '0')}`,
      group: 'Unknown',
      fields: [n('Data 1'), n('Data 2'), n('Data 3')],
    }
  );
}

/** Allowed step message types per control kind (from the original editor), grouped order. */
export const MSG_TYPES_SWITCH: readonly number[] = [
  MSG.SW_CC_TRIGGER,
  MSG.SW_CC_TOGGLE,
  MSG.SW_CC_STEP,
  MSG.SW_NOTE,
  MSG.SW_NOTE_TOGGLE,
  MSG.SW_PROGRAM_BANK,
  MSG.SW_PROGRAM_STEP,
  MSG.SW_NRPN_COARSE,
  MSG.SW_NRPN_FINE,
  MSG.SW_MMC,
  MSG.SW_RELAY,
  MSG.SW_PRESET_SELECT,
  MSG.SW_PRESET_INC_DEC,
  MSG.SW_STEP_SELECT,
  MSG.SW_STEP_INC_DEC,
  MSG.OFF,
];

export const MSG_TYPES_FOOTSWITCH: readonly number[] = MSG_TYPES_SWITCH;

export const MSG_TYPES_EXPRESSION: readonly number[] = [
  MSG.AD_CC,
  MSG.AD_NRPN_COARSE,
  MSG.AD_NRPN_FINE,
  MSG.AD_PITCH_BEND,
  MSG.AD_AFTERTOUCH,
  MSG.OFF,
];

export const MSG_TYPES_PRESET_MIDI: readonly number[] = [
  MSG.LOAD_CC,
  MSG.SW_NOTE,
  MSG.SW_PROGRAM_BANK,
  MSG.SW_NRPN_COARSE,
  MSG.SW_NRPN_FINE,
  MSG.SW_MMC,
  MSG.SW_RELAY,
  MSG.OFF,
];

export function allowedMsgTypes(kind: ControlKind): readonly number[] {
  switch (kind) {
    case 'stompswitch':
      return MSG_TYPES_SWITCH;
    case 'footswitch':
      return MSG_TYPES_FOOTSWITCH;
    case 'expression':
      return MSG_TYPES_EXPRESSION;
  }
}

// ---------------------------------------------------------------------------------------------
// Enumerations used by data fields
// ---------------------------------------------------------------------------------------------

export interface EnumOption {
  readonly value: number;
  readonly label: string;
}

/** Preset Select targets: 0 current?, 1 Track, 2 Transport, 3..26 A1..D6. */
export const PRESET_SELECT_OPTIONS: readonly EnumOption[] = [
  { value: 0, label: 'Current' },
  { value: 1, label: 'Track (DAW)' },
  { value: 2, label: 'Transport (DAW)' },
  ...Array.from({ length: 24 }, (_, i) => ({
    value: i + 3,
    label: `${String.fromCharCode(65 + Math.floor(i / 6))}${(i % 6) + 1}`,
  })),
];

export const INC_DEC_OPTIONS: readonly EnumOption[] = [
  { value: 0, label: 'Increment' },
  { value: 1, label: 'Decrement' },
];

export const TARGET_OPTIONS: readonly EnumOption[] = CONTROLS.map((c) => ({ value: c.targetValue, label: c.label }));

export const STEP_OPTIONS: readonly EnumOption[] = Array.from({ length: 6 }, (_, i) => ({
  value: i + 1,
  label: `Step ${i + 1}`,
}));

export const RELAY_OPTIONS: readonly EnumOption[] = Array.from({ length: 4 }, (_, i) => ({
  value: i,
  label: `R${i + 1}`,
}));

/** Labels used by the original editor for relay modes (unverified for step messages). */
export const RELAY_MODE_OPTIONS: readonly EnumOption[] = [
  { value: 0, label: 'Auto detect' },
  { value: 1, label: 'Normally open' },
  { value: 2, label: 'Normally closed' },
  { value: 3, label: 'Latching' },
];

export const MMC_COMMAND_OPTIONS: readonly EnumOption[] = [
  { value: 1, label: 'Stop' },
  { value: 2, label: 'Play' },
  { value: 3, label: 'Deferred play' },
  { value: 4, label: 'Fast forward' },
  { value: 5, label: 'Rewind' },
  { value: 6, label: 'Record strobe' },
  { value: 7, label: 'Record exit' },
  { value: 8, label: 'Record pause' },
  { value: 9, label: 'Pause' },
  { value: 10, label: 'Eject' },
  { value: 11, label: 'Chase' },
  { value: 13, label: 'MMC reset' },
];

// ---------------------------------------------------------------------------------------------
// LEDs
// ---------------------------------------------------------------------------------------------

export const LED_COLOR_OFF = 0x00;
/** Seen in factory dumps for unconfigured steps; most likely "default colour for the message type". */
export const LED_COLOR_DEFAULT = 0x7f;

export interface LedColorInfo {
  readonly value: number;
  readonly name: string;
  /** Hardware index label, e.g. "2A". */
  readonly code: string;
  readonly family: string;
  readonly dim: boolean;
  /** Approximate rendering of the LED. */
  readonly hex: string;
}

const FAMILIES: readonly (readonly [string, string])[] = [
  ['Pink', '#ff5ca8'],
  ['Red', '#ff2d2d'],
  ['Orange', '#ff7417'],
  ['Amber', '#ffb31a'],
  ['Yellow', '#ffe923'],
  ['Lime', '#a8ff2e'],
  ['Green', '#22e05a'],
  ['Teal', '#19dcc8'],
  ['Blue', '#2f6bff'],
  ['Lavender', '#b8a2ff'],
  ['Purple', '#b536ff'],
  ['White', '#f4f7ff'],
];

export const LED_COLORS: readonly LedColorInfo[] = [
  { value: LED_COLOR_OFF, name: 'Off', code: '—', family: 'Off', dim: false, hex: '#000000' },
  ...FAMILIES.flatMap(([family, hex], i) => [
    { value: i * 2 + 1, name: family, code: `${i + 1}A`, family, dim: false, hex },
    { value: i * 2 + 2, name: `Dim ${family}`, code: `${i + 1}B`, family, dim: true, hex },
  ]),
  { value: LED_COLOR_DEFAULT, name: 'Default', code: '7F', family: 'Default', dim: false, hex: '#c9d1dc' },
];

export const LED_COLOR_BY_VALUE: ReadonlyMap<number, LedColorInfo> = new Map(LED_COLORS.map((c) => [c.value, c]));

export function ledColorInfo(value: number): LedColorInfo {
  return (
    LED_COLOR_BY_VALUE.get(value) ?? {
      value,
      name: `Unknown (0x${value.toString(16).toUpperCase().padStart(2, '0')})`,
      code: '?',
      family: 'Unknown',
      dim: false,
      hex: '#808080',
    }
  );
}

export const LED_NUM_OPTIONS: readonly EnumOption[] = [
  { value: 0, label: 'Default' },
  { value: 1, label: 'Bottom' },
  { value: 2, label: 'Middle' },
  { value: 3, label: 'Top' },
];
