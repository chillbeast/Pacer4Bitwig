import { describe, expect, it } from 'vitest';
import { encodePreset, messageChecksumValid, parseDump, concatMessages } from '../src/pacer';
import { buildBitwigFxPreset } from '../src/templates/bitwigFx';
import {
  BITWIG_PRESET_LOADED_VALUE,
  LOOPER_DEFAULT_SLOT,
  buildBitwigLooperPreset,
  decodePresetLoadedValue,
  looperChannelOf,
  presetAnnouncementOf,
  type LooperLedMode,
} from '../src/templates/bitwigLooper';

const MODES: LooperLedMode[] = ['two-colour', 'multi-colour'];

describe('Bitwig Looper MIDI channel', () => {
  it('defaults to channel 16', () => {
    for (const mode of MODES) {
      expect(buildBitwigLooperPreset(mode)).toEqual(buildBitwigLooperPreset(mode, 16));
      expect(looperChannelOf(buildBitwigLooperPreset(mode))).toBe(16);
    }
  });

  for (const mode of MODES) {
    it(`${mode}: another channel changes only channel bytes and checksums`, () => {
      const base = encodePreset(buildBitwigLooperPreset(mode, 16), LOOPER_DEFAULT_SLOT);
      const moved = encodePreset(buildBitwigLooperPreset(mode, 5), LOOPER_DEFAULT_SLOT);
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
      // switches: step 1 (two-colour) or steps 1–6 (multi-colour), 4 footswitch jacks, 2 pedals, the preset-loaded CC
      expect(changedMessages).toBe((mode === 'multi-colour' ? 60 : 10) + 4 + 2 + 1);
    });
  }

  it('moves every channel-16 value and keeps the CC numbers', () => {
    const p = buildBitwigLooperPreset('multi-colour', 1);
    const channels = Object.values(p.controls).flatMap((c) => c.steps.filter((s) => s.active).map((s) => s.channel));
    expect(new Set(channels)).toEqual(new Set([1]));
    expect(p.controls.SW1.steps[0].data).toEqual([102, 127, 0]);
    expect(p.controls.SWD.steps[5].data).toEqual([69, 127, 0]);
    expect(p.midi[0]).toMatchObject({ channel: 1, data: [119, 2, 0] });
    expect(looperChannelOf(p)).toBe(1);
    // survives an encode/parse round trip
    const back = parseDump(concatMessages(encodePreset(p, 19))).presets.get(19)!.preset;
    expect(looperChannelOf(back)).toBe(1);
  });

  it('rejects invalid channels', () => {
    expect(() => buildBitwigLooperPreset('two-colour', 0)).toThrow(RangeError);
    expect(() => buildBitwigLooperPreset('two-colour', 17)).toThrow(RangeError);
  });

  it('announces the preset and LED variant in the preset-loaded value', () => {
    expect(BITWIG_PRESET_LOADED_VALUE).toEqual({
      looper: { 'two-colour': 127, 'multi-colour': 2 },
      fx: { 'two-colour': 17, 'multi-colour': 18 },
    });
    for (const mode of MODES) {
      expect(presetAnnouncementOf(buildBitwigLooperPreset(mode))).toEqual({ kind: 'looper', mode });
      expect(presetAnnouncementOf(buildBitwigLooperPreset(mode, 9))).toEqual({ kind: 'looper', mode });
      expect(presetAnnouncementOf(buildBitwigFxPreset(mode))).toEqual({ kind: 'fx', mode });
      expect(presetAnnouncementOf(buildBitwigFxPreset(mode, 9))).toEqual({ kind: 'fx', mode });
    }
    const mixed = buildBitwigLooperPreset('two-colour');
    mixed.controls.SW3.steps[0].channel = 2;
    expect(looperChannelOf(mixed)).toBeNull();
    expect(presetAnnouncementOf(mixed)).toBeNull();
  });

  it('decodes preset-loaded values as kind × 16 + variant, 127 = looper two-colour', () => {
    expect(decodePresetLoadedValue(127)).toEqual({ kind: 'looper', mode: 'two-colour' });
    expect(decodePresetLoadedValue(2)).toEqual({ kind: 'looper', mode: 'multi-colour' });
    expect(decodePresetLoadedValue(17)).toEqual({ kind: 'fx', mode: 'two-colour' });
    expect(decodePresetLoadedValue(18)).toEqual({ kind: 'fx', mode: 'multi-colour' });
    // unknown kinds count as the looper
    expect(decodePresetLoadedValue(34)).toEqual({ kind: 'looper', mode: 'multi-colour' });
    expect(decodePresetLoadedValue(0)).toBeNull();
    expect(decodePresetLoadedValue(19)).toBeNull();
  });
});
