import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

// WCAG AA contrast of the design tokens (text pairs need 4.5:1, focus ring and non-text UI 3:1).
const css = readFileSync(fileURLToPath(new URL('../src/styles/tokens.css', import.meta.url)), 'utf8');

function block(selector: string): Record<string, string> {
  const start = css.indexOf('{', css.indexOf(selector));
  const body = css.slice(start + 1, css.indexOf('}', start));
  return Object.fromEntries([...body.matchAll(/--([\w-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]));
}

type Rgba = [number, number, number, number];

function color(value: string): Rgba {
  if (value.startsWith('#')) {
    const h = value.slice(1);
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16), 1];
  }
  const m = /rgba?\(([^)]+)\)/.exec(value);
  if (!m) throw new Error(`Unsupported colour ${value}`);
  const [r, g, b, a = 1] = m[1].split(',').map(Number);
  return [r, g, b, a];
}

const over = (fg: Rgba, bg: Rgba): Rgba => [0, 1, 2].map((i) => fg[i] * fg[3] + bg[i] * (1 - fg[3])).concat(1) as Rgba;

function luminance([r, g, b]: Rgba): number {
  const lin = (v: number) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

function contrast(a: Rgba, b: Rgba): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const dark = block(':root,');
const themes = { dark, light: { ...dark, ...block("[data-theme='light']") } };

const TEXT_PAIRS: [string, string][] = [
  ['text', 'bg'],
  ['text', 'surface-1'],
  ['text-dim', 'surface-2'],
  ['text-faint', 'surface-1'],
  ['text-faint', 'surface-2'],
  ['text-faint', 'surface-3'],
  ['accent', 'surface-1'],
  ['accent', 'surface-2'],
  ['accent-ink', 'accent'],
  ['accent-strong', 'accent-soft@surface-1'],
  ['danger', 'surface-1'],
  ['danger', 'danger-soft@surface-1'],
  ['warning', 'surface-1'],
  ['warning', 'warning-soft@surface-1'],
  ['success', 'surface-1'],
  ['edited', 'surface-1'],
  ['info', 'surface-1'],
];

describe('design token contrast (WCAG AA)', () => {
  for (const [name, tokens] of Object.entries(themes)) {
    const resolve = (ref: string): Rgba => {
      if (!ref.includes('@')) return color(tokens[ref]);
      const [soft, base] = ref.split('@');
      return over(color(tokens[soft]), color(tokens[base]));
    };

    it(`${name}: text colours reach 4.5:1`, () => {
      const failures = TEXT_PAIRS.map(([fg, bg]) => ({ pair: `${fg} on ${bg}`, ratio: contrast(resolve(fg), resolve(bg)) }))
        .filter((r) => r.ratio < 4.5)
        .map((r) => `${r.pair}: ${r.ratio.toFixed(2)}`);
      expect(failures).toEqual([]);
    });

    it(`${name}: focus ring reaches 3:1 against the surfaces`, () => {
      for (const bg of ['bg', 'surface-1', 'surface-2']) {
        expect(contrast(resolve('focus'), resolve(bg))).toBeGreaterThanOrEqual(3);
      }
    });
  }
});
