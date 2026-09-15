import { messageChecksumValid } from './checksum';
import {
  CMD_GET,
  CMD_SET,
  CONTROL_BY_OBJ,
  CONTROL_BY_TARGET,
  CONTROL_MODES,
  ELM_CONTROL_MODE,
  ELM_LED_FIRST,
  ELM_LED_LAST,
  ELM_STEP_FIRST,
  ELM_STEP_LAST,
  INC_DEC_OPTIONS,
  LED_NUM_OPTIONS,
  MMC_COMMAND_OPTIONS,
  MSG,
  OBJ_ALL,
  OBJ_NAME,
  OBJ_PRESET_MIDI,
  PRESET_SELECT_OPTIONS,
  RELAY_MODE_OPTIONS,
  TARGET_BACKUP,
  TARGET_GLOBAL,
  TARGET_PRESET,
  ledColorInfo,
  msgTypeInfo,
  type EnumOption,
} from './constants';
import type { PartRef } from './encode';
import type { Preset } from './model';
import { slotLabel } from './slots';
import { isPacerMessage, readElements, toHex } from './sysex';

const NOTE_NAMES = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];

/** MIDI note number → name, middle C (60) = C4. */
export function noteName(note: number): string {
  return `${NOTE_NAMES[((note % 12) + 12) % 12]}${Math.floor(note / 12) - 1}`;
}

export function channelLabel(channel: number): string {
  return channel === 0 ? 'global' : `ch${channel}`;
}

export function enumLabel(options: readonly EnumOption[], value: number): string {
  return options.find((o) => o.value === value)?.label ?? String(value);
}

function targetShort(value: number): string {
  const def = CONTROL_BY_TARGET.get(value);
  return def ? def.short : `#${value}`;
}

export interface MessageLike {
  channel: number;
  msgType: number;
  data: readonly number[];
}

/** One-line summary, e.g. "CC 102 · ch16 · 127/0". */
export function stepSummary(s: MessageLike): string {
  const [d1, d2, d3] = s.data;
  const ch = channelLabel(s.channel);
  switch (s.msgType) {
    case MSG.SW_CC_TRIGGER:
      return `CC ${d1} · ${ch} · ${d2}/${d3}`;
    case MSG.SW_CC_TOGGLE:
      return `CC ${d1} · ${ch} · ${d2}⇄${d3}`;
    case MSG.SW_CC_STEP:
      return `CC ${d1} · ${ch} · ${d2}→${d3}`;
    case MSG.AD_CC:
      return `CC ${d1} · ${ch} · ${d2}–${d3}`;
    case MSG.LOAD_CC:
      return `CC ${d1} = ${d2} · ${ch}`;
    case MSG.SW_NOTE:
      return `${noteName(d1)} · ${ch} · vel ${d2}`;
    case MSG.SW_NOTE_TOGGLE:
      return `${noteName(d1)} tgl · ${ch} · vel ${d2}`;
    case MSG.SW_PROGRAM_BANK:
      return d2 || d3 ? `PC ${d1} · ${ch} · bank ${d3}:${d2}` : `PC ${d1} · ${ch}`;
    case MSG.SW_PROGRAM_STEP:
      return `PC ${d2}→${d3} · ${ch}`;
    case MSG.SW_NRPN_COARSE:
    case MSG.SW_NRPN_FINE:
      return `NRPN ${d3}:${d2} · ${ch} · ${d1}`;
    case MSG.AD_NRPN_COARSE:
    case MSG.AD_NRPN_FINE:
      return `NRPN ${d3}:${d2} · ${ch} · max ${d1}`;
    case MSG.AD_PITCH_BEND:
      return `Pitch · ${ch} · ${d2}–${d3}`;
    case MSG.AD_AFTERTOUCH:
      return `Aftertouch · ${ch} · ${d2}–${d3}`;
    case MSG.SW_MMC:
      return `MMC ${enumLabel(MMC_COMMAND_OPTIONS, d2)} · dev ${d1}`;
    case MSG.SW_RELAY:
      return `Relay R${d2 + 1} · ${enumLabel(RELAY_MODE_OPTIONS, d1)}`;
    case MSG.SW_PRESET_SELECT:
      return `Preset → ${enumLabel(PRESET_SELECT_OPTIONS, d1)}`;
    case MSG.SW_PRESET_INC_DEC:
      return d1 ? 'Preset −' : 'Preset +';
    case MSG.SW_STEP_SELECT:
      return `${targetShort(d1)} → step ${d2}`;
    case MSG.SW_STEP_INC_DEC:
      return `${targetShort(d1)} step ${d2 ? '−' : '+'}`;
    case MSG.DAW_FUNCTION:
      return `DAW fn ${d1}`;
    case MSG.OFF:
      return 'Off';
    default:
      return `${msgTypeInfo(s.msgType).short} · ${ch} · ${d1} ${d2} ${d3}`;
  }
}

