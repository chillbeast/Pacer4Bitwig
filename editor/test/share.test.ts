import { describe, expect, it } from 'vitest';
import {
  ShareLinkError,
  decodeShareToken,
  encodeShareToken,
  packPreset,
  parseDump,
  presetKey,
  shareTokenFromHash,
  shareUrl,
  toBase64Url,
  unpackPreset,
} from '../src/pacer';
import { LOOPER_LABELS, buildBitwigLooperPreset } from '../src/templates/bitwigLooper';
import { fixture } from './helpers';

describe('share links', () => {
  const factory = parseDump(fixture('ALL.factory.bin'));

  it('round-trips every factory preset', async () => {
    for (const [index, { preset }] of factory.presets) {
      const token = await encodeShareToken({ preset, labels: {}, slot: index });
      const back = await decodeShareToken(token);
      expect(presetKey(back.preset)).toBe(presetKey(preset));
      expect(back.slot).toBe(index);
    }
  });

  it('keeps labels and stays short', async () => {
    const preset = buildBitwigLooperPreset();
    const token = await encodeShareToken({ preset, labels: LOOPER_LABELS, slot: 19 });
    expect(token.startsWith('z')).toBe(true);
    expect(token.length).toBeLessThan(900);
    const back = await decodeShareToken(token);
    expect(back.labels).toEqual(LOOPER_LABELS);
    expect(back.preset).toEqual(preset);
  });

  it('accepts uncompressed tokens', async () => {
    const preset = factory.presets.get(5)!.preset;
    const token = `r${toBase64Url(packPreset({ preset, labels: { SW1: 'E3' }, slot: null }))}`;
    const back = await decodeShareToken(token);
    expect(back.slot).toBeNull();
    expect(back.labels).toEqual({ SW1: 'E3' });
  });

  it('builds and reads URLs', async () => {
    const token = await encodeShareToken({ preset: factory.presets.get(1)!.preset, labels: {}, slot: 1 });
    const url = shareUrl('https://example.org/Pacer4Bitwig/#old', token);
    expect(url).toBe(`https://example.org/Pacer4Bitwig/#preset=${token}`);
    expect(shareTokenFromHash(new URL(url).hash)).toBe(token);
    expect(shareTokenFromHash('#other=1')).toBeNull();
  });

  it('rejects garbage', async () => {
    await expect(decodeShareToken('zhello world')).rejects.toBeInstanceOf(ShareLinkError);
    await expect(decodeShareToken('zAAAA')).rejects.toBeInstanceOf(ShareLinkError);
    await expect(decodeShareToken('x' + 'A'.repeat(40))).rejects.toThrow(/unknown format/);
    await expect(decodeShareToken('z' + 'A'.repeat(20000))).rejects.toThrow(/length/);
  });

  it('rejects tampered payloads', () => {
    const payload = packPreset({ preset: factory.presets.get(2)!.preset, labels: { SW1: 'X' }, slot: 2 });
    const flip = (i: number, v: number) => {
      const copy = payload.slice();
      copy[i] = v;
      return copy;
    };
    expect(() => unpackPreset(flip(0, 0x51))).toThrow(/not a Pacer Studio/);
    expect(() => unpackPreset(flip(2, 9))).toThrow(/version/);
    expect(() => unpackPreset(flip(10, (payload[10] + 1) & 0x7f))).toThrow(/checksum|range|flag|channel|mode/);
    expect(() => unpackPreset(flip(20, 0x80))).toThrow(ShareLinkError);
    expect(() => unpackPreset(payload.subarray(0, payload.length - 1))).toThrow();
    const badLabels = packPreset({ preset: factory.presets.get(2)!.preset, labels: {}, slot: 2 });
    const withJunk = new Uint8Array(badLabels.length + 3);
    withJunk.set(badLabels);
    withJunk[badLabels.length - 1] = 3; // claims 3 label bytes
    withJunk.set([0x7b, 0x31, 0x7d], badLabels.length); // "{1}"
    expect(() => unpackPreset(withJunk)).toThrow(/labels/);
  });
});
