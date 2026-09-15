import { describe, expect, it } from 'vitest';
import {
  CONTROLS,
  MSG,
  allowedMsgTypes,
  concatMessages,
  encodePreset,
  messageChecksumValid,
  parseDump,
} from '../src/pacer';
import { TEMPLATES } from '../src/templates';
import { LOOPER_TAP_HOLD, buildBitwigLooperPreset, isLooperLayout } from '../src/templates/bitwigLooper';
import { MMC, buildCcTogglePedalboard, buildMmcTransport, buildProgramPedalboard } from '../src/templates/pedalboards';
import { fixture } from './helpers';

describe('template registry', () => {
  for (const t of TEMPLATES) {
    for (const choice of t.choices?.options.map((o) => o.value) ?? [undefined]) {
      it(`${t.name}${choice ? ` (${choice})` : ''} encodes to a valid preset`, () => {
        const { preset, labels } = t.build(choice);
        const messages = encodePreset(preset, 3);
        expect(messages).toHaveLength(189);
        expect(messages.every(messageChecksumValid)).toBe(true);
        expect(parseDump(concatMessages(messages)).presets.get(3)!.preset).toEqual(preset);
        for (const def of CONTROLS) {
          for (const step of preset.controls[def.key].steps) {
            expect(allowedMsgTypes(def.kind)).toContain(step.msgType);
          }
        }
        for (const label of Object.values(labels)) expect(label!.length).toBeLessThanOrEqual(12);
      });
    }
  }
});

describe('pedalboard templates', () => {
  it('CC toggle: channel 1, CC 20–33, bright on / dim off colours', () => {
    const { preset } = buildCcTogglePedalboard();
    const switches = CONTROLS.filter((c) => c.kind === 'stompswitch');
    switches.forEach((def, i) => {
      const c = preset.controls[def.key];
      expect(c.steps[0]).toEqual({ channel: 1, msgType: MSG.SW_CC_TOGGLE, data: [20 + i, 127, 0], active: true });
      expect(c.leds![0].offColor).toBe(c.leds![0].onColor + 1);
    });
    expect(preset.controls.FS4.steps[0].data[0]).toBe(33);
    expect(preset.controls.EXP1.steps[0]).toEqual({ channel: 1, msgType: MSG.AD_CC, data: [11, 0, 127], active: true });
  });

  it('Program change: programs 0–9 like the factory PRGM presets, FS program step', () => {
    const { preset } = buildProgramPedalboard();
    const factory = parseDump(fixture('A1.factory.syx')).presets.get(1)!.preset;
    expect(preset.controls.SW3.steps[0].msgType).toBe(factory.controls.SW3.steps[0].msgType);
    expect(preset.controls.SW3.steps[0].data).toEqual(factory.controls.SW3.steps[0].data);
    expect(preset.controls.SWD.steps[0].data).toEqual([9, 0, 0]);
    expect(preset.controls.FS1.steps[0].data).toEqual(factory.controls.FS1.steps[0].data);
    expect(preset.controls.FS2.steps[0].data).toEqual(factory.controls.FS2.steps[0].data);
  });

  it('MMC transport uses the MMC command codes of the factory "MMC" preset', () => {
    const { preset } = buildMmcTransport();
    const factory = parseDump(fixture('ALL.factory.bin')).presets.get(23)!.preset; // "MMC  "
    const factoryCommands = ['SW2', 'SW3', 'SW4', 'SW5', 'SW6'].map((k) => factory.controls[k as 'SW2'].steps[0].data[1]);
    expect(factoryCommands).toEqual([MMC.rewind, MMC.fastForward, MMC.stop, MMC.play, MMC.recordStrobe]);
    expect(MMC.pause).toBe(9);
    const commands = ['SW1', 'SW2', 'SW3', 'SW4', 'SW5', 'SW6'].map((k) => preset.controls[k as 'SW1'].steps[0]);
    expect(commands.map((s) => s.data[1])).toEqual([5, 4, 1, 2, 6, 9]);
    expect(commands.every((s) => s.msgType === MSG.SW_MMC && s.data[0] === 127)).toBe(true);
    expect(preset.controls.SWC.steps[0]).toMatchObject({ msgType: MSG.SW_PRESET_INC_DEC, data: [1, 0, 0] });
  });
});

describe('looper roles', () => {
  it('recognises the looper layout regardless of LED strategy', () => {
    expect(isLooperLayout(buildBitwigLooperPreset('two-colour'))).toBe(true);
    expect(isLooperLayout(buildBitwigLooperPreset('multi-colour'))).toBe(true);
    expect(isLooperLayout(buildCcTogglePedalboard().preset)).toBe(false);
    expect(isLooperLayout(null)).toBe(false);
  });

  it('uses the LOOPER.md default actions', () => {
    expect(LOOPER_TAP_HOLD.SW6).toEqual({ tap: 'Play/stop all', hold: 'Clear row' });
    expect(LOOPER_TAP_HOLD.FS1).toEqual({ tap: 'One-button looper', hold: 'Clear last' });
    expect(LOOPER_TAP_HOLD.EXP2.tap).toBe('Master volume');
  });
});
