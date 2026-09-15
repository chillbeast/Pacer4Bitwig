import { describe, expect, it } from 'vitest';
import { MidiStreamParser, isPacerPrimaryPort, isPacerPort, pickPort } from '../src/midi';
import { hex } from './helpers';

describe('MidiStreamParser', () => {
  it('reassembles SysEx split across chunks', () => {
    const p = new MidiStreamParser();
    expect(p.push([0xf0, 0x00, 0x01])).toEqual([]);
    expect(p.push([0x77, 0x7f, 0x02])).toEqual([]);
    const out = p.push([0x7f, 0x7f, 0xf7]);
    expect(out.map(hex)).toEqual(['F0 00 01 77 7F 02 7F 7F F7']);
  });

  it('splits several messages delivered at once', () => {
    const p = new MidiStreamParser();
    const out = p.push([0xf0, 0x01, 0xf7, 0xbf, 102, 127, 0xf0, 0x02, 0xf7]);
    expect(out.map(hex)).toEqual(['F0 01 F7', 'BF 66 7F', 'F0 02 F7']);
  });

  it('passes realtime bytes interleaved in SysEx', () => {
    const p = new MidiStreamParser();
    const out = p.push([0xf0, 0x01, 0xf8, 0x02, 0xf7]);
    expect(out.map(hex)).toEqual(['F8', 'F0 01 02 F7']);
  });

  it('supports running status', () => {
    const p = new MidiStreamParser();
    const out = p.push([0xb0, 1, 2, 3, 4, 0xc5, 7, 8]);
    expect(out.map(hex)).toEqual(['B0 01 02', 'B0 03 04', 'C5 07', 'C5 08']);
  });

  it('drops a SysEx interrupted by a status byte', () => {
    const p = new MidiStreamParser();
    const out = p.push([0xf0, 0x01, 0x02, 0x90, 60, 100, 0x05, 0xf7]);
    expect(out.map(hex)).toEqual(['90 3C 64']);
    expect(p.droppedSysex).toBe(1);
  });
});

describe('Pacer port detection', () => {
  it('recognises port 1 but not the DAW port', () => {
    expect(isPacerPrimaryPort('PACER')).toBe(true);
    expect(isPacerPrimaryPort('MIDIIN2 (PACER)')).toBe(false);
    expect(isPacerPrimaryPort('MIDIOUT2 (PACER)')).toBe(false);
    expect(isPacerPrimaryPort('PACER MIDI1')).toBe(true);
    expect(isPacerPrimaryPort('PACER MIDI2')).toBe(false);
    expect(isPacerPrimaryPort('PACER Port 2')).toBe(false);
    expect(isPacerPort('MIDIIN2 (PACER)')).toBe(true);
    expect(isPacerPrimaryPort('Microsoft GS Wavetable Synth')).toBe(false);
  });

  it('prefers a remembered port name, else the Pacer port 1', () => {
    const ports = [
      { name: 'MIDIIN2 (PACER)', state: 'connected' },
      { name: 'loopMIDI', state: 'connected' },
      { name: 'PACER', state: 'connected' },
    ];
    expect(pickPort(ports)?.name).toBe('PACER');
    expect(pickPort(ports, 'loopMIDI')?.name).toBe('loopMIDI');
    expect(pickPort([{ name: 'PACER', state: 'disconnected' }])).toBeUndefined();
  });
});
