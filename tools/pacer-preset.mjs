// Generates the one Pacer preset PACER Looper needs (docs/PACER-MAP.md) as a .syx file. Does not talk to the Pacer -
// use pacer-send.mjs (or Pacer Studio) to write the result.
//
//   node pacer-preset.mjs                       -> ../presets/bitwig-pacer-D1.syx
//   node pacer-preset.mjs --slot C6 --channel 5
//
// There is only one preset because the extension paints the Pacer live: colours, the display name and what every
// switch does are written to preset index 0 (RAM) while it plays, and change with the active mode. This file only has
// to give the extension a board that speaks the right CCs, plus sane behaviour when Bitwig is not running.

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
    CMD_SET, EXPRESSION_OBJECTS, FOOTSWITCH_OBJECTS, MSG_CC, MSG_CC_TRIGGER, MSG_LOAD_CC, MSG_OFF, OBJ_MIDI, OBJ_NAME,
    SWITCH_OBJECTS, TGT_PRESET, concat, isValidPacerMessage, message, presetIndex
} from './lib/pacer.mjs';

// ---- The contract (docs/PACER-MAP.md) ---------------------------------------------------------------------------

export const DEFAULT_CHANNEL = 16;
export const DEFAULT_SLOT = 'D1';
export const SWITCH_CC_BASE = 102;
export const FOOTSWITCH_CC_BASE = 112;
export const EXP_CC_BASE = 116;
export const PRESET_LOADED_CC = 119;
/** Tells the extension one of its presets was selected, so it writes the whole board again. */
export const PRESET_LOADED_VALUE = 127;
/** The stored name. The extension replaces it with the active mode's name as soon as it starts. */
export const PRESET_NAME = 'PACER';

const COLOUR = { OFF: 0x00, RED: 0x03, WHITE: 0x17, WHITE_DIM: 0x18 };
/** SW 6 is the mode switch in every mode, so it is the one switch with a colour of its own here. */
const MODE_SWITCH_INDEX = 5;

// ---- Message builders (formats verified against a full dump of the user's Pacer) ---------------------------------

const step = (idx, obj, n, { channel = 0, type = MSG_OFF, data = [0, 0, 0], active = 0 }) => {
    const e = (n - 1) * 6;
    return message ([CMD_SET, TGT_PRESET, idx, obj,
        e + 1, 1, channel, 0x00,
        e + 2, 1, type, 0x00,
        e + 3, 1, data[0], 0x00,
        e + 4, 1, data[1], 0x00,
        e + 5, 1, data[2], 0x00,
        e + 6, 1, active]);
};

const led = (idx, obj, n, { midi = 0, on = COLOUR.OFF, off = COLOUR.OFF, num = 0 }) => {
    const e = 0x40 + (n - 1) * 4;
    return message ([CMD_SET, TGT_PRESET, idx, obj,
        e, 1, midi, 0x00,
        e + 1, 1, on, 0x00,
        e + 2, 1, off, 0x00,
        e + 3, 1, num]);
};

const controlMode = (idx, obj, mode = 0x00) => message ([CMD_SET, TGT_PRESET, idx, obj, 0x60, 0x01, mode]);

// Preset MIDI settings have 5 elements - no "active" byte (unlike steps)
const midiSetting = (idx, n, { channel = 0, type = MSG_OFF, data = [0, 0, 0] }) => {
    const e = (n - 1) * 6;
    return message ([CMD_SET, TGT_PRESET, idx, OBJ_MIDI,
        e + 1, 1, channel, 0x00,
        e + 2, 1, type, 0x00,
        e + 3, 1, data[0], 0x00,
        e + 4, 1, data[1], 0x00,
        e + 5, 1, data[2]]);
};

// Always 5 characters, space padded - like every name in the factory presets and the device dumps ("MMC  ")
const name = (idx, text) => {
    const chars = text.padEnd (5).slice (0, 5);
    return message ([CMD_SET, TGT_PRESET, idx, OBJ_NAME, 0x01, chars.length, ...[...chars].map (c => c.charCodeAt (0))]);
};

