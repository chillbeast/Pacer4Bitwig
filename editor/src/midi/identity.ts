/** MIDI Universal Non-Realtime Identity Request, sent to "all devices" (7F). */
export const IDENTITY_REQUEST = Uint8Array.of(0xf0, 0x7e, 0x7f, 0x06, 0x01, 0xf7);

export interface DeviceIdentity {
  deviceId: number;
  manufacturer: number[];
  manufacturerName: string;
  isNektar: boolean;
  /** 14-bit values (LSB first on the wire). */
  family: number;
  model: number;
  /** Raw software revision bytes (usually 4). */
  version: number[];
  versionText: string;
  raw: Uint8Array;
}

const MANUFACTURERS: Record<string, string> = {
  '00 01 77': 'Nektar Technology',
};

export function isIdentityReply(msg: ArrayLike<number>): boolean {
  return (
    msg.length >= 6 &&
    msg[0] === 0xf0 &&
    msg[1] === 0x7e &&
    msg[3] === 0x06 &&
    msg[4] === 0x02 &&
    msg[msg.length - 1] === 0xf7
  );
}

/**
 * `F0 7E <device> 06 02 <manufacturer 1 or 3 bytes> <family LSB MSB> <model LSB MSB> <version ×4> F7`.
 * Tolerates short replies (missing family/model/version bytes are left out).
 */
export function parseIdentityReply(msg: Uint8Array): DeviceIdentity | null {
  if (!isIdentityReply(msg)) return null;
  const end = msg.length - 1;
  let p = 5;
  if (p >= end) return null;
  const manufacturer = msg[p] === 0x00 ? Array.from(msg.subarray(p, p + 3)) : [msg[p]];
  if (manufacturer.length === 3 && p + 3 > end) return null;
  p += manufacturer.length;
  const word = (): number | undefined => {
    if (p + 2 > end) return undefined;
    const v = msg[p] | (msg[p + 1] << 7);
    p += 2;
    return v;
  };
  const family = word() ?? 0;
  const model = word() ?? 0;
  const version = Array.from(msg.subarray(p, end));
  const key = manufacturer.map((b) => b.toString(16).padStart(2, '0')).join(' ');
  return {
    deviceId: msg[2],
    manufacturer,
    manufacturerName: MANUFACTURERS[key] ?? `Manufacturer ${key.toUpperCase()}`,
    isNektar: key === '00 01 77',
    family,
    model,
    version,
    versionText: version.length > 0 ? version.join('.') : 'unknown',
    raw: msg,
  };
}

export function describeIdentity(id: DeviceIdentity): string {
  const hex = (n: number) => `0x${n.toString(16).toUpperCase().padStart(4, '0')}`;
  return `${id.manufacturerName} · family ${hex(id.family)} · model ${hex(id.model)} · firmware ${id.versionText}`;
}
