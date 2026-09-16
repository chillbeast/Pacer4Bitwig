import { describe, expect, it } from 'vitest';
import { clonePreset, describePartValue, diffByControl, parseDump } from '../src/pacer';
import { LOOPER_LABELS, LOOPER_TAP_HOLD, buildBitwigLooperPreset } from '../src/templates/bitwigLooper';
import { bitwigRolesOf, cheatLabel } from '../src/ui/CheatSheet';
import { fixture } from './helpers';

describe('per-control diff', () => {
  const a1 = parseDump(fixture('A1.factory.syx')).presets.get(1)!.preset;

  it('groups changed parts by control and describes both sides', () => {
    const edited = clonePreset(a1);
    edited.name = 'HELLO';
    edited.controls.SW2.steps[0].data[0] = 42;
    edited.controls.SW2.leds![0].onColor = 0x03;
    edited.controls.EXP1.mode = 1;
    edited.midi[3].msgType = 0x65;

    const diff = diffByControl(a1, edited);
    expect(diff.map((d) => [d.label, d.parts.length])).toEqual([
      ['Preset name', 1],
      ['Switch 2', 2],
      ['Expression 1', 1],
      ['On-load MIDI', 1],
    ]);
    const [step, led] = diff[1].parts;
    expect(describePartValue(step, a1)).toBe('Program & Bank · PC 1 · global');
    expect(describePartValue(step, edited)).toBe('Program & Bank · PC 42 · global');
    expect(describePartValue(led, edited)).toBe('on Red / off Default · Default · local');
    expect(describePartValue(diff[0].parts[0], edited)).toBe('“HELLO”');
    expect(describePartValue(diff[2].parts[0], edited)).toBe('Sequence');
    expect(describePartValue(diff[0].parts[0], null)).toBe('—');
  });

  it('treats an empty slot as "everything differs"', () => {
    expect(diffByControl(null, a1).reduce((n, g) => n + g.parts.length, 0)).toBe(189);
    expect(diffByControl(a1, clonePreset(a1))).toEqual([]);
  });
});

describe('cheat sheet labels', () => {
  it('uses the PACER Looper roles for the looper layout', () => {
    const looper = buildBitwigLooperPreset();
    expect(bitwigRolesOf(looper)).toBe(LOOPER_TAP_HOLD);
    expect(cheatLabel(looper, LOOPER_LABELS, 'SWA', LOOPER_TAP_HOLD)).toEqual({ title: 'Undo', hold: 'Redo' });
    expect(cheatLabel(looper, LOOPER_LABELS, 'SW6', LOOPER_TAP_HOLD)).toEqual({ title: 'Previous mode', hold: 'Mode menu' });
    expect(cheatLabel(looper, LOOPER_LABELS, 'EXP1', LOOPER_TAP_HOLD)).toEqual({ title: 'Selected track volume', hold: undefined });
  });

  it('uses the same roles whatever channel the preset is on', () => {
    // There is one Bitwig preset now; what the switches do is the extension's active mode, not the preset
    const moved = buildBitwigLooperPreset(3);
    expect(bitwigRolesOf(moved)).toBe(LOOPER_TAP_HOLD);
  });

  it('falls back to editor labels, then the message type', () => {
    const a1 = parseDump(fixture('A1.factory.syx')).presets.get(1)!.preset;
    expect(bitwigRolesOf(a1)).toBeNull();
    expect(cheatLabel(a1, { SW1: 'CLEAN' }, 'SW1', null)).toEqual({ title: 'CLEAN' });
    expect(cheatLabel(a1, {}, 'SW2', null)).toEqual({ title: 'Program & Bank' });
    expect(cheatLabel(a1, {}, 'FS3', null)).toEqual({ title: '—' });
  });
});
