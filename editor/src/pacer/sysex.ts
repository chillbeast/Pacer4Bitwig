import { checksum } from './checksum';
import { CMD_OFFSET, DEVICE_BYTE, MANUFACTURER_ID, PACER_HEADER, SYSEX_END, SYSEX_START } from './constants';

/**
 * Extract complete SysEx messages (F0 ... F7, both included) from a byte buffer.
 * Bytes outside SysEx frames are skipped; an unterminated frame is dropped when a new F0 starts.
 */
export function splitSysex(bytes: ArrayLike<number>): Uint8Array[] {
  const out: Uint8Array[] = [];
  let start = -1;
  for (let i = 0; i < bytes.length; i++) {
    const b = bytes[i];
    if (b === SYSEX_START) {
      start = i;
    } else if (b === SYSEX_END && start >= 0) {
      const msg = new Uint8Array(i - start + 1);
      for (let k = start; k <= i; k++) msg[k - start] = bytes[k];
      out.push(msg);
      start = -1;
    }
  }
  return out;
}

export function isPacerMessage(msg: ArrayLike<number>): boolean {
  return (
    msg.length >= CMD_OFFSET + 2 &&
    msg[0] === SYSEX_START &&
    msg[1] === MANUFACTURER_ID[0] &&
    msg[2] === MANUFACTURER_ID[1] &&
    msg[3] === MANUFACTURER_ID[2] &&
    msg[4] === DEVICE_BYTE &&
    msg[msg.length - 1] === SYSEX_END
  );
}

/** Build a complete Pacer message from its body (command byte onwards); the checksum is appended. */
export function buildPacerMessage(body: readonly number[]): Uint8Array {
  const msg = new Uint8Array(PACER_HEADER.length + body.length + 2);
  msg.set(PACER_HEADER, 0);
  for (let i = 0; i < body.length; i++) {
    const v = body[i];
    if (!Number.isInteger(v) || v < 0 || v > 127) {
      throw new RangeError(`SysEx data byte out of range at ${i}: ${v}`);
    }
    msg[PACER_HEADER.length + i] = v;
  }
  msg[msg.length - 2] = checksum(body);
  msg[msg.length - 1] = SYSEX_END;
  return msg;
}

export function concatMessages(messages: readonly Uint8Array[]): Uint8Array {
  const total = messages.reduce((n, m) => n + m.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const m of messages) {
    out.set(m, offset);
    offset += m.length;
  }
  return out;
}

export function toHex(bytes: ArrayLike<number>, separator = ' '): string {
  const parts: string[] = [];
  for (let i = 0; i < bytes.length; i++) parts.push(bytes[i].toString(16).toUpperCase().padStart(2, '0'));
  return parts.join(separator);
}

export function fromHex(text: string): Uint8Array {
  const clean = text.replace(/0x/gi, '').replace(/[^0-9a-f]/gi, '');
  if (clean.length % 2 !== 0) throw new Error('Odd number of hex digits');
  const out = new Uint8Array(clean.length / 2);
  for (let i = 0; i < out.length; i++) out[i] = parseInt(clean.substr(i * 2, 2), 16);
  return out;
}

export interface PacerHeader {
  cmd: number;
  target: number;
  /** Undefined for messages that stop after the target (e.g. full backup request). */
  index: number | undefined;
  obj: number | undefined;
}

/** Read cmd/target/index/object of a Pacer message; null when not a Pacer message. */
export function readHeader(msg: ArrayLike<number>): PacerHeader | null {
  if (!isPacerMessage(msg)) return null;
  const payloadEnd = msg.length - 2; // checksum position
  const at = (offset: number) => (CMD_OFFSET + offset < payloadEnd ? msg[CMD_OFFSET + offset] : undefined);
  const cmd = at(0);
  const target = at(1);
  if (cmd === undefined || target === undefined) return null;
  return { cmd, target, index: at(2), obj: at(3) };
}

export interface SysexElement {
  elm: number;
  length: number;
  /** First value byte (all known elements are one byte long). */
  value: number;
}

/**
 * Read `elm, len, value[len]` records between `start` and `end` (exclusive).
 * A 0x00 byte between records is a separator (element ids start at 0x01).
 */
export function readElements(msg: ArrayLike<number>, start: number, end: number): SysexElement[] {
  const out: SysexElement[] = [];
  let p = start;
  while (p < end) {
    const elm = msg[p];
    if (elm === 0x00) {
      p++;
      continue;
    }
    if (p + 1 >= end) break;
    const length = msg[p + 1];
    if (length < 1 || p + 2 + length > end) break;
    out.push({ elm, length, value: msg[p + 2] });
    p += 2 + length;
  }
  return out;
}
