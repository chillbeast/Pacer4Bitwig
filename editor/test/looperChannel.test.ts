import { describe, expect, it } from 'vitest';
import { encodePreset, messageChecksumValid, parseDump, concatMessages } from '../src/pacer';
import {
  LOOPER_DEFAULT_SLOT,
  LOOPER_PRESET_LOADED_VALUE,
  announcesBitwig,
  announcesBitwigPreset,
  buildBitwigLooperPreset,
  looperChannelOf,
} from '../src/templates/bitwigLooper';

describe('Bitwig Pacer MIDI channel', () => {
  it('defaults to channel 16', () => {
    expect(buildBitwigLooperPreset()).toEqual(buildBitwigLooperPreset(16));
    expect(looperChannelOf(buildBitwigLooperPreset())).toBe(16);
  });

  it('another channel changes only channel bytes and checksums', () => {
    const base = encodePreset(buildBitwigLooperPreset(16), LOOPER_DEFAULT_SLOT);
    const moved = encodePreset(buildBitwigLooperPreset(5), LOOPER_DEFAULT_SLOT);
    expect(moved).toHaveLength(base.length);
    let changedMessages = 0;
    base.forEach((msg, i) => {
      const other = moved[i];
      expect(other.length).toBe(msg.length);
      expect(messageChecksumValid(other)).toBe(true);
      const diffs = [...msg.keys()].filter((k) => msg[k] !== other[k]);
      if (diffs.length === 0) return;
      changedMessages++;
      // step and on-load messages: F0 00 01 77 7F cmd tgt idx obj elm(ch) 01 <channel> …
      const channelElement = (msg[9] - 1) % 6 === 0;
      expect(channelElement).toBe(true);
      expect(diffs.filter((k) => k !== msg.length - 2)).toEqual([11]);
      expect([msg[11], other[11]]).toEqual([16, 5]);
    });
    // 10 switches (step 1 only), 4 footswitch jacks, 2 pedals, the preset-loaded CC
    expect(changedMessages).toBe(10 + 4 + 2 + 1);
  });

  it('moves every channel-16 value and keeps the CC numbers', () => {
    const p = buildBitwigLooperPreset(1);
    const channels = Object.values(p.controls).flatMap((c) => c.steps.filter((s) => s.active).map((s) => s.channel));
    expect(new Set(channels)).toEqual(new Set([1]));
    expect(p.controls.SW1.steps[0].data).toEqual([102, 127, 0]);
    expect(p.controls.SWD.steps[0].data).toEqual([111, 127, 0]);
    expect(p.midi[0]).toMatchObject({ channel: 1, data: [119, LOOPER_PRESET_LOADED_VALUE, 0] });
    expect(looperChannelOf(p)).toBe(1);
    // survives an encode/parse round trip
    const back = parseDump(concatMessages(encodePreset(p, 19))).presets.get(19)!.preset;
    expect(looperChannelOf(back)).toBe(1);
  });

  it('rejects invalid channels', () => {
    expect(() => buildBitwigLooperPreset(0)).toThrow(RangeError);
    expect(() => buildBitwigLooperPreset(17)).toThrow(RangeError);
  });

  it('announces itself so the extension repaints the board', () => {
    expect(LOOPER_PRESET_LOADED_VALUE).toBe(127);
    expect(announcesBitwig(buildBitwigLooperPreset())).toBe(true);
    expect(announcesBitwig(buildBitwigLooperPreset(9))).toBe(true);

    const mixed = buildBitwigLooperPreset();
    mixed.controls.SW3.steps[0].channel = 2;
    expect(looperChannelOf(mixed)).toBeNull();
    expect(announcesBitwig(mixed)).toBe(false);
  });

  it('still recognises the values older presets send', () => {
    // 127 and 17 were the two presets; 2 and 18 were their multi-colour variants, which no longer exist
    expect(announcesBitwigPreset(127)).toBe(true);
    expect(announcesBitwigPreset(17)).toBe(true);
    expect(announcesBitwigPreset(2)).toBe(true);
    expect(announcesBitwigPreset(18)).toBe(true);
    expect(announcesBitwigPreset(34)).toBe(true);
    expect(announcesBitwigPreset(0)).toBe(false);
    expect(announcesBitwigPreset(19)).toBe(false);
  });
});