/**
 * Build the preset.
 *
 * @param {string} slot Preset slot, e.g. "D1"
 * @param {number} channel MIDI channel 1-16
 * @returns {Uint8Array[]} 189 SysEx messages
 */
export function buildPreset (slot, channel = DEFAULT_CHANNEL)
{
    if (!Number.isInteger (channel) || channel < 1 || channel > 16)
        throw new Error (`Invalid MIDI channel ${channel} (expected 1..16)`);

    const idx = presetIndex (slot);
    const out = [name (idx, PRESET_NAME)];

    SWITCH_OBJECTS.forEach ((obj, s) => {
        out.push (controlMode (idx, obj));
        out.push (step (idx, obj, 1, { channel, type: MSG_CC_TRIGGER, data: [SWITCH_CC_BASE + s, 127, 0], active: 1 }));
        for (let n = 2; n <= 6; n++)
            out.push (step (idx, obj, n, { data: [0, 127, 0] }));

        // LED MIDI control stays off so the board still lights up without Bitwig; the extension turns it on for
        // every switch as soon as it starts, and from then on it owns the colours.
        out.push (led (idx, obj, 1, { on: s === MODE_SWITCH_INDEX ? COLOUR.WHITE : COLOUR.RED, off: COLOUR.WHITE_DIM }));
        for (let n = 2; n <= 6; n++)
            out.push (led (idx, obj, n, {}));
    });

    FOOTSWITCH_OBJECTS.forEach ((obj, f) => {
        out.push (controlMode (idx, obj));
        out.push (step (idx, obj, 1, { channel, type: MSG_CC_TRIGGER, data: [FOOTSWITCH_CC_BASE + f, 127, 0], active: 1 }));
        // Same shape as the factory's unused steps
        for (let n = 2; n <= 6; n++)
            out.push (step (idx, obj, n, { data: [0, 127, 0] }));
    });

    EXPRESSION_OBJECTS.forEach ((obj, e) => {
        out.push (controlMode (idx, obj));
        out.push (step (idx, obj, 1, { channel, type: MSG_CC, data: [EXP_CC_BASE + e, 0, 127], active: 1 }));
        // Same shape as the factory's unused pedal steps
        for (let n = 2; n <= 6; n++)
            out.push (step (idx, obj, n, { type: MSG_CC, data: [0, 0, 127] }));
    });

    out.push (midiSetting (idx, 1, { channel, type: MSG_LOAD_CC, data: [PRESET_LOADED_CC, PRESET_LOADED_VALUE, 0] }));
    for (let n = 2; n <= 16; n++)
        out.push (midiSetting (idx, n, {}));

    return out;
}

// ---- CLI --------------------------------------------------------------------------------------------------------

if (process.argv[1] && fileURLToPath (import.meta.url) === process.argv[1])
{
    const args = process.argv.slice (2);
    const option = (flag, fallback) => (args.includes (flag) ? args[args.indexOf (flag) + 1] : fallback);
    const slot = option ('--slot', DEFAULT_SLOT).toUpperCase ();
    const channel = Number (option ('--channel', String (DEFAULT_CHANNEL)));

    if (slot === 'D6')
        throw new Error ('D6 cannot be read back by the Pacer - pick another slot');

    const root = join (dirname (fileURLToPath (import.meta.url)), '..', 'presets');
    mkdirSync (root, { recursive: true });

    const messages = buildPreset (slot, channel);
    const invalid = messages.filter (m => !isValidPacerMessage (m)).length;
    if (messages.length !== 189 || invalid > 0)
        throw new Error (`Self-check failed: ${messages.length} messages (expected 189), ${invalid} invalid`);

    const suffix = channel === DEFAULT_CHANNEL ? '' : `-ch${channel}`;
    const file = join (root, `bitwig-pacer-${slot}${suffix}.syx`);
    writeFileSync (file, concat (messages));
    console.log (`${file}: ${messages.length} messages`);
}
