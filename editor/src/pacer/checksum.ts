import { CMD_OFFSET, SYSEX_END, SYSEX_START } from './constants';

/**
 * Pacer checksum: `(128 - sum % 128) % 128` over the bytes from the command byte
 * to the last data byte (header and F0/F7 excluded).
 */
export function checksum(bytes: ArrayLike<number>, start = 0, end = bytes.length): number {
  let sum = 0;
  for (let i = start; i < end; i++) sum += bytes[i];
  return (128 - (sum % 128)) % 128;
}

/** Validate the checksum of a complete Pacer message (F0 00 01 77 7F cmd ... cs F7). */
export function messageChecksumValid(msg: ArrayLike<number>): boolean {
  const len = msg.length;
  if (len < CMD_OFFSET + 3) return false;
  if (msg[0] !== SYSEX_START || msg[len - 1] !== SYSEX_END) return false;
  return checksum(msg, CMD_OFFSET, len - 2) === msg[len - 2];
}
