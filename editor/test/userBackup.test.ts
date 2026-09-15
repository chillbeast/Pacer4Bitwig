import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { encodeDump, messageChecksumValid, parseDump, splitSysex } from '../src/pacer';
import { hex, userBackupPath } from './helpers';

// Uses the newest real backup in ../../backups (created by tools/pacer-backup.mjs) when present.
// The file is only read, never modified.
const path = userBackupPath();

describe.skipIf(!path)('round trip over the user\'s real Pacer backup', () => {
  it('parses a complete full backup and re-encodes it byte for byte', () => {
    const data = new Uint8Array(readFileSync(path!));
    const messages = splitSysex(data);
    expect(messages.every(messageChecksumValid)).toBe(true);
    const result = parseDump(data);
    expect(result.badChecksums).toBe(0);
    expect(result.presets.size).toBe(25);
    expect([...result.presets.values()].every((p) => p.complete)).toBe(true);
    const encoded = encodeDump(
      [...result.presets].map(([i, p]) => [i, p.preset] as const),
      result.globals,
    );
    expect(hex(encoded)).toBe(hex(data));
  });
});
