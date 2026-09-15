import { describe, expect, it } from 'vitest';
import { MSG, describeMidiMessage, encodePreset, splitSysex, toHex } from '../src/pacer';
import {
  LOOPER_ACTION_CC,
  LOOPER_DEFAULT_SLOT,
  LOOPER_FOOTSWITCHES,
  LOOPER_SWITCHES,
  buildBitwigLooperPreset,
  colourSlotCc,
  type LooperLedMode,
} from '../src/templates/bitwigLooper';
import { fixture } from './helpers';

describe('Bitwig Looper template (docs/PACER-MAP.md)', () => {
  it('targets D1 by default', () => {
    expect(LOOPER_DEFAULT_SLOT).toBe(0x13);
  });

  it('has the documented colour-slot CC ranges', () => {
    const ranges = LOOPER_SWITCHES.map((_, s) => [colourSlotCc(s, 2), colourSlotCc(s, 6)]);
    expect(ranges).toEqual([
      [20, 24], [25, 29], [30, 34], [35, 39], [40, 44],
      [45, 49], [50, 54], [55, 59], [60, 64], [65, 69],
    ]);
  });

  for (const mode of ['two-colour', 'multi-colour'] as LooperLedMode[]) {
    describe(mode, () => {
      const preset = buildBitwigLooperPreset(mode);

      it('names the preset LOOPS and uses "all steps at once"', () => {
        expect(preset.name).toBe('LOOPS');
        for (const control of Object.values(preset.controls)) expect(control.mode).toBe(0);
      });

      it('maps switches to CC Trigger 127/0 on channel 16', () => {
        LOOPER_SWITCHES.forEach((key, s) => {
          expect(LOOPER_ACTION_CC[key]).toBe(102 + s);
          expect(preset.controls[key].steps[0]).toEqual({
            channel: 16,
            msgType: MSG.SW_CC_TRIGGER,
            data: [102 + s, 127, 0],
            active: true,
          });
        });
      });

      it('maps footswitches, expression pedals and the preset-loaded message', () => {
        LOOPER_FOOTSWITCHES.forEach((key, f) => {
          expect(preset.controls[key].steps[0]).toEqual({
            channel: 16,
            msgType: MSG.SW_CC_TRIGGER,
            data: [112 + f, 127, 0],
            active: true,
          });
          expect(preset.controls[key].steps.slice(1).every((st) => !st.active)).toBe(true);
        });
        expect(preset.controls.EXP1.steps[0]).toEqual({ channel: 16, msgType: MSG.AD_CC, data: [116, 0, 127], active: true });
        expect(preset.controls.EXP2.steps[0]).toEqual({ channel: 16, msgType: MSG.AD_CC, data: [117, 0, 127], active: true });
        expect(preset.midi[0]).toEqual({ channel: 16, msgType: MSG.LOAD_CC, data: [119, 127, 0] });
        expect(preset.midi.slice(1).every((m) => m.msgType === MSG.OFF)).toBe(true);
      });

      if (mode === 'two-colour') {
        it('puts LED config on step 1 only (red loops, white others)', () => {
          LOOPER_SWITCHES.forEach((key, s) => {
            const control = preset.controls[key];
            expect(control.leds![0]).toEqual({ midiCtrl: true, onColor: s < 4 ? 0x03 : 0x17, offColor: 0x00, num: 0 });
            expect(control.leds!.slice(1).every((l) => !l.midiCtrl)).toBe(true);
            expect(control.steps.slice(1).every((st) => !st.active && st.msgType === MSG.OFF)).toBe(true);
          });
        });
      } else {
        it('uses steps 2–6 as colour slots', () => {
          const colours = [0x17, 0x03, 0x0d, 0x07, 0x11, 0x15];
          LOOPER_SWITCHES.forEach((key, s) => {
            const control = preset.controls[key];
            for (let step = 2; step <= 6; step++) {
              expect(control.steps[step - 1]).toEqual({
                channel: 16,
                msgType: MSG.SW_CC_TRIGGER,
                data: [20 + s * 5 + (step - 2), 127, 0],
                active: true,
              });
            }
            control.leds!.forEach((led, i) =>
              expect(led).toEqual({ midiCtrl: true, onColor: colours[i], offColor: 0x00, num: 0 }),
            );
          });
        });
      }

      // Two independent implementations of PACER-MAP.md must agree exactly (order may differ).
      it('matches the independent generator (tools/looper-preset.mjs) byte for byte', () => {
        const mine = encodePreset(preset, LOOPER_DEFAULT_SLOT);
        const theirs = splitSysex(fixture(`bitwig-looper-${mode}-D1.syx`));
        expect(mine).toHaveLength(189);
        expect(theirs).toHaveLength(189);

        const summarise = (list: Uint8Array[]) => list.map((m) => `${describeMidiMessage(m).summary} [${toHex(m)}]`).sort();
        expect(summarise(mine)).toEqual(summarise(theirs));
      });
    });
  }
});
