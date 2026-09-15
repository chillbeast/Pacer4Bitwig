export const MAX_IMPORT_BYTES = 4 * 1024 * 1024;

function download(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function downloadBytes(bytes: Uint8Array, filename: string, mime = 'application/octet-stream'): void {
  download(new Blob([new Uint8Array(bytes)], { type: mime }), filename);
}

export function downloadText(text: string, filename: string, mime = 'application/json'): void {
  download(new Blob([text], { type: mime }), filename);
}

/** 2026-09-14_2012 */
export function timestamp(date = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}_${p(date.getHours())}${p(date.getMinutes())}`;
}

export function safeFilePart(text: string): string {
  return text.trim().replace(/[^A-Za-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '') || 'preset';
}

export async function readFileBytes(file: File): Promise<Uint8Array> {
  return new Uint8Array(await file.arrayBuffer());
}

/** Open the browser file picker. */
export function pickFiles(accept: string, multiple = true): Promise<File[]> {
  return new Promise((resolve) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.multiple = multiple;
    input.addEventListener('change', () => resolve(input.files ? Array.from(input.files) : []), { once: true });
    input.addEventListener('cancel', () => resolve([]), { once: true });
    input.click();
  });
}
