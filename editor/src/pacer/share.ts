/**
 * Share links: a preset packed into bytes, deflated, base64url-encoded and put into the URL hash.
 *
 * Payload: "PS" v1 slot | name ×5 | controls (mode, 6 steps × 6 bytes, 6 LEDs × 4 bytes when present) |
 *          16 on-load settings × 5 bytes | checksum | labels length (2 × 7 bits) | labels JSON (UTF-8)
 */
import { CONTROLS, CONTROL_KEYS, PRESET_MIDI_COUNT, PRESET_NAME_LENGTH, STEP_COUNT, type ControlKey } from './constants';
import { checksum } from './checksum';
import type { ControlLabels } from './json';
import { createPreset, normalizeName, type Preset } from './model';

export const SHARE_HASH_KEY = 'preset';
const MAGIC = [0x50, 0x53];
const VERSION = 1;
const NO_SLOT = 127;
const MAX_TOKEN_LENGTH = 12000;
const MAX_PAYLOAD_BYTES = 16 * 1024;
const MAX_LABEL_LENGTH = 24;

export class ShareLinkError extends Error {
  override name = 'ShareLinkError';
}

export interface SharedPreset {
  preset: Preset;
  labels: ControlLabels;
  /** Slot the preset was shared from (0..24), or null. */
  slot: number | null;
}

export function packPreset(shared: SharedPreset): Uint8Array {
  const body: number[] = [];
  const name = normalizeName(shared.preset.name);
  for (let i = 0; i < PRESET_NAME_LENGTH; i++) body.push(name.charCodeAt(i));
  for (const def of CONTROLS) {
    const c = shared.preset.controls[def.key];
    body.push(c.mode);
    for (const s of c.steps) body.push(s.channel, s.msgType, s.data[0], s.data[1], s.data[2], s.active ? 1 : 0);
    if (def.hasLeds && c.leds) for (const l of c.leds) body.push(l.midiCtrl ? 1 : 0, l.onColor, l.offColor, l.num);
  }
  for (const m of shared.preset.midi) body.push(m.channel, m.msgType, m.data[0], m.data[1], m.data[2]);
  if (body.some((v) => !Number.isInteger(v) || v < 0 || v > 127)) throw new ShareLinkError('Preset contains invalid values');

  const labels = new TextEncoder().encode(Object.keys(shared.labels).length > 0 ? JSON.stringify(shared.labels) : '');
  if (labels.length > 16383) throw new ShareLinkError('Labels too long');
  const slot = shared.slot !== null && shared.slot >= 0 && shared.slot <= 24 ? shared.slot : NO_SLOT;
  const header = [...MAGIC, VERSION, slot];
  const out = new Uint8Array(header.length + body.length + 1 + 2 + labels.length);
  out.set(header, 0);
  out.set(body, header.length);
  let p = header.length + body.length;
  out[p++] = checksum(body);
  out[p++] = (labels.length >> 7) & 0x7f;
  out[p++] = labels.length & 0x7f;
  out.set(labels, p);
  return out;
}

const PRESET_BYTES =
  PRESET_NAME_LENGTH +
  CONTROLS.reduce((n, c) => n + 1 + STEP_COUNT * 6 + (c.hasLeds ? STEP_COUNT * 4 : 0), 0) +
  PRESET_MIDI_COUNT * 5;

