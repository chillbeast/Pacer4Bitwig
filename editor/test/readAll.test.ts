import { describe, expect, it } from 'vitest';
import { readAllWithRetries, readPresetWithRetries, summarizeReadAll, type ReadIO } from '../src/app/readAll';
import { splitSysex } from '../src/pacer';
import { fixture } from './helpers';

const all = splitSysex(fixture('ALL.factory.bin'));
const presetMessages = (index: number) => all.filter((m) => m[6] === 0x01 && m[7] === index);
const globalMessages = all.filter((m) => m[6] === 0x05);

function fakeIO(overrides: Partial<ReadIO> & { presetReplies?: Record<number, Uint8Array[][]> }) {
  const calls: string[] = [];
  const queues = overrides.presetReplies ?? {};
  const io: ReadIO = {
    fullBackup: overrides.fullBackup ?? (async () => ({ messages: all })),
    preset:
      overrides.preset ??
      (async (index) => {
        calls.push(`preset ${index}`);
        const queue = queues[index];
        if (queue && queue.length > 0) return { messages: queue.shift()! };
        return { messages: presetMessages(index) };
      }),
    globals:
      overrides.globals ??
      (async () => {
        calls.push('globals');
        return { messages: globalMessages };
      }),
  };
  return { io, calls };
}

describe('readAllWithRetries', () => {
  it('accepts a complete backup without extra requests', async () => {
    const { io, calls } = fakeIO({});
    const result = await readAllWithRetries(io);
    expect(result.presets.size).toBe(25);
    expect(result.retried).toEqual([]);
    expect(result.failed).toEqual([]);
    expect(result.globalsComplete).toBe(true);
    expect(calls).toEqual([]);
    expect(summarizeReadAll(result)).toMatchObject({ ok: true, detail: '25 of 25 presets complete · global settings complete' });
  });

  it('re-requests an incomplete preset and reports it as retried', async () => {
    const dropped = new Set(presetMessages(8).slice(100, 130));
    const { io, calls } = fakeIO({
      fullBackup: async () => ({ messages: all.filter((m) => !dropped.has(m)) }),
      presetReplies: { 8: [presetMessages(8).slice(0, 50)] }, // first retry still partial, second complete
    });
    const statuses: string[] = [];
    const result = await readAllWithRetries(io, { onStatus: (s) => statuses.push(s.label) });
    expect(calls).toEqual(['preset 8', 'preset 8']);
    expect(result.retried).toEqual([8]);
    expect(result.recovered).toEqual([8]);
    expect(result.presets.get(8)!.complete).toBe(true);
    expect(statuses).toContain('Re-reading B2 (attempt 2/2)');
    expect(summarizeReadAll(result).detail).toBe('25 of 25 presets complete · 1 retried (B2) · global settings complete');
  });

  it('gives up after the retry budget and names what is missing', async () => {
    const { io } = fakeIO({
      fullBackup: async () => ({ messages: all.filter((m) => !(m[7] === 3 && m[6] === 1 && m[8] === 0x14)) }),
      preset: async () => {
        throw new Error('No reply from the Pacer.');
      },
    });
    const result = await readAllWithRetries(io, { maxRetries: 2 });
    expect(result.failed).toHaveLength(1);
    expect(result.failed[0].index).toBe(3);
    expect(result.failed[0].reason).toContain('A3: 176 of 189 parts, missing Switch A mode, Switch A steps 1–6, Switch A LEDs 1–6');
    expect(result.failed[0].reason).toContain('No reply from the Pacer.');
    expect(summarizeReadAll(result).ok).toBe(false);
  });

  it('never re-requests D6 and re-reads missing globals', async () => {
    const { io, calls } = fakeIO({
      fullBackup: async () => ({ messages: all.filter((m) => !(m[6] === 1 && m[7] === 24) && !(m[6] === 5 && m[7] === 0)) }),
    });
    const result = await readAllWithRetries(io);
    expect(calls).toEqual(['globals']);
    expect(result.failed).toEqual([{ index: 24, parts: 0, reason: 'D6: nothing received (D6 cannot be re-requested on its own)' }]);
    expect(result.globalsComplete).toBe(true);
    expect(result.globals.messages).toHaveLength(37);
  });

  it('fails clearly when the backup has no presets', async () => {
    const { io } = fakeIO({ fullBackup: async () => ({ messages: globalMessages }) });
    await expect(readAllWithRetries(io)).rejects.toThrow('without preset data (37 of 4762 messages)');
  });
});

describe('readPresetWithRetries', () => {
  it('retries a timed-out read', async () => {
    let n = 0;
    const { io } = fakeIO({
      preset: async (index) => {
        n++;
        if (n === 1) throw new Error('No reply from the Pacer.');
        return { messages: presetMessages(index) };
      },
    });
    const { parsed, retries } = await readPresetWithRetries(io, 5);
    expect(parsed.complete).toBe(true);
    expect(retries).toBe(1);
  });

  it('names the preset when nothing ever arrives, and refuses D6', async () => {
    const { io } = fakeIO({
      preset: async () => {
        throw new Error('No reply from the Pacer.');
      },
    });
    await expect(readPresetWithRetries(io, 19)).rejects.toThrow('No reply for D1 (0 of 189 messages after 3 attempts)');
    await expect(readPresetWithRetries(io, 24)).rejects.toThrow(/D6/);
  });
});
