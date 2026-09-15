import { describe, expect, it } from 'vitest';
import { IDENTITY_REQUEST, describeIdentity, isIdentityReply, parseIdentityReply } from '../src/midi';
import { bytes, hex } from './helpers';

describe('Universal Identity', () => {
  it('builds the request', () => {
    expect(hex(IDENTITY_REQUEST)).toBe('F0 7E 7F 06 01 F7');
  });

  it('parses a full reply with a 3-byte manufacturer id', () => {
    const id = parseIdentityReply(bytes('F0 7E 7F 06 02 00 01 77 12 34 56 78 01 02 03 04 F7'))!;
    expect(id.isNektar).toBe(true);
    expect(id.manufacturerName).toBe('Nektar Technology');
    expect(id.family).toBe(0x12 | (0x34 << 7));
    expect(id.model).toBe(0x56 | (0x78 << 7));
    expect(id.version).toEqual([1, 2, 3, 4]);
    expect(id.versionText).toBe('1.2.3.4');
    expect(describeIdentity(id)).toMatch(/firmware 1\.2\.3\.4/);
  });

  it('tolerates short replies and one-byte manufacturers', () => {
    const id = parseIdentityReply(bytes('F0 7E 00 06 02 41 F7'))!;
    expect(id.manufacturer).toEqual([0x41]);
    expect(id.isNektar).toBe(false);
    expect(id.versionText).toBe('unknown');
  });

  it('rejects other messages', () => {
    expect(isIdentityReply(IDENTITY_REQUEST)).toBe(false);
    expect(parseIdentityReply(bytes('F0 00 01 77 7F 02 7F 7F F7'))).toBeNull();
    expect(parseIdentityReply(bytes('F0 7E 7F 06 02 00 01 F7'))).toBeNull();
  });
});
