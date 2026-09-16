// Writes the preset NAME live, to preset index 0 ("current"), i.e. into the loaded preset's RAM copy only.
// Selecting any preset on the Pacer restores the stored name, so this never touches EEPROM.
//
//   node live-name.mjs MODE1           write the name, then read it back
//   node live-name.mjs "AB" --no-pad   write a 2-character name (no space padding)
//   node live-name.mjs --read          read the current name, write nothing
//
// The question this answers: does the Pacer's display follow a live name write? Watch the device.

import {
    CMD_GET, CMD_SET, OBJ_ALL, OBJ_NAME, TGT_PRESET, findPacerPort, hex, message, openMidi, requestFromPacer
} from './lib/pacer.mjs';

const args = process.argv.slice (2);
const readOnly = args.includes ('--read');
const pad = !args.includes ('--no-pad');
const text = args.find (a => !a.startsWith ('--'));

const midi = openMidi ();

async function readName ()
{
    const replies = await requestFromPacer (midi, [CMD_GET, TGT_PRESET, 0x00, OBJ_ALL], { quietMs: 800, timeoutMs: 8000 });
    const m = replies.find (r => r[8] === OBJ_NAME);
    if (!m)
        return null;
    return { text: String.fromCharCode (...m.slice (11, 11 + m[10])), raw: hex (m) };
}

if (readOnly || text === undefined)
{
    const name = await readName ();
    console.log (name ? `current name: "${name.text}"\n  ${name.raw}` : 'no name in the reply');
    process.exit (0);
}

const chars = [...(pad ? text.padEnd (5).slice (0, 5) : text)].map (c => c.charCodeAt (0));
if (chars.some (c => c > 0x7F))
{
    console.error ('Name must be 7-bit ASCII.');
    process.exit (1);
}

const output = new midi.Output ();
const port = findPacerPort (output);
if (port < 0)
{
    console.error ('No Pacer output port found.');
    process.exit (1);
}
const sysex = message ([CMD_SET, TGT_PRESET, 0x00, OBJ_NAME, 0x01, chars.length, ...chars]);
output.openPort (port);
output.sendMessage (Array.from (sysex));
output.closePort ();

// The Pacer needs a moment before a GET reflects the write: reading back at once returns the previous name.
await new Promise (r => setTimeout (r, 250));

console.log (`sent: ${hex (sysex)}`);
console.log (`wrote "${String.fromCharCode (...chars)}" (${chars.length} chars) to preset index 0 (current, RAM only)`);

const after = await readName ();
console.log (after ? `read back: "${after.text}"\n  ${after.raw}` : 'read back: no reply');
