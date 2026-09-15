import { describe, expect, it } from 'vitest';
import {
  describeMidiMessage,
  encodeLed,
  encodeName,
  encodeStep,
  MSG,
  noteName,
  requestFullBackup,
  requestPreset,
  stepSummary,
} from '../src/pacer';

describe('stepSummary', () => {
  it('summarises common message types', () => {
    expect(stepSummary({ channel: 16, msgType: MSG.SW_CC_TRIGGER, data: [102, 127, 0] })).toBe('CC 102 · ch16 · 127/0');
    expect(stepSummary({ channel: 0, msgType: MSG.SW_CC_TOGGLE, data: [49, 127, 0] })).toBe('CC 49 · global · 127⇄0');
    expect(stepSummary({ channel: 1, msgType: MSG.SW_NOTE, data: [60, 100, 0] })).toBe('C4 · ch1 · vel 100');
    expect(stepSummary({ channel: 2, msgType: MSG.SW_PROGRAM_BANK, data: [5, 0, 0] })).toBe('PC 5 · ch2');
    expect(stepSummary({ channel: 16, msgType: MSG.AD_CC, data: [116, 0, 127] })).toBe('CC 116 · ch16 · 0–127');
    expect(stepSummary({ channel: 0, msgType: MSG.SW_STEP_INC_DEC, data: [15, 0, 0] })).toBe('EXP1 step +');
    expect(stepSummary({ channel: 0, msgType: MSG.SW_PRESET_SELECT, data: [1, 0, 0] })).toBe('Preset → Track (DAW)');
    expect(stepSummary({ channel: 0, msgType: MSG.OFF, data: [0, 127, 0] })).toBe('Off');
  });

  it('names notes with middle C = C4', () => {
    expect(noteName(60)).toBe('C4');
    expect(noteName(0)).toBe('C-1');
    expect(noteName(52)).toBe('E3');
    expect(noteName(127)).toBe('G9');
  });
});

describe('describeMidiMessage', () => {
  it('decodes channel messages', () => {
    expect(describeMidiMessage(Uint8Array.of(0xbf, 102, 127)).summary).toBe('CC 102 = 127 · ch16');
    expect(describeMidiMessage(Uint8Array.of(0x90, 60, 0)).summary).toBe('Note off C4 (60) · ch1');
    expect(describeMidiMessage(Uint8Array.of(0xf8)).kind).toBe('realtime');
  });

  it('decodes Pacer SysEx', () => {
    expect(describeMidiMessage(requestPreset(19)).summary).toBe('GET preset D1 (all)');
    expect(describeMidiMessage(requestFullBackup()).summary).toBe('GET full backup');
    expect(describeMidiMessage(encodeName('LOOPS', 19)).summary).toBe('D1 · name "LOOPS"');
    const step = encodeStep('SW1', 0, { channel: 16, msgType: MSG.SW_CC_TRIGGER, data: [102, 127, 0], active: true }, 19);
    expect(describeMidiMessage(step).summary).toBe('D1 · Switch 1 · step 1: CC Trigger CC 102 · ch16 · 127/0');
    const led = encodeLed('SWD', 5, { midiCtrl: true, onColor: 0x15, offColor: 0, num: 3 }, 19);
    expect(describeMidiMessage(led).summary).toBe('D1 · Switch D · LED 6: on Purple, off Off, Top, MIDI ctrl');
  });

  it('flags a bad checksum', () => {
    const msg = encodeName('LOOPS', 19);
    msg[msg.length - 2] ^= 1;
    expect(describeMidiMessage(msg).badChecksum).toBe(true);
  });

  it('decodes universal identity and MMC messages', () => {
    expect(describeMidiMessage(Uint8Array.of(0xf0, 0x7e, 0x7f, 0x06, 0x01, 0xf7)).summary).toBe('Identity request');
    expect(describeMidiMessage(Uint8Array.of(0xf0, 0x7e, 0x7f, 0x06, 0x02, 0x00, 0x01, 0x77, 0xf7)).summary).toBe(
      'Identity reply · 00 01 77',
    );
    expect(describeMidiMessage(Uint8Array.of(0xf0, 0x7f, 0x7f, 0x06, 0x05, 0xf7)).summary).toBe('MMC Rewind · device 127');
  });
});
