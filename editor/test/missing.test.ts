import { describe, expect, it } from 'vitest';
import { parseDump, parseMessages, splitSysex, summarizeParts } from '../src/pacer';
import { fixture } from './helpers';

describe('missing parts', () => {
  it('is empty for a complete preset', () => {
    const p = parseDump(fixture('A1.factory.syx')).presets.get(1)!;
    expect(p.missing).toEqual([]);
  });

  it('lists what did not arrive, in dump order', () => {
    const messages = splitSysex(fixture('A1.factory.syx')).filter(
      (m) => !(m[8] === 0x15 && m[9] >= 0x40 && m[9] < 0x60) && !(m[8] === 0x7e && m[9] >= 0x0d) && m[8] !== 0x01,
    );
    const p = parseMessages(messages).presets.get(1)!;
    expect(p.parts).toBe(189 - 1 - 6 - 14);
    expect(summarizeParts(p.missing)).toBe('name, Switch B LEDs 1–6, on-load MIDI 3–16');
  });

  it('shortens long lists', () => {
    const p = parseMessages(splitSysex(fixture('A5.stompswitch-5.bin'))).presets.get(5)!;
    expect(summarizeParts(p.missing, 3)).toMatch(/^name, Switch 1 mode, Switch 1 steps 1–6 \+\d+ more$/);
  });
});
