// Generates the Bitwig Pacer presets described in docs/PACER-MAP.md as .syx files: the looper preset and the FX preset
// (docs/FX-PRESET.md). Does not talk to the Pacer - use pacer-send.mjs (or Pacer Studio) to write the result.
//
//   node looper-preset.mjs                                  every preset (looper D1, FX D2), both variants, channel 16 -> ../presets/
//   node looper-preset.mjs --preset fx --slot C6 --mode multi --channel 5

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
    CMD_SET, EXPRESSION_OBJECTS, FOOTSWITCH_OBJECTS, MSG_CC, MSG_CC_TRIGGER, MSG_LOAD_CC, MSG_OFF, OBJ_MIDI, OBJ_NAME,
    SWITCH_OBJECTS, TGT_PRESET, concat, isValidPacerMessage, message, presetIndex
} from './lib/pacer.mjs';

// ---- The contract (docs/PACER-MAP.md) ---------------------------------------------------------------------------

export const DEFAULT_CHANNEL = 16;
export const SWITCH_CC_BASE = 102;
export const FOOTSWITCH_CC_BASE = 112;
export const EXP_CC_BASE = 116;
export const PRESET_LOADED_CC = 119;
export const COLOUR_SLOT_CC_BASE = 20;

export const PRESET_KINDS = ['looper', 'fx'];
export const DEFAULT_SLOT = { looper: 'D1', fx: 'D2' };
export const PRESET_NAME = { looper: 'LOOPS', fx: 'FX' };
/**
 * The preset-loaded CC value tells the extension which preset and LED variant is loaded: kind * 16 + variant
 * (variant 1 two-colour, 2 multi-colour); 127 is the legacy looper two-colour value.
 */
export const PRESET_LOADED_VALUE = { looper: { two: 127, multi: 2 }, fx: { two: 17, multi: 18 } };

const COLOUR = { OFF: 0x00, RED: 0x03, AMBER: 0x07, GREEN: 0x0D, BLUE: 0x11, PURPLE: 0x15, WHITE: 0x17 };
/** Multi-colour: on colour of steps 1..6. */
const SLOT_COLOURS = [COLOUR.WHITE, COLOUR.RED, COLOUR.GREEN, COLOUR.AMBER, COLOUR.BLUE, COLOUR.PURPLE];
const LOOP_SWITCHES = 4;
const FX_SWITCHES = 6;

/** Two-colour: on colour of step 1 - loop switches red (looper), FX switches green (FX), the rest white. */
const twoColourOn = (kind, switchIndex) => {
    if (kind === 'fx')
        return switchIndex < FX_SWITCHES ? COLOUR.GREEN : COLOUR.WHITE;
    return switchIndex < LOOP_SWITCHES ? COLOUR.RED : COLOUR.WHITE;
};

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

// Always 5 characters, space padded - like every name in the factory presets and the device dumps ("MMC  ")
const name = (idx, text) => {
    const chars = text.padEnd (5).slice (0, 5);
    return message ([CMD_SET, TGT_PRESET, idx, OBJ_NAME, 0x01, chars.length, ...[...chars].map (c => c.charCodeAt (0))]);
};

export function buildPreset (kind, slot, mode, channel = DEFAULT_CHANNEL)
{
    if (!PRESET_KINDS.includes (kind))
        throw new Error (`Invalid preset "${kind}" (expected ${PRESET_KINDS.join (' or ')})`);
    if (!Number.isInteger (channel) || channel < 1 || channel > 16)
        throw new Error (`Invalid MIDI channel ${channel} (expected 1..16)`);

    const idx = presetIndex (slot);
    const multi = mode === 'multi';
    const out = [name (idx, PRESET_NAME[kind])];

    SWITCH_OBJECTS.forEach ((obj, s) => {
        out.push (controlMode (idx, obj));
        out.push (step (idx, obj, 1, { channel, type: MSG_CC_TRIGGER, data: [SWITCH_CC_BASE + s, 127, 0], active: 1 }));
        for (let n = 2; n <= 6; n++)
            out.push (multi
                ? step (idx, obj, n, { channel, type: MSG_CC_TRIGGER, data: [colourSlotCC (s, n), 127, 0], active: 1 })
                : step (idx, obj, n, { data: [0, 127, 0] }));

        const onColour = multi ? SLOT_COLOURS[0] : twoColourOn (kind, s);
        out.push (led (idx, obj, 1, { midi: 1, on: onColour }));
        for (let n = 2; n <= 6; n++)
            out.push (multi ? led (idx, obj, n, { midi: 1, on: SLOT_COLOURS[n - 1] }) : led (idx, obj, n, {}));
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

    out.push (midiSetting (idx, 1, { channel, type: MSG_LOAD_CC, data: [PRESET_LOADED_CC, PRESET_LOADED_VALUE[kind][multi ? 'multi' : 'two'], 0] }));
    for (let n = 2; n <= 16; n++)
        out.push (midiSetting (idx, n, {}));

    return out;
}

export const buildLooperPreset = (slot, mode, channel) => buildPreset ('looper', slot, mode, channel);
export const buildFxPreset = (slot, mode, channel) => buildPreset ('fx', slot, mode, channel);

// ---- CLI --------------------------------------------------------------------------------------------------------

if (process.argv[1] && fileURLToPath (import.meta.url) === process.argv[1])
{
    const args = process.argv.slice (2);
    const option = (flag, fallback) => (args.includes (flag) ? args[args.indexOf (flag) + 1] : fallback);
    const presetArg = option ('--preset', 'all');
    const slotArg = option ('--slot', null);
    const modeArg = option ('--mode', 'both');
    const channel = Number (option ('--channel', String (DEFAULT_CHANNEL)));
    const kinds = presetArg === 'all' ? PRESET_KINDS : [presetArg];
    const modes = modeArg === 'both' ? ['two', 'multi'] : [modeArg];

    if (slotArg && kinds.length > 1)
        throw new Error ('--slot needs --preset looper or --preset fx (the presets default to different slots)');

    const root = join (dirname (fileURLToPath (import.meta.url)), '..', 'presets');
    mkdirSync (root, { recursive: true });

    for (const kind of kinds)
    {
        const slot = (slotArg ?? DEFAULT_SLOT[kind] ?? '').toUpperCase ();
        if (slot === 'D6')
            throw new Error ('D6 cannot be read back by the Pacer - pick another slot');

        for (const mode of modes)
        {
            const messages = buildPreset (kind, slot, mode, channel);
            const invalid = messages.filter (m => !isValidPacerMessage (m)).length;
            if (messages.length !== 189 || invalid > 0)
                throw new Error (`Self-check failed: ${messages.length} messages (expected 189), ${invalid} invalid`);
            const suffix = channel === DEFAULT_CHANNEL ? '' : `-ch${channel}`;
            const file = join (root, `bitwig-${kind}-${mode}-colour-${slot}${suffix}.syx`);
            writeFileSync (file, concat (messages));
            console.log (`${file}: ${messages.length} messages`);
        }
    }
}