export function unpackPreset(bytes: Uint8Array): SharedPreset {
  const fail = (why: string): never => {
    throw new ShareLinkError(`Invalid share link: ${why}`);
  };
  const minLength = 4 + PRESET_BYTES + 3;
  if (bytes.length < minLength) fail('too short');
  if (bytes[0] !== MAGIC[0] || bytes[1] !== MAGIC[1]) fail('not a Pacer Studio preset');
  if (bytes[2] !== VERSION) fail(`unsupported version ${bytes[2]}`);
  const slot = bytes[3] === NO_SLOT ? null : bytes[3] <= 24 ? bytes[3] : fail('bad slot');

  const body = bytes.subarray(4, 4 + PRESET_BYTES);
  if (body.some((v) => v > 127)) fail('value out of range');
  if (checksum(body) !== bytes[4 + PRESET_BYTES]) fail('checksum mismatch');

  let p = 0;
  const next = () => body[p++];
  const flag = () => {
    const v = next();
    return v === 0 ? false : v === 1 ? true : fail('bad flag');
  };
  const channel = () => {
    const v = next();
    return v <= 16 ? v : fail('bad channel');
  };

  let name = '';
  for (let i = 0; i < PRESET_NAME_LENGTH; i++) {
    const c = next();
    if (c < 0x20 || c > 0x7e) fail('bad name');
    name += String.fromCharCode(c);
  }
  const preset = createPreset(name);
  for (const def of CONTROLS) {
    const c = preset.controls[def.key];
    c.mode = next();
    if (c.mode > 2) fail('bad control mode');
    for (const s of c.steps) {
      s.channel = channel();
      s.msgType = next();
      s.data = [next(), next(), next()];
      s.active = flag();
    }
    if (def.hasLeds && c.leds) {
      for (const l of c.leds) {
        l.midiCtrl = flag();
        l.onColor = next();
        l.offColor = next();
        l.num = next();
        if (l.num > 3) fail('bad LED number');
      }
    }
  }
  for (const m of preset.midi) {
    m.channel = channel();
    m.msgType = next();
    m.data = [next(), next(), next()];
  }

  const q = 4 + PRESET_BYTES + 1;
  const hi = bytes[q];
  const lo = bytes[q + 1];
  if (hi > 127 || lo > 127) fail('bad labels length');
  const labelLength = (hi << 7) | lo;
  if (q + 2 + labelLength !== bytes.length) fail('length mismatch');
  const labels: ControlLabels = {};
  if (labelLength > 0) {
    let parsed: unknown;
    try {
      parsed = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(q + 2)));
    } catch {
      fail('bad labels');
    }
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) fail('bad labels');
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      if (!CONTROL_KEYS.includes(key as ControlKey) || typeof value !== 'string' || value.length > MAX_LABEL_LENGTH) {
        fail('bad labels');
      }
      labels[key as ControlKey] = value as string;
    }
  }
  return { preset, labels, slot };
}

// ---------------------------------------------------------------------------------------------
// base64url + compression
// ---------------------------------------------------------------------------------------------

export function toBase64Url(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function fromBase64Url(text: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]*$/.test(text)) throw new ShareLinkError('Invalid share link: bad characters');
  const padded = text.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (text.length % 4)) % 4);
  let binary: string;
  try {
    binary = atob(padded);
  } catch {
    throw new ShareLinkError('Invalid share link: bad encoding');
  }
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

const canCompress = () => typeof CompressionStream === 'function' && typeof DecompressionStream === 'function';

async function transform(bytes: Uint8Array, stream: CompressionStream | DecompressionStream, limit: number): Promise<Uint8Array> {
  const writer = stream.writable.getWriter();
  void writer.write(new Uint8Array(bytes)).catch(() => {});
  void writer.close().catch(() => {});
  const reader = stream.readable.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.length;
    if (total > limit) {
      await reader.cancel().catch(() => {});
      throw new ShareLinkError('Invalid share link: too large');
    }
    chunks.push(value);
  }
  const out = new Uint8Array(total);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

/** "z" + base64url(deflate(payload)) when compression is available, else "r" + base64url(payload). */
export async function encodeShareToken(shared: SharedPreset): Promise<string> {
  const payload = packPreset(shared);
  if (canCompress()) {
    const compressed = await transform(payload, new CompressionStream('deflate'), MAX_PAYLOAD_BYTES);
    return `z${toBase64Url(compressed)}`;
  }
  return `r${toBase64Url(payload)}`;
}

export async function decodeShareToken(token: string): Promise<SharedPreset> {
  if (token.length < 2 || token.length > MAX_TOKEN_LENGTH) throw new ShareLinkError('Invalid share link: bad length');
  const kind = token[0];
  const data = fromBase64Url(token.slice(1));
  if (kind === 'r') return unpackPreset(data);
  if (kind !== 'z') throw new ShareLinkError('Invalid share link: unknown format');
  if (!canCompress()) throw new ShareLinkError('This browser cannot open compressed share links');
  let payload: Uint8Array;
  try {
    payload = await transform(data, new DecompressionStream('deflate'), MAX_PAYLOAD_BYTES);
  } catch (err) {
    if (err instanceof ShareLinkError) throw err;
    throw new ShareLinkError('Invalid share link: corrupted data');
  }
  return unpackPreset(payload);
}

/** Token from a location hash like "#preset=z…", or null. */
export function shareTokenFromHash(hash: string): string | null {
  const text = hash.startsWith('#') ? hash.slice(1) : hash;
  for (const part of text.split('&')) {
    const [key, value] = part.split('=');
    if (key === SHARE_HASH_KEY && value) return value;
  }
  return null;
}

export function shareUrl(baseUrl: string, token: string): string {
  return `${baseUrl.split('#')[0]}#${SHARE_HASH_KEY}=${token}`;
}
