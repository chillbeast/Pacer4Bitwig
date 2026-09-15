import { describe, expect, it } from 'vitest';
import { MSG, clonePreset, createFollower, createPreset, createStep, parseDump } from '../src/pacer';
import { buildBitwigLooperPreset } from '../src/templates/bitwigLooper';
import { buildMmcTransport } from '../src/templates/pedalboards';
import { fixture } from './helpers';

const msg = (...b: number[]) => Uint8Array.from(b);

describe('hardware follow', () => {
  const looper = buildBitwigLooperPreset('multi-colour');

  it('maps a looper CC on channel 16 to its switch', () => {
    const f = createFollower();
    expect(f.match(looper, msg(0xbf, 102, 127))).toBe('SW1');
    expect(f.match(looper, msg(0xbf, 111, 0))).toBe('SWD');
    expect(f.match(looper, msg(0xbf, 116, 64))).toBe('EXP1');
    expect(f.match(looper, msg(0xbf, 113, 127))).toBe('FS2');
  });

  it('ignores colour-slot CCs, other channels and unrelated messages', () => {
    const f = createFollower();
    expect(f.match(looper, msg(0xbf, 20, 127))).toBeNull(); // step 2 of SW1, not step 1
    expect(f.match(looper, msg(0xb0, 102, 127))).toBeNull();
    expect(f.match(looper, msg(0xf8))).toBeNull();
    expect(f.match(null, msg(0xbf, 102, 127))).toBeNull();
  });

  it('returns nothing when two controls match equally', () => {
    const p = clonePreset(looper);
    p.controls.SW2.steps[0] = createStep({ channel: 16, msgType: MSG.SW_CC_TOGGLE, data: [102, 127, 0], active: true });
    expect(createFollower().match(p, msg(0xbf, 102, 127))).toBeNull();
  });

  it('resolves the global channel', () => {
    const p = createPreset('X');
    p.controls.SWA.steps[0] = createStep({ channel: 0, msgType: MSG.SW_NOTE, data: [60, 100, 0], active: true });
    const f = createFollower();
    expect(f.match(p, msg(0x92, 60, 100), 3)).toBe('SWA');
    expect(f.match(p, msg(0x90, 60, 100), 3)).toBeNull();
    expect(f.match(p, msg(0x95, 60, 0), null)).toBe('SWA');
  });

  it('prefers an exact program switch over overlapping program-step ranges (factory PRGM1)', () => {
    const prgm = parseDump(fixture('A1.factory.syx')).presets.get(1)!.preset;
    const f = createFollower();
    expect(f.match(prgm, msg(0xc0, 2))).toBe('SW3');
    expect(f.match(prgm, msg(0xc0, 77))).toBeNull(); // only the two FS ranges match
  });

  it('follows MMC and NRPN', () => {
    const f = createFollower();
    const { preset } = buildMmcTransport();
    expect(f.match(preset, msg(0xf0, 0x7f, 0x7f, 0x06, 0x05, 0xf7))).toBe('SW1');
    expect(f.match(preset, msg(0xf0, 0x7f, 0x10, 0x06, 0x09, 0xf7))).toBe('SW6');

    const p = createPreset('N');
    p.controls.SWB.steps[0] = createStep({ channel: 4, msgType: MSG.SW_NRPN_COARSE, data: [10, 2, 1], active: true });
    expect(f.match(p, msg(0xb3, 99, 1))).toBeNull();
    expect(f.match(p, msg(0xb3, 98, 2))).toBe('SWB');
  });
});
