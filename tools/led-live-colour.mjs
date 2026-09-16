// Tests whether a SysEx write to preset index 0 ("current", the slot a full backup reports as CUR) changes the loaded
// preset live - the mechanism a "modes" feature would need to recolour or reassign switches on the fly.
//
//   node led-live-colour.mjs --switch 1 --colour 0x0B          green, dimmed variant as the off colour
//   node led-live-colour.mjs --switch 1 --colour 0x03 --slot D3   write to the stored preset instead
//
// Colours (Pacer user guide): 1 magenta 0x01, 2 red 0x03, 3 orange 0x05, 4 gold 0x07, 5 yellow 0x09, 6 green 0x0B,
// 7 dark green 0x0D, 8 cyan 0x0F, 9 blue 0x11, 10 lavender 0x13, 11 purple 0x15, 12 white 0x17; +1 = dimmed.

import { CMD_SET, SWITCH_OBJECTS, TGT_PRESET, findPacerPort, message, openMidi, presetIndex } from './lib/pacer.mjs';

const args = process.argv.slice (2);
const option = (name, fallback) => {
    const index = args.indexOf (name);
    return index >= 0 && args[index + 1] !== undefined ? args[index + 1] : fallback;
};
const number = (value, fallback) => {
    const parsed = String (value).toLowerCase ().startsWith ('0x') ? parseInt (value, 16) : parseInt (value, 10);
    return Number.isNaN (parsed) ? fallback : parsed;
};

const switchNumber = number (option ('--switch', 1), 1);
const colour = number (option ('--colour', 0x0B), 0x0B);
const slot = option ('--slot', null);
const index = slot ? presetIndex (slot.toUpperCase ()) : 0;
const object = SWITCH_OBJECTS[switchNumber - 1];

const midi = openMidi ();
const output = new midi.Output ();
const port = findPacerPort (output);
if (port < 0)
{
    console.error ('No Pacer output port found.');
    process.exit (1);
}
output.openPort (port);

// The four LED elements of step 1: MIDI ctrl, on colour, off colour, LED number
output.sendMessage (Array.from (message ([CMD_SET, TGT_PRESET, index, object,
    0x41, 1, colour, 0x00,
    0x42, 1, colour + 1])));
output.closePort ();

console.log (`SW ${switchNumber}: on 0x${colour.toString (16)}, off 0x${(colour + 1).toString (16)}, written to preset index ${index}${index === 0 ? ' (current)' : ' (' + slot.toUpperCase () + ')'}.`);
