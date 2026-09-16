// Experiments with the Pacer's live LED colours: the message Nektar's DAW mode uses to give each switch an off and an
// on colour (F0 00 01 77 7F 01 06 18 [00 slot 02 off on] x10 cs F7, see docs/ROADMAP.md). Target 0x06 looks like a
// display/RAM target, so this should not touch the presets - nothing here writes a preset.
//
//   node led-colour-lab.mjs --sweep                 sweep colours 0-31 on SW 1
//   node led-colour-lab.mjs --sweep --from 0 --to 15 --switch 2 --dwell 1500
//   node led-colour-lab.mjs --switch 1 --on 0x0D --off 0x03      one pair, held until you press Ctrl+C
//   node led-colour-lab.mjs --daw --sweep           send Nektar's "DAW connected" message first

import { findPacerPort, openMidi } from './lib/pacer.mjs';

const args = process.argv.slice (2);
const option = (name, fallback) => {
    const index = args.indexOf (name);
    return index >= 0 && args[index + 1] !== undefined ? args[index + 1] : fallback;
};
const number = (value, fallback) => {
    if (value === undefined)
        return fallback;
    const parsed = String (value).toLowerCase ().startsWith ('0x') ? parseInt (value, 16) : parseInt (value, 10);
    return Number.isNaN (parsed) ? fallback : parsed;
};

const SWITCH_CC_BASE = 102;
const CHANNEL = number (option ('--channel', 16), 16) - 1;
const SLOT = number (option ('--switch', 1), 1);
const DWELL_MS = number (option ('--dwell', 1400), 1400);
const NUM_SLOTS = 10;

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

/** Nektar's slot-colour message: every slot gets an off and an on colour; its checksum is just cmd + target + index. */
function slotColours (offColours, onColours)
{
    const bytes = [0xF0, 0x00, 0x01, 0x77, 0x7F, 0x01, 0x06, 0x18];
    for (let slot = 1; slot <= NUM_SLOTS; slot++)
        bytes.push (0x00, slot, 0x02, offColours[slot - 1] & 0x7F, onColours[slot - 1] & 0x7F);
    bytes.push ((0x01 + 0x06 + 0x18) % 128, 0xF7);
    return bytes;
}

function setColours (slot, off, on)
{
    const offColours = new Array (NUM_SLOTS).fill (0x00);
    const onColours = new Array (NUM_SLOTS).fill (0x00);
    offColours[slot - 1] = off;
    onColours[slot - 1] = on;
    output.sendMessage (slotColours (offColours, onColours));
}

function light (slot, on)
{
    output.sendMessage ([0xB0 | CHANNEL & 0x0F, SWITCH_CC_BASE + slot - 1, on ? 127 : 0]);
}

const hex = value => '0x' + value.toString (16).padStart (2, '0');

if (args.includes ('--daw'))
{
    output.sendMessage ([0xF0, 0x00, 0x01, 0x77, 0x7F, 0x01, 0x09, 0x01, 0x00, 0x00, 0x01, 0x3E, 0x36, 0xF7]);
    console.log ('Sent the "DAW connected" message.');
    await sleep (300);
}

const close = () => {
    output.closePort ();
    process.exit (0);
};
process.on ('SIGINT', close);

if (args.includes ('--sweep'))
{
    const from = number (option ('--from', 0), 0);
    const to = number (option ('--to', 31), 31);
    console.log (`Sweeping colours ${hex (from)}-${hex (to)} on SW ${SLOT}, ${DWELL_MS} ms each. Watch the switch.`);
    for (let colour = from; colour <= to; colour++)
    {
        setColours (SLOT, 0x00, colour);
        light (SLOT, true);
        console.log (`  on colour ${hex (colour)} (${colour})`);
        await sleep (DWELL_MS);
    }
    light (SLOT, false);
    console.log ('Done. Select a preset on the Pacer to put its own colours back.');
    close ();
}

const on = number (option ('--on', 0x17), 0x17);
const off = number (option ('--off', 0x00), 0x00);
setColours (SLOT, off, on);
console.log (`SW ${SLOT}: on ${hex (on)}, off ${hex (off)}. Blinking until you press Ctrl+C.`);
for (;;)
{
    light (SLOT, true);
    await sleep (DWELL_MS);
    light (SLOT, false);
    await sleep (DWELL_MS);
}
