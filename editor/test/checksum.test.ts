import { describe, expect, it } from 'vitest';
import { checksum, messageChecksumValid, splitSysex } from '../src/pacer';
import { bytes, fixture } from './helpers';

describe('checksum', () => {
  it('matches request checksums documented in the reference dumps', () => {
    // 00 01 77 7F 02 01 07 7F 77
    expect(checksum([0x02, 0x01, 0x07, 0x7f])).toBe(0x77);
    // 00 01 77 7F 02 01 17 7F 67
    expect(checksum([0x02, 0x01, 0x17, 0x7f])).toBe(0x67);
    // D6: 00 01 77 7F 02 01 18 7F 66
    expect(checksum([0x02, 0x01, 0x18, 0x7f])).toBe(0x66);
    // all presets: 00 01 77 7F 02 01 7F 7E
    expect(checksum([0x02, 0x01, 0x7f])).toBe(0x7e);
    // single control, no LEDs: 00 01 77 7F 02 01 14 0D 5C
    expect(checksum([0x02, 0x01, 0x14, 0x0d])).toBe(0x5c);
  });

  it('is zero when the sum is a multiple of 128', () => {
    expect(checksum([0x40, 0x40])).toBe(0);
    expect(checksum([])).toBe(0);
  });

  it('validates the LED example message byte for byte', () => {
    const msg = fixture('A1.led-example.bin');
    expect(msg[msg.length - 2]).toBe(0x68);
    expect(messageChecksumValid(msg)).toBe(true);
  });

  it('validates the name and preset MIDI messages from a real device dump', () => {
    expect(messageChecksumValid(bytes('F0 00 01 77 7F 01 01 13 01 01 05 47 2D 4D 53 54 7C F7'))).toBe(true);
    expect(
      messageChecksumValid(
        bytes('F0 00 01 77 7F 01 01 13 7E 01 01 00 00 02 01 61 00 03 01 00 00 04 01 00 00 05 01 00 78 F7'),
      ),
    ).toBe(true);
  });

  it('accepts every message of the factory full dump', () => {
    const messages = splitSysex(fixture('ALL.factory.bin'));
    expect(messages).toHaveLength(4762);
    expect(messages.every(messageChecksumValid)).toBe(true);
  });

  it('rejects a corrupted message', () => {
    const msg = fixture('A1.led-example.bin');
    msg[12] ^= 0x01;
    expect(messageChecksumValid(msg)).toBe(false);
  });
});
