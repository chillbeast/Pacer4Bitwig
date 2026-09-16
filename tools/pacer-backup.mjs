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

/** A preset is 189 SysEx messages; a full dump is CUR + A1..D6 plus the global settings (docs/PACER-MAP.md). */
const MESSAGES_PER_PRESET = 189;
const PRESET_COUNT = 25;
const GLOBAL_MESSAGES = 37;

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

// A dump can come back short - the Pacer simply stops answering - and every message that did arrive has a valid
// checksum, so nothing else gives it away. A backup you cannot trust is worse than no backup, so say so loudly and
// put it in the name of the file.
const expected = presetArg ? MESSAGES_PER_PRESET : MESSAGES_PER_PRESET * PRESET_COUNT + GLOBAL_MESSAGES;
const missing = [];
if (!presetArg)
    for (let i = 0; i < PRESET_COUNT; i++)
        if (!names.has (i))
            missing.push (presetName (i));
const complete = messages.length === expected && missing.length === 0 && badChecksums === 0;

const root = join (dirname (fileURLToPath (import.meta.url)), '..', 'backups');
mkdirSync (root, { recursive: true });
const suffix = complete ? '' : '-INCOMPLETE';
const file = join (root, `pacer-${presetArg ? presetArg.toUpperCase () : 'full'}-${timestamp ()}${suffix}.syx`);
const bytes = concat (messages);
writeFileSync (file, bytes);

console.log (`${messages.length} messages, ${bytes.length} bytes, ${badChecksums} bad checksums -> ${file}`);
console.log ([...names.entries ()].sort ((a, b) => a[0] - b[0]).map (([i, n]) => `${presetName (i)}="${n}"`).join ('  '));

if (!complete)
{
    console.error ('');
    console.error (`INCOMPLETE BACKUP: expected ${expected} messages, got ${messages.length}.`);
    if (missing.length > 0)
        console.error (`Missing presets: ${missing.join (', ')}.`);
    console.error ('Do NOT rely on this file as your undo. Run the backup again - a short dump is usually a one-off.');
    process.exit (2);
}
