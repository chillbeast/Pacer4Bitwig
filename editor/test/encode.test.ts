import { describe, expect, it } from 'vitest';
import {
  clonePreset,
  concatMessages,
  createPreset,
  describePart,
  diffMessages,
  diffParts,
  encodeDump,
  encodeLed,
  encodeMidiSetting,
  encodeName,
  encodePreset,
  encodeStep,
  messageChecksumValid,
  MSG,
  parseDump,
  presetKey,
  presetsEqual,
  splitSysex,
  type Preset,
} from '../src/pacer';
import { bytes, fixture, hex } from './helpers';

describe('round trip over ALL.factory.bin', () => {
  const original = fixture('ALL.factory.bin');
  const parsed = parseDump(original);

  it('parse → encode → parse yields equal presets', () => {
    for (const [index, { preset }] of parsed.presets) {
      const reparsed = parseDump(concatMessages(encodePreset(preset, index)));
      const again = reparsed.presets.get(index);
      expect(again?.complete).toBe(true);
      expect(again?.preset).toEqual(preset);
      expect(presetKey(again!.preset)).toBe(presetKey(preset));
    }
  });

  it('re-encodes the whole dump byte for byte', () => {
    const encoded = encodeDump(
      [...parsed.presets].map(([i, p]) => [i, p.preset] as const),
      parsed.globals,
    );
    expect(encoded.length).toBe(original.length);
    expect(hex(encoded)).toBe(hex(original));
  });

  it('encodes 189 valid messages per preset in dump order', () => {
    const preset = parsed.presets.get(1)!.preset;
    const messages = encodePreset(preset, 1);
    expect(messages).toHaveLength(189);
    expect(messages.every(messageChecksumValid)).toBe(true);
    expect(hex(concatMessages(messages))).toBe(hex(fixture('A1.factory.syx')));
  });

  it('round-trips a user patch file byte for byte', () => {
    const data = fixture('B1-B6.bias-fx.syx');
    const result = parseDump(data);
    expect(result.presets.size).toBe(6);
    const encoded = encodeDump([...result.presets].map(([i, p]) => [i, p.preset] as const));
    expect(hex(encoded)).toBe(hex(data));
  });
});

describe('message encoders', () => {
  it('encodes the name like the Pacer does (elm 0x01, 5 chars)', () => {
    expect(hex(encodeName('G-MST', 0x13))).toBe(hex(bytes('F0 00 01 77 7F 01 01 13 01 01 05 47 2D 4D 53 54 7C F7')));
    // padded with spaces
    expect(hex(encodeName('AB', 1)).includes('41 42 20 20 20')).toBe(true);
  });

  it('encodes preset MIDI settings with 5 elements (no "active" byte)', () => {
    const msg = encodeMidiSetting(0, { channel: 0, msgType: MSG.OFF, data: [0, 0, 0] }, 0x13);
    expect(hex(msg)).toBe(
      hex(bytes('F0 00 01 77 7F 01 01 13 7E 01 01 00 00 02 01 61 00 03 01 00 00 04 01 00 00 05 01 00 78 F7')),
    );
  });

  it('encodes a step and a LED config', () => {
    const step = encodeStep('SWA', 1, { channel: 16, msgType: MSG.SW_CC_TRIGGER, data: [50, 127, 0], active: true }, 0x13);
    expect(hex(step.subarray(0, 13))).toBe('F0 00 01 77 7F 01 01 13 14 07 01 10 00');
    expect(step[step.length - 3]).toBe(0x01); // active, no trailing 0x00
    expect(messageChecksumValid(step)).toBe(true);

    const led = encodeLed('SW1', 0, { midiCtrl: false, onColor: 0x7f, offColor: 0x7f, num: 0 }, 1);
    expect(hex(led)).toBe(hex(fixture('A1.led-example.bin')));
    expect(() => encodeLed('FS1', 0, { midiCtrl: false, onColor: 0, offColor: 0, num: 0 }, 1)).toThrow();
  });

  it('clamps values to 7 bits', () => {
    const msg = encodeStep('SW1', 0, { channel: 99, msgType: 300, data: [-5, 128, 64], active: true }, 1);
    expect(messageChecksumValid(msg)).toBe(true);
    const parsed = parseDump(msg).presets.get(1)!.preset.controls.SW1.steps[0];
    expect(parsed).toEqual({ channel: 16, msgType: 127, data: [0, 127, 64], active: true });
  });

  it('rejects invalid preset indexes', () => {
    expect(() => encodeName('X', 25)).toThrow(RangeError);
    expect(() => encodeName('X', -1)).toThrow(RangeError);
  });
});

describe('diffMessages', () => {
  const base: Preset = parseDump(fixture('A1.factory.syx')).presets.get(1)!.preset;
  const edit = (fn: (p: Preset) => void) => {
    const p = clonePreset(base);
    fn(p);
    return p;
  };

  it('returns nothing for identical presets', () => {
    expect(diffMessages(base, clonePreset(base), 1)).toEqual([]);
    expect(presetsEqual(base, clonePreset(base))).toBe(true);
  });

  it('returns every message when the original is unknown', () => {
    expect(diffMessages(null, base, 1)).toHaveLength(189);
  });

  it('returns only the changed parts', () => {
    const edited = edit((p) => {
      p.name = 'LOOPS';
      p.controls.SWB.steps[2].data[0] = 42;
      p.controls.SWB.leds![2].onColor = 0x03;
      p.controls.EXP2.mode = 2;
      p.midi[7].channel = 16;
    });
    const parts = diffParts(base, edited, 1).map((p) => describePart(p.part));
    expect(parts).toEqual([
      'Preset name',
      'Switch B · step 3',
      'Switch B · LED 3',
      'Expression 2 · control mode',
      'On-load MIDI 8',
    ]);
    const messages = diffMessages(base, edited, 1);
    const applied = parseDump(concatMessages([...encodePreset(base, 1), ...messages])).presets.get(1)!.preset;
    expect(presetsEqual(applied, edited)).toBe(true);
  });

  it('treats name padding as equal', () => {
    const a = createPreset('AB');
    const b = clonePreset(a);
    b.name = 'AB';
    expect(presetsEqual(a, b)).toBe(true);
    expect(diffMessages(a, b, 3)).toEqual([]);
  });

  it('produces messages that parse into the target slot', () => {
    const messages = encodePreset(base, 19);
    const result = parseDump(concatMessages(messages));
    expect([...result.presets.keys()]).toEqual([19]);
    expect(splitSysex(concatMessages(messages))).toHaveLength(189);
  });
});
