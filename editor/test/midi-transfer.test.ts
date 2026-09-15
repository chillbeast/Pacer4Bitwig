import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { collectReplies, MidiError, sendQueue, type MessageListener } from '../src/midi';

function source() {
  const listeners = new Set<MessageListener>();
  return {
    subscribe: (l: MessageListener) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    emit: (n: number) => {
      for (let i = 0; i < n; i++) for (const l of [...listeners]) l(Uint8Array.of(0xf0, i & 0x7f, 0xf7));
    },
    get count() {
      return listeners.size;
    },
  };
}

describe('collectReplies', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('resolves complete once the expected count arrives', async () => {
    const src = source();
    const progress: number[] = [];
    const promise = collectReplies(src.subscribe, () => src.emit(189), {
      expected: 189,
      settleMs: 100,
      onProgress: (n) => progress.push(n),
    });
    await vi.advanceTimersByTimeAsync(100);
    const result = await promise;
    expect(result.complete).toBe(true);
    expect(result.messages).toHaveLength(189);
    expect(progress.at(-1)).toBe(189);
    expect(src.count).toBe(0);
  });

  it('resolves incomplete when the stream goes quiet', async () => {
    const src = source();
    const promise = collectReplies(src.subscribe, () => src.emit(50), { expected: 189, idleTimeoutMs: 1500 });
    await vi.advanceTimersByTimeAsync(1500);
    const result = await promise;
    expect(result.complete).toBe(false);
    expect(result.messages).toHaveLength(50);
  });

  it('rejects with a timeout when nothing answers (e.g. D6)', async () => {
    const src = source();
    const promise = collectReplies(src.subscribe, () => {}, { expected: 189, firstReplyTimeoutMs: 3000 });
    const assertion = expect(promise).rejects.toMatchObject({ code: 'timeout' });
    await vi.advanceTimersByTimeAsync(3000);
    await assertion;
    expect(src.count).toBe(0);
  });

  it('can be cancelled', async () => {
    const src = source();
    const controller = new AbortController();
    const promise = collectReplies(src.subscribe, () => src.emit(3), { expected: 189, signal: controller.signal });
    controller.abort();
    await expect(promise).rejects.toBeInstanceOf(MidiError);
  });

  it('filters with accept()', async () => {
    const src = source();
    const promise = collectReplies(src.subscribe, () => src.emit(10), {
      accept: (m) => m[1] % 2 === 0,
      idleTimeoutMs: 10,
    });
    await vi.advanceTimersByTimeAsync(10);
    expect((await promise).messages).toHaveLength(5);
  });
});

describe('sendQueue', () => {
  it('sends in order with a delay between messages and reports progress', async () => {
    const sent: number[] = [];
    const sleeps: number[] = [];
    const progress: string[] = [];
    const messages = [1, 2, 3].map((n) => Uint8Array.of(n));
    await sendQueue(messages, (m) => sent.push(m[0]), {
      delayMs: 15,
      sleep: async (ms) => {
        sleeps.push(ms);
      },
      onProgress: (s, t) => progress.push(`${s}/${t}`),
    });
    expect(sent).toEqual([1, 2, 3]);
    expect(sleeps).toEqual([15, 15]);
    expect(progress).toEqual(['1/3', '2/3', '3/3']);
  });

  it('stops when aborted', async () => {
    const controller = new AbortController();
    const sent: number[] = [];
    const messages = [1, 2, 3, 4].map((n) => Uint8Array.of(n));
    const promise = sendQueue(messages, (m) => sent.push(m[0]), {
      signal: controller.signal,
      sleep: async () => {
        if (sent.length === 2) controller.abort();
      },
    });
    await expect(promise).rejects.toMatchObject({ code: 'aborted' });
    expect(sent).toEqual([1, 2]);
  });
});
