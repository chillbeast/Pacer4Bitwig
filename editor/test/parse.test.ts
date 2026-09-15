import { describe, expect, it } from 'vitest';
import {
  FULL_BACKUP_MESSAGES,
  MSG,
  PRESET_PART_COUNT,
  SINGLE_PRESET_MESSAGES,
  displayName,
  parseDump,
  slotLabel,
  splitSysex,
} from '../src/pacer';
import { fixture } from './helpers';

describe('parseDump — factory full backup', () => {
  const result = parseDump(fixture('ALL.factory.bin'));

  it('finds 25 complete presets and 37 global messages', () => {
    expect(PRESET_PART_COUNT).toBe(SINGLE_PRESET_MESSAGES);
    expect(result.messageCount).toBe(FULL_BACKUP_MESSAGES);
    expect(result.presets.size).toBe(25);
    expect([...result.presets.values()].every((p) => p.complete && p.hasLeds)).toBe(true);
    expect(result.globals).toHaveLength(37);
    expect(result.badChecksums).toBe(0);
    expect(result.ignored).toBe(0);
    expect(result.warnings).toEqual([]);
  });

  it('reads the preset names', () => {
    const names = [...result.presets.values()].map((p) => `${slotLabel(p.index)}=${displayName(p.preset.name)}`);
    expect(names.slice(0, 6)).toEqual(['CUR=PRGM1', 'A1=PRGM1', 'A2=PRGM2', 'A3=PRGM3', 'A4=PRGM4', 'A5=NOTES']);
    expect(result.presets.get(20)?.preset.name).toBe('COLOR');
    expect(result.presets.get(23)?.preset.name).toBe('MMC  ');
    expect(result.presets.get(24)?.preset.name).toBe('SEQNC');
  });

  it('reads steps', () => {
    const notes = result.presets.get(5)!.preset;
    expect(notes.controls.SW1.steps[0]).toEqual({ channel: 0, msgType: MSG.SW_NOTE, data: [52, 127, 0], active: true });
    const helix = result.presets.get(7)!.preset;
    expect(helix.controls.FS1.steps[1]).toEqual({
      channel: 0,
      msgType: MSG.SW_STEP_INC_DEC,
      data: [15, 0, 0],
      active: true,
    });
    const prgm = result.presets.get(1)!.preset;
    expect(prgm.controls.EXP1.steps[0]).toEqual({ channel: 0, msgType: MSG.AD_CC, data: [7, 0, 127], active: true });
    expect(prgm.controls.SW1.steps[5]).toEqual({ channel: 0, msgType: MSG.OFF, data: [0, 0, 0], active: false });
    const mmc = result.presets.get(23)!.preset;
    expect(mmc.controls.SW2.steps[0]).toMatchObject({ msgType: MSG.SW_MMC, data: [127, 5, 0] });
  });

  it('reads LEDs only for stompswitches', () => {
    const color = result.presets.get(20)!.preset;
    expect(color.controls.SW1.leds?.[0]).toEqual({ midiCtrl: false, onColor: 0x03, offColor: 0x04, num: 0 });
    expect(color.controls.SW2.leds?.[0]).toEqual({ midiCtrl: false, onColor: 0x05, offColor: 0x06, num: 0 });
    expect(color.controls.SW1.leds?.[1]).toEqual({ midiCtrl: false, onColor: 0x7f, offColor: 0x7f, num: 0 });
    expect(color.controls.FS1.leds).toBeNull();
    expect(color.controls.EXP2.leds).toBeNull();
  });

  it('reads the preset MIDI (on load) messages', () => {
    const notes = result.presets.get(5)!.preset;
    expect(notes.midi[0]).toEqual({ channel: 1, msgType: MSG.LOAD_CC, data: [123, 127, 0] });
    expect(notes.midi[15]).toEqual({ channel: 16, msgType: MSG.LOAD_CC, data: [123, 127, 0] });
    expect(result.presets.get(1)!.preset.midi[0].msgType).toBe(MSG.OFF);
  });
});

describe('parseDump — partial data', () => {
  it('parses a single-control reply (no LEDs) into an incomplete preset', () => {
    const result = parseDump(fixture('A5.stompswitch-5.bin'));
    const parsed = result.presets.get(5)!;
    expect(parsed.complete).toBe(false);
    expect(parsed.parts).toBe(6);
    expect(parsed.hasLeds).toBe(false);
    const sw5 = parsed.preset.controls.SW5;
    expect(sw5.steps[0]).toEqual({ channel: 15, msgType: MSG.SW_CC_TOGGLE, data: [68, 85, 102], active: true });
    expect(sw5.steps[1]).toEqual({ channel: 0, msgType: MSG.SW_NOTE, data: [35, 127, 0], active: true });
    expect(sw5.steps[5]).toEqual({ channel: 0, msgType: MSG.OFF, data: [0, 127, 0], active: false });
  });

  it('keeps global messages from a global-only file', () => {
    const result = parseDump(fixture('GLOBAL.factory.syx'));
    expect(result.presets.size).toBe(0);
    expect(result.globals).toHaveLength(37);
  });

  it('parses a single-preset file', () => {
    const result = parseDump(fixture('A1.factory.syx'));
    expect(result.presets.size).toBe(1);
    const [parsed] = result.presets.values();
    expect(parsed.complete).toBe(true);
    expect(splitSysex(fixture('A1.factory.syx'))).toHaveLength(189);
  });

  it('skips messages with a bad checksum and reports them', () => {
    const data = fixture('A1.factory.syx');
    data[12] ^= 0x01; // corrupt the name
    const result = parseDump(data);
    expect(result.badChecksums).toBe(1);
    expect(result.warnings[0]).toMatch(/checksum/i);
    expect([...result.presets.values()][0].complete).toBe(false);
  });

  it('ignores non-Pacer SysEx and junk bytes', () => {
    const junk = Uint8Array.of(0x90, 0x40, 0x7f, 0xf0, 0x7e, 0x7f, 0x06, 0x01, 0xf7, 0x12);
    const result = parseDump(junk);
    expect(result.presets.size).toBe(0);
    expect(result.ignored).toBe(1);
  });
});
