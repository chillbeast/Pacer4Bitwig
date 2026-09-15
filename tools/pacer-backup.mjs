// Read-only Pacer backup: requests a full dump (or one preset) over USB port 1 and writes it to a .syx file.
// Only GET (cmd 0x02) requests are ever sent.
//
//   node pacer-backup.mjs --list
//   node pacer-backup.mjs                 full backup -> ../backups/pacer-full-<timestamp>.syx
//   node pacer-backup.mjs --preset D1     one preset  -> ../backups/pacer-D1-<timestamp>.syx

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
    CMD_GET, OBJ_ALL, OBJ_NAME, TGT_BACKUP, TGT_PRESET,
    concat, isValidPacerMessage, openMidi, presetIndex, presetName, requestFromPacer, timestamp
} from './lib/pacer.mjs';

const midi = openMidi ();
const args = process.argv.slice (2);

if (args.includes ('--list'))
{
    const input = new midi.Input ();
    const output = new midi.Output ();
    for (let i = 0; i < input.getPortCount (); i++)
        console.log (`in  ${i}: ${input.getPortName (i)}`);
    for (let i = 0; i < output.getPortCount (); i++)
        console.log (`out ${i}: ${output.getPortName (i)}`);
    process.exit (0);
}

const presetArg = args.includes ('--preset') ? args[args.indexOf ('--preset') + 1] : null;
const body = presetArg ? [CMD_GET, TGT_PRESET, presetIndex (presetArg), OBJ_ALL] : [CMD_GET, TGT_BACKUP];

console.log (`Requesting ${presetArg ? 'preset ' + presetArg.toUpperCase () : 'full backup'}...`);
const messages = await requestFromPacer (midi, body);
if (messages.length === 0)
{
    console.error ('No reply from the Pacer.');
    process.exit (2);
}

const badChecksums = messages.filter (m => !isValidPacerMessage (m)).length;
const names = new Map ();
for (const m of messages)
    if (m[6] === TGT_PRESET && m[8] === OBJ_NAME)
        names.set (m[7], String.fromCharCode (...m.slice (11, 11 + m[10])).trimEnd ());

const root = join (dirname (fileURLToPath (import.meta.url)), '..', 'backups');
mkdirSync (root, { recursive: true });
const file = join (root, `pacer-${presetArg ? presetArg.toUpperCase () : 'full'}-${timestamp ()}.syx`);
const bytes = concat (messages);
writeFileSync (file, bytes);

console.log (`${messages.length} messages, ${bytes.length} bytes, ${badChecksums} bad checksums -> ${file}`);
console.log ([...names.entries ()].sort ((a, b) => a[0] - b[0]).map (([i, n]) => `${presetName (i)}="${n}"`).join ('  '));