export type MidiKind = 'sysex' | 'channel' | 'system' | 'realtime' | 'invalid';

export interface MidiDescription {
  kind: MidiKind;
  summary: string;
  /** Pacer SysEx with a wrong checksum. */
  badChecksum?: boolean;
}

const CC_NAMES: Record<number, string> = {
  0: 'Bank MSB',
  1: 'Mod wheel',
  7: 'Volume',
  10: 'Pan',
  11: 'Expression',
  32: 'Bank LSB',
  64: 'Sustain',
  120: 'All sound off',
  121: 'Reset controllers',
  123: 'All notes off',
};

function describePacerSysex(msg: Uint8Array): MidiDescription {
  const badChecksum = !messageChecksumValid(msg);
  const end = msg.length - 2;
  const cmd = msg[5];
  const target = msg[6];
  const index = end > 7 ? msg[7] : undefined;
  const obj = end > 8 ? msg[8] : undefined;
  const d = (summary: string): MidiDescription => ({ kind: 'sysex', summary, badChecksum });

  if (cmd === CMD_GET) {
    if (target === TARGET_BACKUP) return d('GET full backup');
    if (target === TARGET_PRESET) {
      if (index === 0x7f) return d('GET all presets');
      if (index === undefined) return d('GET preset');
      if (obj === undefined || obj === OBJ_ALL) return d(`GET preset ${slotLabel(index)} (all)`);
      const def = CONTROL_BY_OBJ.get(obj);
      return d(`GET preset ${slotLabel(index)} · ${def ? def.label : `obj 0x${obj.toString(16)}`}`);
    }
    if (target === TARGET_GLOBAL) return d(`GET global${index !== undefined ? ` ${index}` : ''}`);
    return d(`GET target 0x${target.toString(16)}`);
  }

  if (cmd !== CMD_SET) return d(`Pacer cmd 0x${cmd.toString(16)}`);

  if (target === TARGET_GLOBAL) {
    const which = index === 0 ? 'current state' : `config ${index}`;
    return d(`Global ${which} · obj 0x${(obj ?? 0).toString(16)}`);
  }
  if (target !== TARGET_PRESET || index === undefined || obj === undefined) {
    return d(`SET target 0x${target.toString(16)}`);
  }
  const slot = slotLabel(index);

  if (obj === OBJ_NAME) {
    const len = msg[10] ?? 0;
    const chars = Array.from(msg.subarray(11, Math.min(11 + len, end)), (c) => String.fromCharCode(c)).join('');
    return d(`${slot} · name "${chars}"`);
  }

  const elements = readElements(msg, 9, end);
  const value = (elm: number) => elements.find((e) => e.elm === elm)?.value ?? 0;

  if (obj === OBJ_PRESET_MIDI && elements.length > 0) {
    const first = elements[0].elm;
    const s = Math.floor((first - 1) / 6);
    const base = s * 6;
    const summary = stepSummary({
      channel: value(base + 1),
      msgType: value(base + 2),
      data: [value(base + 3), value(base + 4), value(base + 5)],
    });
    return d(`${slot} · on-load MIDI ${s + 1}: ${summary}`);
  }

  const def = CONTROL_BY_OBJ.get(obj);
  if (!def || elements.length === 0) return d(`${slot} · obj 0x${obj.toString(16)}`);
  const first = elements[0].elm;

  if (first === ELM_CONTROL_MODE) {
    const mode = CONTROL_MODES.find((m) => m.value === elements[0].value);
    return d(`${slot} · ${def.label} · mode ${mode ? mode.name : elements[0].value}`);
  }
  if (first >= ELM_STEP_FIRST && first <= ELM_STEP_LAST) {
    const s = Math.floor((first - 1) / 6);
    const base = s * 6;
    const type = value(base + 2);
    const summary = stepSummary({
      channel: value(base + 1),
      msgType: type,
      data: [value(base + 3), value(base + 4), value(base + 5)],
    });
    const active = value(base + 6) ? '' : ' (inactive)';
    return d(`${slot} · ${def.label} · step ${s + 1}: ${msgTypeInfo(type).name} ${summary}${active}`);
  }
  if (first >= ELM_LED_FIRST && first <= ELM_LED_LAST) {
    const s = Math.floor((first - ELM_LED_FIRST) / 4);
    const base = ELM_LED_FIRST + s * 4;
    const on = ledColorInfo(value(base + 1)).name;
    const off = ledColorInfo(value(base + 2)).name;
    const num = enumLabel(LED_NUM_OPTIONS, value(base + 3));
    const ctrl = value(base) ? 'MIDI ctrl' : 'local';
    return d(`${slot} · ${def.label} · LED ${s + 1}: on ${on}, off ${off}, ${num}, ${ctrl}`);
  }
  return d(`${slot} · ${def.label} · elm 0x${first.toString(16)}`);
}

