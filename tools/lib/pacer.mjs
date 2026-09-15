// Shared Pacer SysEx + MIDI helpers for the Node tools.

import { createRequire } from 'node:module';

export const HEADER = [0xF0, 0x00, 0x01, 0x77, 0x7F];
export const CMD_SET = 0x01;
export const CMD_GET = 0x02;
export const TGT_PRESET = 0x01;
export const TGT_GLOBAL = 0x05;
export const TGT_BACKUP = 0x7F;
export const OBJ_NAME = 0x01;
export const OBJ_MIDI = 0x7E;
export const OBJ_ALL = 0x7F;

/** Stomp switches SW 1-6, SW A-D in index order. */
export const SWITCH_OBJECTS = [0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x14, 0x15, 0x16, 0x17];
export const FOOTSWITCH_OBJECTS = [0x18, 0x19, 0x1A, 0x1B];
export const EXPRESSION_OBJECTS = [0x36, 0x37];

export const MSG_CC = 0x00;             // expression pedal CC
export const MSG_CC_TRIGGER = 0x40;     // switch: data1 = CC, data2 = down value, data3 = up value
export const MSG_LOAD_CC = 0x65;        // preset MIDI "on load" CC
export const MSG_OFF = 0x61;

export const checksum = bytes => (128 - (bytes.reduce ((a, b) => a + b, 0) % 128)) % 128;

/** Wrap a body (cmd .. last data byte) into a complete SysEx message. */
export const message = body => Uint8Array.from ([...HEADER, ...body, checksum (body), 0xF7]);

export const hex = bytes => [...bytes].map (b => b.toString (16).padStart (2, '0').toUpperCase ()).join (' ');

export function presetIndex (name)
{
    const m = /^([A-D])([1-6])$/i.exec (name);
    if (!m)
        throw new Error (`Invalid preset "${name}" (expected A1..D6)`);
    return (m[1].toUpperCase ().charCodeAt (0) - 65) * 6 + Number (m[2]);
}

export function presetName (index)
{
    return index === 0 ? 'CUR' : String.fromCharCode (65 + Math.floor ((index - 1) / 6)) + ((index - 1) % 6 + 1);
}

/** Split concatenated SysEx bytes into messages (each including F0 .. F7). */
export function splitSysex (bytes)
{
    const messages = [];
    let i = 0;
    while ((i = bytes.indexOf (0xF0, i)) >= 0)
    {
        const end = bytes.indexOf (0xF7, i);
        if (end < 0)
            break;
        messages.push (bytes.subarray (i, end + 1));
        i = end + 1;
    }
    return messages;
}

/** True if the message is a Nektar Pacer message with a valid checksum. */
export function isValidPacerMessage (m)
{
    if (m.length < 9 || HEADER.some ((b, i) => m[i] !== b) || m[m.length - 1] !== 0xF7)
        return false;
    return checksum (Array.from (m.subarray (5, m.length - 2))) === m[m.length - 2];
}

// ---- MIDI ports (USB port 1 only; port 2 is Nektar's DAW integration) -------------------------------------------

export function openMidi ()
{
    return createRequire (import.meta.url) ('@julusian/midi');
}

export function findPacerPort (io)
{
    for (let i = 0; i < io.getPortCount (); i++)
    {
        const name = io.getPortName (i);
        if (/pacer/i.test (name) && !/MIDI(IN|OUT)2/i.test (name))
            return i;
    }
    return -1;
}

/**
 * Send a GET request and collect Pacer SysEx replies until the line is quiet.
 *
 * @returns {Promise<Uint8Array[]>}
 */
export async function requestFromPacer (midi, requestBody, { quietMs = 1500, timeoutMs = 30000 } = {})
{
    if (requestBody[0] !== CMD_GET)
        throw new Error ('requestFromPacer only sends GET requests');

    const input = new midi.Input ();
    const output = new midi.Output ();
    const inPort = findPacerPort (input);
    const outPort = findPacerPort (output);
    if (inPort < 0 || outPort < 0)
        throw new Error ('Pacer port 1 not found');

    const messages = [];
    let lastMessageAt = 0;
    input.ignoreTypes (false, true, true);
    input.on ('message', (_delta, m) => {
        if (m[0] === 0xF0 && m[1] === 0x00 && m[2] === 0x01 && m[3] === 0x77)
        {
            messages.push (Uint8Array.from (m));
            lastMessageAt = Date.now ();
        }
    });
    input.openPort (inPort);
    output.openPort (outPort);

    const startedAt = Date.now ();
    output.sendMessage ([...message (requestBody)]);
    await new Promise (resolve => {
        const timer = setInterval (() => {
            const now = Date.now ();
            if ((lastMessageAt && now - lastMessageAt > quietMs) || now - startedAt > timeoutMs)
            {
                clearInterval (timer);
                resolve ();
            }
        }, 100);
    });

    input.closePort ();
    output.closePort ();
    return messages;
}

export function concat (messages)
{
    const bytes = new Uint8Array (messages.reduce ((n, m) => n + m.length, 0));
    let offset = 0;
    for (const m of messages)
    {
        bytes.set (m, offset);
        offset += m.length;
    }
    return bytes;
}

export const timestamp = () => new Date ().toISOString ().replace (/[:.]/g, '-').slice (0, 19);
