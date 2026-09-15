// Generates the "Bitwig Looper" Pacer preset described in docs/PACER-MAP.md as .syx files.
// Does not talk to the Pacer - use pacer-send.mjs (or Pacer Studio) to write the result.
//
//   node looper-preset.mjs                       both variants for D1 -> ../presets/
//   node looper-preset.mjs --slot C6 --mode multi

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
    CMD_SET, EXPRESSION_OBJECTS, FOOTSWITCH_OBJECTS, MSG_CC, MSG_CC_TRIGGER, MSG_LOAD_CC, MSG_OFF, OBJ_MIDI, OBJ_NAME,
    SWITCH_OBJECTS, TGT_PRESET, concat, isValidPacerMessage, message, presetIndex
} from './lib/pacer.mjs';

// ---- The contract (docs/PACER-MAP.md) ---------------------------------------------------------------------------

export const CHANNEL = 16;
export const SWITCH_CC_BASE = 102;
export const FOOTSWITCH_CC_BASE = 112;
export const EXP_CC_BASE = 116;
export const PRESET_LOADED_CC = 119;
export const COLOUR_SLOT_CC_BASE = 20;
export const PRESET_NAME = 'LOOPS';

const COLOUR = { OFF: 0x00, RED: 0x03, AMBER: 0x07, GREEN: 0x0D, BLUE: 0x11, PURPLE: 0x15, WHITE: 0x17 };
/** Multi-colour: on colour of steps 1..6. */
const SLOT_COLOURS = [COLOUR.WHITE, COLOUR.RED, COLOUR.GREEN, COLOUR.AMBER, COLOUR.BLUE, COLOUR.PURPLE];
const LOOP_SWITCHES = 4;

export const colourSlotCC = (switchIndex, step) => COLOUR_SLOT_CC_BASE + switchIndex * 5 + step - 2;

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

const name = (idx, text) => {
    const chars = text.padEnd (5).slice (0, 5);
    return message ([CMD_SET, TGT_PRESET, idx, OBJ_NAME, 0x01, chars.length, ...[...chars].map (c => c.charCodeAt (0))]);
};

export function buildLooperPreset (slot, mode)
{
    const idx = presetIndex (slot);
    const multi = mode === 'multi';
    const out = [name (idx, PRESET_NAME)];

    SWITCH_OBJECTS.forEach ((obj, s) => {
        out.push (controlMode (idx, obj));
        out.push (step (idx, obj, 1, { channel: CHANNEL, type: MSG_CC_TRIGGER, data: [SWITCH_CC_BASE + s, 127, 0], active: 1 }));
        for (let n = 2; n <= 6; n++)
            out.push (multi
                ? step (idx, obj, n, { channel: CHANNEL, type: MSG_CC_TRIGGER, data: [colourSlotCC (s, n), 127, 0], active: 1 })
                : step (idx, obj, n, { data: [0, 127, 0] }));

        const onColour = multi ? SLOT_COLOURS[0] : (s < LOOP_SWITCHES ? COLOUR.RED : COLOUR.WHITE);
        out.push (led (idx, obj, 1, { midi: 1, on: onColour }));
        for (let n = 2; n <= 6; n++)
            out.push (multi ? led (idx, obj, n, { midi: 1, on: SLOT_COLOURS[n - 1] }) : led (idx, obj, n, {}));
    });

    FOOTSWITCH_OBJECTS.forEach ((obj, f) => {
        out.push (controlMode (idx, obj));
        out.push (step (idx, obj, 1, { channel: CHANNEL, type: MSG_CC_TRIGGER, data: [FOOTSWITCH_CC_BASE + f, 127, 0], active: 1 }));
        for (let n = 2; n <= 6; n++)
            out.push (step (idx, obj, n, {}));
    });

    EXPRESSION_OBJECTS.forEach ((obj, e) => {
        out.push (controlMode (idx, obj));
        out.push (step (idx, obj, 1, { channel: CHANNEL, type: MSG_CC, data: [EXP_CC_BASE + e, 0, 127], active: 1 }));
        // Same shape as the factory's unused pedal steps
        for (let n = 2; n <= 6; n++)
            out.push (step (idx, obj, n, { type: MSG_CC, data: [0, 0, 127] }));
    });

    out.push (midiSetting (idx, 1, { channel: CHANNEL, type: MSG_LOAD_CC, data: [PRESET_LOADED_CC, 127, 0] }));
    for (let n = 2; n <= 16; n++)
        out.push (midiSetting (idx, n, {}));

    return out;
}

// ---- CLI --------------------------------------------------------------------------------------------------------

if (process.argv[1] && fileURLToPath (import.meta.url) === process.argv[1])
{
    const args = process.argv.slice (2);
    const slot = (args.includes ('--slot') ? args[args.indexOf ('--slot') + 1] : 'D1').toUpperCase ();
    const modeArg = args.includes ('--mode') ? args[args.indexOf ('--mode') + 1] : 'both';
    const modes = modeArg === 'both' ? ['two', 'multi'] : [modeArg];

    if (slot === 'D6')
        throw new Error ('D6 cannot be read back by the Pacer - pick another slot');

    const root = join (dirname (fileURLToPath (import.meta.url)), '..', 'presets');
    mkdirSync (root, { recursive: true });

    for (const mode of modes)
    {
        const messages = buildLooperPreset (slot, mode);
        const invalid = messages.filter (m => !isValidPacerMessage (m)).length;
        if (messages.length !== 189 || invalid > 0)
            throw new Error (`Self-check failed: ${messages.length} messages (expected 189), ${invalid} invalid`);
        const file = join (root, `bitwig-looper-${mode}-colour-${slot}.syx`);
        writeFileSync (file, concat (messages));
        console.log (`${file}: ${messages.length} messages`);
    }
}
