import { describe, expect, it } from 'vitest';
import { isBackupSaved, type SessionBackup } from '../src/store/device';

const backup = (downloaded: boolean, complete: boolean): SessionBackup => ({
  bytes: new Uint8Array(0),
  messages: 0,
  time: 0,
  downloaded,
  complete,
});

describe('the backup gate before the first write', () => {
  it('opens only for a complete backup that was saved', () => {
    expect(isBackupSaved(null)).toBe(false);
    expect(isBackupSaved(backup(false, true))).toBe(false);
    expect(isBackupSaved(backup(true, true))).toBe(true);
  });

  it('stays shut for an incomplete backup, even once it is downloaded', () => {
    // A read that needed repairs keeps only the verified presets: it is no undo for the ones that are missing
    expect(isBackupSaved(backup(true, false))).toBe(false);
  });
});
