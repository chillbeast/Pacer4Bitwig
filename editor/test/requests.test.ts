import { describe, expect, it } from 'vitest';
import { isGetRequest, requestAllPresets, requestControl, requestFullBackup, requestPreset } from '../src/pacer';
import { hex } from './helpers';

describe('request builders', () => {
  it('builds preset requests as documented', () => {
    expect(hex(requestPreset(0x07))).toBe('F0 00 01 77 7F 02 01 07 7F 77 F7');
    expect(hex(requestPreset(0x17))).toBe('F0 00 01 77 7F 02 01 17 7F 67 F7');
    expect(hex(requestPreset(0x14))).toBe('F0 00 01 77 7F 02 01 14 7F 6A F7');
  });

  it('builds control, all-presets and full backup requests', () => {
    expect(hex(requestControl(0x14, 'SW1'))).toBe('F0 00 01 77 7F 02 01 14 0D 5C F7');
    expect(hex(requestAllPresets())).toBe('F0 00 01 77 7F 02 01 7F 7E F7');
    expect(hex(requestFullBackup())).toBe('F0 00 01 77 7F 02 7F 7F F7');
  });

  it('only ever builds GET messages', () => {
    for (const msg of [requestPreset(1), requestControl(1, 'EXP1'), requestAllPresets(), requestFullBackup()]) {
      expect(isGetRequest(msg)).toBe(true);
    }
  });
});
