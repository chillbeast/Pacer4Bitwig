import { messageChecksumValid } from './checksum';
import {
  CMD_SET,
  CONTROLS,
  CONTROL_BY_OBJ,
  ELM_CONTROL_MODE,
  ELM_LED_FIRST,
  ELM_LED_LAST,
  ELM_STEP_FIRST,
  ELM_STEP_LAST,
  LAST_STORED_PRESET,
  OBJ_NAME,
  OBJ_PRESET_MIDI,
  PRESET_MIDI_COUNT,
  STEP_COUNT,
  TARGET_GLOBAL,
  TARGET_PRESET,
} from './constants';
import type { PartRef } from './encode';
import { createPreset, normalizeName, type Preset } from './model';
import { slotLabel } from './slots';
import { isPacerMessage, readElements, splitSysex, toHex } from './sysex';

/** Name + (mode + 6 steps [+ 6 LEDs]) per control + 16 preset MIDI settings = 189. */
export const PRESET_PART_COUNT =
  1 + CONTROLS.reduce((n, c) => n + 1 + STEP_COUNT + (c.hasLeds ? STEP_COUNT : 0), 0) + PRESET_MIDI_COUNT;

export interface ParsedPreset {
  index: number;
  preset: Preset;
  /** Distinct parts (name, control modes, steps, LEDs, preset MIDI settings) found in the data. */
  parts: number;
  /** True when every part of the preset was present (a full preset dump). */
  complete: boolean;
  /** True when at least one LED config was present. */
  hasLeds: boolean;
  /** Parts that were not in the data (empty for a complete preset). */
  missing: PartRef[];
}

/** Every part of a preset in dump order, with the key used while parsing. */
export function presetPartKeys(): { key: string; part: PartRef }[] {
  const out: { key: string; part: PartRef }[] = [{ key: 'name', part: { kind: 'name' } }];
  for (const def of CONTROLS) {
    out.push({ key: `${def.key}:mode`, part: { kind: 'mode', control: def.key } });
    for (let s = 0; s < STEP_COUNT; s++) out.push({ key: `${def.key}:s${s}`, part: { kind: 'step', control: def.key, step: s } });
    if (def.hasLeds) {
      for (let s = 0; s < STEP_COUNT; s++) out.push({ key: `${def.key}:l${s}`, part: { kind: 'led', control: def.key, step: s } });
    }
  }
  for (let s = 0; s < PRESET_MIDI_COUNT; s++) out.push({ key: `midi:${s}`, part: { kind: 'midi', setting: s } });
  return out;
}

export interface ParseResult {
  presets: Map<number, ParsedPreset>;
  /** Raw global configuration messages (target 0x05), preserved for backup export. */
  globals: Uint8Array[];
  messageCount: number;
  ignored: number;
  badChecksums: number;
  warnings: string[];
}

const MAX_WARNINGS = 40;

interface Builder {
  preset: Preset;
  parts: Set<string>;
  hasLeds: boolean;
}

/** Parse a SysEx dump (a .syx file or any concatenation of Pacer messages). */
export function parseDump(bytes: ArrayLike<number>): ParseResult {
  return parseMessages(splitSysex(bytes));
}

