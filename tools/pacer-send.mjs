// Writes a single-preset .syx file to the Pacer (USB port 1). Guarded:
//  - every message must be a valid Pacer SET for ONE preset slot (no global settings, no full backups)
//  - the slot must be confirmed on the command line
//  - the slot's current contents are backed up to ../backups/ first
//
//   node pacer-send.mjs ../presets/bitwig-looper-two-colour-D1.syx --confirm D1

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
    CMD_GET, CMD_SET, OBJ_ALL, TGT_PRESET, concat, findPacerPort, isValidPacerMessage, openMidi, presetIndex,
    presetName, requestFromPacer, splitSysex, timestamp
} from './lib/pacer.mjs';

const DELAY_MS = 10;

const args = process.argv.slice (2);
const file = args.find (a => !a.startsWith ('--'));
const confirm = args.includes ('--confirm') ? args[args.indexOf ('--confirm') + 1] : null;
if (!file || !confirm)
{
    console.error ('Usage: node pacer-send.mjs <preset.syx> --confirm <slot, e.g. D1>');
    process.exit (1);
}

const messages = splitSysex (readFileSync (file));
const slots = new Set (messages.map (m => m[7]));
const problems = messages.filter (m => !isValidPacerMessage (m) || m[5] !== CMD_SET || m[6] !== TGT_PRESET);
if (messages.length === 0 || problems.length > 0 || slots.size !== 1)
{
    console.error (`Refusing: ${messages.length} messages, ${problems.length} are not valid preset SETs, ${slots.size} target slots.`);
    process.exit (1);
}

const idx = [...slots][0];
if (idx !== presetIndex (confirm))
{
    console.error (`Refusing: the file targets ${presetName (idx)}, but --confirm says ${confirm.toUpperCase ()}.`);
    process.exit (1);
}

const midi = openMidi ();

console.log (`Backing up ${presetName (idx)} first...`);
const backup = await requestFromPacer (midi, [CMD_GET, TGT_PRESET, idx, OBJ_ALL]);
if (backup.length === 0)
{
    console.error ('No reply to the backup request - not writing.');
    process.exit (2);
}
const root = join (dirname (fileURLToPath (import.meta.url)), '..', 'backups');
mkdirSync (root, { recursive: true });
const backupFile = join (root, `pacer-${presetName (idx)}-before-write-${timestamp ()}.syx`);
writeFileSync (backupFile, concat (backup));
console.log (`  ${backup.length} messages -> ${backupFile}`);

const output = new midi.Output ();
const port = findPacerPort (output);
output.openPort (port);
console.log (`Writing ${messages.length} messages to ${presetName (idx)} via "${output.getPortName (port)}"...`);
for (const m of messages)
{
    output.sendMessage ([...m]);
    await new Promise (resolve => setTimeout (resolve, DELAY_MS));
}
output.closePort ();
console.log ('Done. Select the preset on the Pacer to use it.');
