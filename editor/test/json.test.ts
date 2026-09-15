import { describe, expect, it } from 'vitest';
import { exportJson, importJson, JsonImportError, parseDump, presetKey } from '../src/pacer';
import { fixture } from './helpers';

describe('JSON import/export', () => {
  const preset = parseDump(fixture('A1.factory.syx')).presets.get(1)!.preset;

  it('round-trips presets and labels', () => {
    const text = exportJson([{ index: 19, preset, labels: { SW1: 'LOOP 1' } }], new Date('2026-01-01T00:00:00Z'));
    const entries = importJson(text);
    expect(entries).toHaveLength(1);
    expect(entries[0].index).toBe(19);
    expect(presetKey(entries[0].preset)).toBe(presetKey(preset));
    expect(entries[0].labels).toEqual({ SW1: 'LOOP 1' });
    expect(JSON.parse(text).presets[0].slot).toBe('D1');
  });

  it('rejects foreign or malformed documents with a path', () => {
    expect(() => importJson('nope')).toThrow(JsonImportError);
    expect(() => importJson('{"format":"other"}')).toThrow(/Pacer Studio/);
    const doc = JSON.parse(exportJson([{ index: 1, preset }]));
    doc.presets[0].preset.controls.SW3.steps.pop();
    expect(() => importJson(JSON.stringify(doc))).toThrow(/presets\[0\]\.preset\.controls\.SW3\.steps/);
  });

  it('clamps out-of-range numbers', () => {
    const doc = JSON.parse(exportJson([{ index: 1, preset }]));
    doc.presets[0].preset.controls.SW1.steps[0].data[0] = 400;
    doc.presets[0].preset.controls.SW1.steps[0].channel = 40;
    const [entry] = importJson(JSON.stringify(doc));
    expect(entry.preset.controls.SW1.steps[0].data[0]).toBe(127);
    expect(entry.preset.controls.SW1.steps[0].channel).toBe(16);
  });
});
