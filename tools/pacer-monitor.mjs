// Read-only MIDI monitor for the Pacer: listens on every Pacer input port (port 1 presets, port 2 DAW mode) and
// prints what the Pacer sends, decoded. Sends nothing. Useful while running the hardware checklist.
//
//   node pacer-monitor.mjs                  print to the console
//   node pacer-monitor.mjs --log out.log    also append to a file

import { appendFileSync } from 'node:fs';

import { hex, openMidi } from './lib/pacer.mjs';

const midi = openMidi ();
const args = process.argv.slice (2);
const logFile = args.includes ('--log') ? args[args.indexOf ('--log') + 1] : null;

const NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];

function describe (m)
{
    const status = m[0];
    if (status === 0xF0)
    {
        if (m[1] === 0x00 && m[2] === 0x01 && m[3] === 0x77)
            return `SysEx Nektar cmd=${m[5]?.toString (16)} tgt=${m[6]?.toString (16)} idx=${m[7]?.toString (16)} obj=${m[8]?.toString (16)} (${m.length} bytes)`;
        if (m[1] === 0x7E)
            return `SysEx universal non-realtime (${m.length} bytes)`;
        return `SysEx (${m.length} bytes)`;
    }
    const channel = (status & 0x0F) + 1;
    switch (status & 0xF0)
    {
        case 0x80: return `ch${channel} note off ${NOTE_NAMES[m[1] % 12]}${Math.floor (m[1] / 12) - 1} vel ${m[2]}`;
        case 0x90: return `ch${channel} note on  ${NOTE_NAMES[m[1] % 12]}${Math.floor (m[1] / 12) - 1} vel ${m[2]}`;
        case 0xA0: return `ch${channel} poly pressure ${m[1]} ${m[2]}`;
        case 0xB0: return `ch${channel} CC ${m[1]} = ${m[2]}`;
        case 0xC0: return `ch${channel} program ${m[1]}`;
        case 0xD0: return `ch${channel} channel pressure ${m[1]}`;
        case 0xE0: return `ch${channel} pitch bend ${(m[2] << 7 | m[1]) - 8192}`;
        default: return `system ${status.toString (16)}`;
    }
}

function write (line)
{
    console.log (line);
    if (logFile)
        appendFileSync (logFile, line + '\n');
}

const probe = new midi.Input ();
const ports = [];
for (let i = 0; i < probe.getPortCount (); i++)
    if (/pacer/i.test (probe.getPortName (i)))
        ports.push ({ index: i, name: probe.getPortName (i) });

if (ports.length === 0)
{
    console.error ('No Pacer input port found.');
    process.exit (1);
}

const inputs = ports.map (({ index, name }) => {
    const input = new midi.Input ();
    // Keep SysEx, drop MIDI clock and active sensing
    input.ignoreTypes (false, true, true);
    input.on ('message', (_delta, message) => {
        const time = new Date ().toISOString ().slice (11, 23);
        write (`${time}  ${name.padEnd (16)}  ${describe (message).padEnd (48)}  ${hex (message.slice (0, 24))}${message.length > 24 ? ' …' : ''}`);
    });
    input.openPort (index);
    return input;
});

write (`${new Date ().toISOString ()}  listening on: ${ports.map (p => p.name).join (', ')}`);

const close = () => {
    inputs.forEach (input => input.closePort ());
    process.exit (0);
};
process.on ('SIGINT', close);
process.on ('SIGTERM', close);
