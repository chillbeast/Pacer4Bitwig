import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import {
  GLOBAL_MESSAGE_COUNT,
  GLOBAL_OBJ,
  activeGlobalChannel,
  concatMessages,
  encodeGlobalMessage,
  encodeGlobals,
  getGlobalValue,
  globalWriteParts,
  globalsEqual,
  mergeGlobals,
  messageChecksumValid,
  parseDump,
  parseGlobalDump,
  parseGlobalMessages,
  readCurrentState,
  requestGlobals,
  setGlobalValue,
  unknownElements,
} from '../src/pacer';
import { bytes, fixture, hex, userBackupPath } from './helpers';

describe('global settings (target 0x05)', () => {
  const file = fixture('GLOBAL.factory.syx');
  const globals = parseGlobalDump(file);

  it('parses the 37 global messages and re-encodes them byte for byte', () => {
    expect(globals.messages).toHaveLength(GLOBAL_MESSAGE_COUNT);
    expect(hex(concatMessages(encodeGlobals(globals)))).toBe(hex(file));
  });

  it('finds the same globals inside the full factory backup', () => {
    const fromFull = parseGlobalMessages(parseDump(fixture('ALL.factory.bin')).globals);
    expect(globalsEqual(fromFull, globals)).toBe(true);
  });

  it('decodes the documented config elements', () => {
    for (const index of [1, 2, 3, 4]) {
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.SETTINGS, 0x01)).toBe(1); // channel
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.SETTINGS, 0x09)).toBe(0); // MIDI source
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.SETTINGS, 0x30)).toBe(0); // patch up/down
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.SETTINGS, 0x62)).toBe(4); // dim level
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.RELAY_MODE, 0x03)).toBe(1);
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.ENCODER, 0x02)).toBe(0x17); // preset select
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.ENCODER, 0x04)).toBe(3); // A1
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.ENCODER, 0x05)).toBe(26); // D6
      expect(getGlobalValue(globals, index, GLOBAL_OBJ.EXPRESSION, 0x02)).toBe(1);
    }
    expect(unknownElements(globals, 1)).toEqual([{ obj: 0x01, elm: 0x63, value: 1 }]);
  });

  it('decodes the current state (idx 0)', () => {
    const state = readCurrentState(globals)!;
    expect(state.activePreset).toBe(0);
    expect(state.currentUserPreset).toBe(0);
    expect(state.currentGlobalConfig).toBe(1);
    expect(state.channels).toHaveLength(16);
    expect(state.channels[0]).toEqual({ channel: 1, program: 0, bankA: 0, bankB: 0 });
    expect(activeGlobalChannel(globals)).toBe(1);
  });

  it('parses a current-state message from the reference notes (another device)', () => {
    const msg = bytes('F0 00 01 77 7F 01 05 00 01 32 01 00 00 42 01 00 00 52 01 01 2F F7');
    expect(messageChecksumValid(msg)).toBe(true);
    expect(readCurrentState(parseGlobalMessages([msg]))!.channels[0].bankB).toBe(1);
  });

  it('encodes an edited config message in the device shape', () => {
    const edited = setGlobalValue(globals, 1, GLOBAL_OBJ.SETTINGS, 0x62, 6);
    const parts = globalWriteParts(globals, edited);
    expect(parts).toHaveLength(1);
    expect(hex(parts[0].bytes)).toBe('F0 00 01 77 7F 01 05 01 01 01 01 01 00 09 01 00 00 30 01 00 00 62 01 06 00 63 01 01 6C F7');
    expect(globalsEqual(globals, edited)).toBe(false);
    expect(getGlobalValue(globals, 1, GLOBAL_OBJ.SETTINGS, 0x62)).toBe(4); // original untouched
  });

  it('never writes the current state and writes every config when the device is unknown', () => {
    expect(() => setGlobalValue(globals, 0, GLOBAL_OBJ.SETTINGS, 0x31, 2)).toThrow(/read-only/);
    expect(() => setGlobalValue(globals, 1, GLOBAL_OBJ.SETTINGS, 0x55, 2)).toThrow();
    const all = globalWriteParts(null, globals);
    expect(all).toHaveLength(20);
    expect(all.every((p) => p.message.index >= 1 && p.message.index <= 4)).toBe(true);
    expect(globalWriteParts(globals, globals)).toEqual([]);
  });

  it('merges partial replies', () => {
    const one = setGlobalValue(globals, 2, GLOBAL_OBJ.RELAY_MODE, 0x01, 3);
    const partial = parseGlobalMessages([encodeGlobalMessage(one.messages.find((m) => m.index === 2 && m.obj === 0x25)!)]);
    const merged = mergeGlobals(globals, partial);
    expect(merged.messages).toHaveLength(37);
    expect(getGlobalValue(merged, 2, GLOBAL_OBJ.RELAY_MODE, 0x01)).toBe(3);
  });

  it('builds the global GET request', () => {
    expect(hex(requestGlobals())).toBe('F0 00 01 77 7F 02 05 00 79 F7');
  });

  const backup = userBackupPath();
  it.skipIf(!backup)("round-trips the globals of the user's real backup", () => {
    const data = new Uint8Array(readFileSync(backup!));
    const g = parseGlobalMessages(parseDump(data).globals);
    expect(g.messages).toHaveLength(37);
    expect(hex(concatMessages(encodeGlobals(g)))).toBe(hex(concatMessages(parseDump(data).globals)));
  });
});