export function parseMessages(messages: readonly Uint8Array[]): ParseResult {
  const builders = new Map<number, Builder>();
  const globals: Uint8Array[] = [];
  const warnings: string[] = [];
  let ignored = 0;
  let badChecksums = 0;

  const warn = (text: string) => {
    if (warnings.length < MAX_WARNINGS) warnings.push(text);
  };

  const builderFor = (index: number): Builder => {
    let b = builders.get(index);
    if (!b) {
      b = { preset: createPreset(), parts: new Set(), hasLeds: false };
      builders.set(index, b);
    }
    return b;
  };

  for (const msg of messages) {
    if (!isPacerMessage(msg)) {
      ignored++;
      continue;
    }
    if (!messageChecksumValid(msg)) {
      badChecksums++;
      warn(`Bad checksum, message skipped: ${toHex(msg.subarray(0, 16))}…`);
      continue;
    }
    const end = msg.length - 2; // checksum position
    const cmd = msg[5];
    const target = msg[6];
    if (cmd !== CMD_SET) {
      ignored++; // GET requests, echoes
      continue;
    }
    if (target === TARGET_GLOBAL) {
      globals.push(msg.slice());
      continue;
    }
    if (target !== TARGET_PRESET || end < 10) {
      ignored++;
      continue;
    }
    const index = msg[7];
    const obj = msg[8];
    if (index > LAST_STORED_PRESET) {
      ignored++;
      warn(`Unsupported preset index 0x${index.toString(16)}`);
      continue;
    }
    const b = builderFor(index);

    if (obj === OBJ_NAME) {
      // obj, elm (0x00/0x01), length, chars
      const length = msg[10];
      const chars: string[] = [];
      for (let i = 0; i < length && 11 + i < end; i++) chars.push(String.fromCharCode(msg[11 + i]));
      b.preset.name = normalizeName(chars.join(''));
      b.parts.add('name');
      continue;
    }

    if (obj === OBJ_PRESET_MIDI) {
      for (const { elm, value } of readElements(msg, 9, end)) {
        if (elm < 0x01 || elm > PRESET_MIDI_COUNT * 6) {
          warn(`${slotLabel(index)} preset MIDI: unknown element 0x${elm.toString(16)}`);
          continue;
        }
        const s = Math.floor((elm - 1) / 6);
        const setting = b.preset.midi[s];
        switch ((elm - 1) % 6) {
          case 0:
            setting.channel = value;
            break;
          case 1:
            setting.msgType = value;
            break;
          case 2:
            setting.data[0] = value;
            break;
          case 3:
            setting.data[1] = value;
            break;
          case 4:
            setting.data[2] = value;
            break;
          default:
            break; // element 6 ("active") is not part of device dumps
        }
        b.parts.add(`midi:${s}`);
      }
      continue;
    }

    const def = CONTROL_BY_OBJ.get(obj);
    if (!def) {
      ignored++;
      warn(`${slotLabel(index)}: unsupported object 0x${obj.toString(16)}`);
      continue;
    }
    const control = b.preset.controls[def.key];
    for (const { elm, value } of readElements(msg, 9, end)) {
      if (elm >= ELM_STEP_FIRST && elm <= ELM_STEP_LAST) {
        const s = Math.floor((elm - 1) / 6);
        const step = control.steps[s];
        switch ((elm - 1) % 6) {
          case 0:
            step.channel = value;
            break;
          case 1:
            step.msgType = value;
            break;
          case 2:
            step.data[0] = value;
            break;
          case 3:
            step.data[1] = value;
            break;
          case 4:
            step.data[2] = value;
            break;
          case 5:
            step.active = value !== 0;
            break;
        }
        b.parts.add(`${def.key}:s${s}`);
      } else if (elm >= ELM_LED_FIRST && elm <= ELM_LED_LAST) {
        if (!control.leds) {
          warn(`${slotLabel(index)} ${def.label}: LED element on a control without LEDs`);
          continue;
        }
        const l = Math.floor((elm - ELM_LED_FIRST) / 4);
        const led = control.leds[l];
        switch ((elm - ELM_LED_FIRST) % 4) {
          case 0:
            led.midiCtrl = value !== 0;
            break;
          case 1:
            led.onColor = value;
            break;
          case 2:
            led.offColor = value;
            break;
          case 3:
            led.num = value;
            break;
        }
        b.parts.add(`${def.key}:l${l}`);
        b.hasLeds = true;
      } else if (elm === ELM_CONTROL_MODE) {
        control.mode = value;
        b.parts.add(`${def.key}:mode`);
      } else {
        warn(`${slotLabel(index)} ${def.label}: unknown element 0x${elm.toString(16)}`);
      }
    }
  }

  const presets = new Map<number, ParsedPreset>();
  const allParts = presetPartKeys();
  for (const index of [...builders.keys()].sort((a, b) => a - b)) {
    const b = builders.get(index)!;
    presets.set(index, {
      index,
      preset: b.preset,
      parts: b.parts.size,
      complete: b.parts.size === PRESET_PART_COUNT,
      hasLeds: b.hasLeds,
      missing: allParts.filter((p) => !b.parts.has(p.key)).map((p) => p.part),
    });
  }

  return { presets, globals, messageCount: messages.length, ignored, badChecksums, warnings };
}
