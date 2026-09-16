import { describe, expect, it } from 'vitest';
import { MSG, describeMidiMessage, encodePreset, splitSysex, toHex } from '../src/pacer';
import {
  LOOPER_ACTION_CC,
  LOOPER_DEFAULT_SLOT,
  LOOPER_FOOTSWITCHES,
  LOOPER_MODE_SWITCH_INDEX,
  LOOPER_PRESET_LOADED_VALUE,
  LOOPER_SWITCHES,
  buildBitwigLooperPreset,
  colourSlotCc,
} from '../src/templates/bitwigLooper';
import { fixture } from './helpers';

// Two independent implementations of PACER-MAP.md must agree exactly (order may differ).
function expectSameMessages(mine: Uint8Array[], theirs: Uint8Array[]): void {
  expect(mine).toHaveLength(189);
  expect(theirs).toHaveLength(189);
  const summarise = (list: Uint8Array[]) => list.map((m) => `${describeMidiMessage(m).summary} [${toHex(m)}]`).sort();
  expect(summarise(mine)).toEqual(summarise(theirs));
}

describe('Bitwig Pacer template (docs/PACER-MAP.md)', () => {
  const preset = buildBitwigLooperPreset();

  it('targets D1 by default', () => {
    expect(LOOPER_DEFAULT_SLOT).toBe(0x13);
  });

  it('names the preset PACER and uses "all steps at once"', () => {
    // The extension replaces the name with the active mode's name as soon as it starts
    expect(preset.name).toBe('PACER');
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
    expect(preset.midi[0]).toEqual({ channel: 16, msgType: MSG.LOAD_CC, data: [119, LOOPER_PRESET_LOADED_VALUE, 0] });
    expect(preset.midi.slice(1).every((m) => m.msgType === MSG.OFF)).toBe(true);
  });

  it('uses only step 1, so steps 2-6 send nothing', () => {
    LOOPER_SWITCHES.forEach((key) => {
      const control = preset.controls[key];
      expect(control.steps.slice(1).every((st) => !st.active && st.msgType === MSG.OFF)).toBe(true);
      expect(control.leds!.slice(1).every((l) => !l.midiCtrl)).toBe(true);
    });
  });

  it('leaves the LEDs to the Pacer until the extension takes them over', () => {
    // LED MIDI control off means the board still lights up without Bitwig; the extension turns it on per switch
    LOOPER_SWITCHES.forEach((key, s) => {
      const led = preset.controls[key].leds![0];
      expect(led.midiCtrl).toBe(false);
      expect(led.onColor).toBe(s === LOOPER_MODE_SWITCH_INDEX ? 0x17 : 0x03);
      expect(led.num).toBe(0);
    });
  });

  it('matches the independent generator (tools/pacer-preset.mjs) byte for byte', () => {
    expectSameMessages(encodePreset(preset, LOOPER_DEFAULT_SLOT), splitSysex(fixture('bitwig-pacer-D1.syx')));
  });

  it('still exposes the LED Lab colour-slot CCs, which are not part of the contract', () => {
    const ranges = LOOPER_SWITCHES.map((_, s) => [colourSlotCc(s, 2), colourSlotCc(s, 6)]);
    expect(ranges).toEqual([
      [20, 24], [25, 29], [30, 34], [35, 39], [40, 44],
      [45, 49], [50, 54], [55, 59], [60, 64], [65, 69],
    ]);
  });
});
