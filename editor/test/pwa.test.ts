import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { SW_FILE, buildServiceWorker } from '../build/pwa';

const publicDir = fileURLToPath(new URL('../public/', import.meta.url));

describe('service worker generator', () => {
  const source = buildServiceWorker(
    ['index.html', 'assets/index-abc123.js', 'assets/index-abc123.js.map', 'assets/index-def.css', SW_FILE, 'icon-192.png'],
    'v42',
  );

  it('is valid JavaScript', () => {
    expect(() => new Function(source)).not.toThrow();
  });

  it('names the cache after the build version', () => {
    expect(source).toContain('const CACHE = "pacer-studio-v42"');
  });

  it('precaches the shell with relative URLs (works under any base path)', () => {
    const assets = JSON.parse(/const ASSETS = (\[[\s\S]*?\]);/.exec(source)![1]) as string[];
    expect(assets).toEqual(['./', './assets/index-abc123.js', './assets/index-def.css', './icon-192.png', './index.html']);
    expect(source).toContain('new URL(path, self.registration.scope)');
  });

  it('removes caches of older builds on activate', () => {
    expect(source).toMatch(/startsWith\('pacer-studio-'\) && key !== CACHE/);
  });
});

describe('web app manifest', () => {
  const manifest = JSON.parse(readFileSync(`${publicDir}manifest.webmanifest`, 'utf8')) as {
    start_url: string;
    scope: string;
    icons: { src: string; sizes: string }[];
  };

  it('uses relative URLs and existing icons', () => {
    expect(manifest.start_url).toBe('./');
    expect(manifest.scope).toBe('./');
    expect(manifest.icons.map((i) => i.sizes)).toEqual(expect.arrayContaining(['192x192', '512x512']));
    for (const icon of manifest.icons) expect(existsSync(`${publicDir}${icon.src}`)).toBe(true);
  });
});