/** Human description of any MIDI message, for the monitor. */
export function describeMidiMessage(bytes: Uint8Array): MidiDescription {
  if (bytes.length === 0) return { kind: 'invalid', summary: 'Empty message' };
  const status = bytes[0];

  if (status === 0xf0) {
    if (isPacerMessage(bytes)) return describePacerSysex(bytes);
    if (bytes[1] === 0x7e && bytes[3] === 0x06 && bytes[4] === 0x01) return { kind: 'sysex', summary: 'Identity request' };
    if (bytes[1] === 0x7e && bytes[3] === 0x06 && bytes[4] === 0x02) {
      return { kind: 'sysex', summary: `Identity reply · ${toHex(bytes.subarray(5, bytes.length - 1))}` };
    }
    if (bytes[1] === 0x7f && bytes[3] === 0x06 && bytes.length >= 6) {
      const command = enumLabel(MMC_COMMAND_OPTIONS, bytes[4]);
      return { kind: 'sysex', summary: `MMC ${command} · device ${bytes[2]}` };
    }
    const maker = toHex(bytes.subarray(1, bytes[1] === 0 ? 4 : 2));
    return { kind: 'sysex', summary: `SysEx · manufacturer ${maker} · ${bytes.length} bytes` };
  }

  if (status >= 0xf8) {
    const names: Record<number, string> = {
      0xf8: 'Clock',
      0xfa: 'Start',
      0xfb: 'Continue',
      0xfc: 'Stop',
      0xfe: 'Active sensing',
      0xff: 'Reset',
    };
    return { kind: 'realtime', summary: names[status] ?? `Realtime 0x${status.toString(16)}` };
  }
  if (status >= 0xf1) {
    const names: Record<number, string> = {
      0xf1: 'MTC quarter frame',
      0xf2: 'Song position',
      0xf3: 'Song select',
      0xf6: 'Tune request',
      0xf7: 'End of SysEx',
    };
    return { kind: 'system', summary: names[status] ?? `System 0x${status.toString(16)}` };
  }
  if (status < 0x80) return { kind: 'invalid', summary: `Data without status: ${toHex(bytes)}` };

  const type = status & 0xf0;
  const ch = (status & 0x0f) + 1;
  const d1 = bytes[1] ?? 0;
  const d2 = bytes[2] ?? 0;
  const c = (summary: string): MidiDescription => ({ kind: 'channel', summary: `${summary} · ch${ch}` });
  switch (type) {
    case 0x80:
      return c(`Note off ${noteName(d1)} (${d1}) vel ${d2}`);
    case 0x90:
      return d2 === 0 ? c(`Note off ${noteName(d1)} (${d1})`) : c(`Note on ${noteName(d1)} (${d1}) vel ${d2}`);
    case 0xa0:
      return c(`Poly aftertouch ${noteName(d1)} = ${d2}`);
    case 0xb0:
      return c(`CC ${d1}${CC_NAMES[d1] ? ` (${CC_NAMES[d1]})` : ''} = ${d2}`);
    case 0xc0:
      return c(`Program ${d1}`);
    case 0xd0:
      return c(`Channel pressure ${d1}`);
    default:
      return c(`Pitch bend ${((d2 << 7) | d1) - 8192}`);
  }
}

export { INC_DEC_OPTIONS };

/** Human-readable value of one preset part, for diff views ("before → after"). */
export function describePartValue(part: PartRef, preset: Preset | null): string {
  if (!preset) return '—';
  switch (part.kind) {
    case 'name':
      return `“${preset.name.trimEnd()}”`;
    case 'mode': {
      const mode = preset.controls[part.control].mode;
      return CONTROL_MODES.find((m) => m.value === mode)?.name ?? `mode ${mode}`;
    }
    case 'step': {
      const step = preset.controls[part.control].steps[part.step];
      if (step.msgType === MSG.OFF) return 'Off';
      return `${msgTypeInfo(step.msgType).name} · ${stepSummary(step)}${step.active ? '' : ' (inactive)'}`;
    }
    case 'led': {
      const led = preset.controls[part.control].leds?.[part.step];
      if (!led) return '—';
      return `on ${ledColorInfo(led.onColor).name} / off ${ledColorInfo(led.offColor).name} · ${enumLabel(LED_NUM_OPTIONS, led.num)} · ${led.midiCtrl ? 'MIDI ctrl' : 'local'}`;
    }
    case 'midi': {
      const m = preset.midi[part.setting];
      return m.msgType === MSG.OFF ? 'Off' : `${msgTypeInfo(m.msgType).name} · ${stepSummary(m)}`;
    }
  }
}
