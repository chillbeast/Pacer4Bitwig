import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export function fixturePath(name: string): string {
  return fileURLToPath(new URL(`./fixtures/${name}`, import.meta.url));
}

export function fixture(name: string): Uint8Array {
  return new Uint8Array(readFileSync(fixturePath(name)));
}

/** Newest full backup of the user's Pacer in ../../backups, if present (never modified). */
export function userBackupPath(): string | null {
  const dir = fileURLToPath(new URL('../../backups/', import.meta.url));
  if (!existsSync(dir)) return null;
  const files = readdirSync(dir)
    .filter((f) => /^pacer-full-.*\.syx$/i.test(f))
    .sort();
  return files.length > 0 ? `${dir}${files[files.length - 1]}` : null;
}

export function hex(bytes: ArrayLike<number>): string {
  return Array.from(bytes as ArrayLike<number>, (b) => b.toString(16).toUpperCase().padStart(2, '0')).join(' ');
}

export function bytes(text: string): Uint8Array {
  return Uint8Array.from(text.trim().split(/\s+/), (h) => parseInt(h, 16));
}
