// Tests the manual's "up to 3 LEDs per switch" (LED Number 0-3: 0 default, 1 bottom, 2 middle, 3 top) by giving SW 3
// three steps, each driving a different LED in a different colour. Writes ONLY SW 3 of one preset slot.
//
//   node led-layout-test.mjs --slot D3            write the test configuration (backs the slot up first)
//   node led-layout-test.mjs --light              light the three LEDs one at a time, then together
//
// Colours are the manual's numbering: 1A=0x01 .. 12A=0x17, with the following even value the dimmed variant.

import { writeFileSync } from 'node:fs';

import { CMD_SET, MSG_CC_TRIGGER, TGT_PRESET, concat, findPacerPort, message, openMidi, presetIndex } from './lib/pacer.mjs';

const args = process.argv.slice (2);
const option = (name, fallback) => {
    const index = args.indexOf (name);
    return index >= 0 && args[index + 1] !== undefined ? args[index + 1] : fallback;
};

const SLOT = option ('--slot', 'D3').toUpperCase ();
const CHANNEL = 16;
const SW3_OBJECT = 0x0F;
/** Step 1 keeps the looper's CC for SW 3; steps 2 and 3 use its colour-slot CCs (docs/PACER-MAP.md). */
const STEPS = [
    { cc: 104, ledNumber: 1, colour: 0x03, name: 'bottom, colour 2A (0x03)' },
    { cc: 30, ledNumber: 2, colour: 0x0D, name: 'middle, colour 7A (0x0D)' },
    { cc: 31, ledNumber: 3, colour: 0x11, name: 'top, colour 9A (0x11)' }
];

const step = (idx, n, { cc, active = 1 }) => {
    const e = (n - 1) * 6;
    return message ([CMD_SET, TGT_PRESET, idx, SW3_OBJECT,
        e + 1, 1, CHANNEL, 0x00,
        e + 2, 1, MSG_CC_TRIGGER, 0x00,
        e + 3, 1, cc, 0x00,
        e + 4, 1, 127, 0x00,
        e + 5, 1, 0, 0x00,
        e + 6, 1, active]);
};

const led = (idx, n, { colour, ledNumber }) => {
    const e = 0x40 + (n - 1) * 4;
    return message ([CMD_SET, TGT_PRESET, idx, SW3_OBJECT,
        e, 1, 1, 0x00,
        e + 1, 1, colour, 0x00,
        e + 2, 1, 0x00, 0x00,
        e + 3, 1, ledNumber]);
};

if (args.includes ('--light'))
{
    const midi = openMidi ();
    const output = new midi.Output ();
    const port = findPacerPort (output);
    if (port < 0)
    {
        console.error ('No Pacer output port found.');
        process.exit (1);
    }
    output.openPort (port);
    const sleep = ms => new Promise (resolve => setTimeout (resolve, ms));
    const send = (cc, value) => output.sendMessage ([0xB0 | (CHANNEL - 1) & 0x0F, cc, value]);

    for (const { cc, name } of STEPS)
    {
        console.log ('lighting ' + name);
        send (cc, 127);
        await sleep (2500);
        send (cc, 0);
        await sleep (400);
    }
    console.log ('all three together');
    for (const { cc } of STEPS)
        send (cc, 127);
    await sleep (4000);
    for (const { cc } of STEPS)
        send (cc, 0);
    output.closePort ();
    process.exit (0);
}

const idx = presetIndex (SLOT);
const messages = [message ([CMD_SET, TGT_PRESET, idx, SW3_OBJECT, 0x60, 0x01, 0x00])];
STEPS.forEach ((config, i) => {
    messages.push (step (idx, i + 1, config));
    messages.push (led (idx, i + 1, config));
});

const file = new URL ('../presets/led-layout-test-' + SLOT + '.syx', import.meta.url);
writeFileSync (file, concat (messages));
console.log (`${messages.length} messages for SW 3 of ${SLOT} -> ${file.pathname.slice (1)}`);
console.log ('Write it with:  node pacer-send.mjs ../presets/led-layout-test-' + SLOT + '.syx --confirm ' + SLOT);
