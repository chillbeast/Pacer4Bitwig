// Writes a colour chart to one preset slot: each of the ten stomp switches gets a different colour from the manual's
// table (1A..10A full, with the matching 1b..10b dimmed as the off colour), so the colours can simply be read off the
// hardware. Only the LED settings of the ten switches are written - steps, jacks and pedals stay as they are.
//
//   node led-colour-chart.mjs --slot D3
//   node pacer-send.mjs ../presets/led-colour-chart-D3.syx --confirm D3

import { writeFileSync } from 'node:fs';

import { CMD_SET, SWITCH_OBJECTS, TGT_PRESET, concat, message, presetIndex } from './lib/pacer.mjs';

const args = process.argv.slice (2);
const option = (name, fallback) => {
    const index = args.indexOf (name);
    return index >= 0 && args[index + 1] !== undefined ? args[index + 1] : fallback;
};

const SLOT = option ('--slot', 'D3').toUpperCase ();
const SWITCH_NAMES = ['SW 1', 'SW 2', 'SW 3', 'SW 4', 'SW 5', 'SW 6', 'SW A', 'SW B', 'SW C', 'SW D'];

const idx = presetIndex (SLOT);
const messages = [];
const chart = [];

SWITCH_OBJECTS.forEach ((object, i) => {
    // Colour numbers 1A..10A are the odd byte values 0x01, 0x03, ...; the next even value is the dimmed variant
    const bright = 1 + i * 2;
    const dim = bright + 1;
    chart.push (`${SWITCH_NAMES[i]}: colour ${i + 1}A (0x${bright.toString (16).padStart (2, '0')}), dim ${i + 1}b`);
    // LED settings of step 1: MIDI ctrl off, so the Pacer shows the colours by itself
    messages.push (message ([CMD_SET, TGT_PRESET, idx, object,
        0x40, 1, 0, 0x00,
        0x41, 1, bright, 0x00,
        0x42, 1, dim, 0x00,
        0x43, 1, 0]));
});

const file = new URL ('../presets/led-colour-chart-' + SLOT + '.syx', import.meta.url);
writeFileSync (file, concat (messages));
console.log (chart.join ('\n'));
console.log (`\n${messages.length} messages -> ${file.pathname.slice (1)}`);
