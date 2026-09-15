import { MidiError } from './errors';

export type MessageListener = (message: Uint8Array) => void;
export type Subscribe = (listener: MessageListener) => () => void;

export interface CollectOptions {
  /** Number of messages expected; resolves shortly after it is reached. */
  expected?: number;
  /** Rejects when nothing arrives within this time. */
  firstReplyTimeoutMs?: number;
  /** Resolves (possibly incomplete) when the stream pauses this long. */
  idleTimeoutMs?: number;
  /** Extra wait after the expected count, to catch trailing messages. */
  settleMs?: number;
  accept?: (message: Uint8Array) => boolean;
  onProgress?: (received: number, expected: number | undefined) => void;
  signal?: AbortSignal;
}

export interface CollectResult {
  messages: Uint8Array[];
  /** False when the stream stopped before the expected count. */
  complete: boolean;
}

/**
 * Subscribe to incoming messages, run `start` (usually: send the request) and collect the replies.
 */
export function collectReplies(subscribe: Subscribe, start: () => void, options: CollectOptions = {}): Promise<CollectResult> {
  const {
    expected,
    firstReplyTimeoutMs = 3000,
    idleTimeoutMs = 1500,
    settleMs = 250,
    accept = () => true,
    onProgress,
    signal,
  } = options;

  return new Promise<CollectResult>((resolve, reject) => {
    const messages: Uint8Array[] = [];
    let timer: ReturnType<typeof setTimeout> | undefined;
    let done = false;
    let unsubscribe: () => void = () => {};

    const finish = (settle: () => void) => {
      if (done) return;
      done = true;
      if (timer !== undefined) clearTimeout(timer);
      unsubscribe();
      signal?.removeEventListener('abort', onAbort);
      settle();
    };
    const arm = (ms: number, onTimeout: () => void) => {
      if (timer !== undefined) clearTimeout(timer);
      timer = setTimeout(onTimeout, ms);
    };
    function onAbort() {
      finish(() => reject(new MidiError('aborted', 'Cancelled')));
    }

    if (signal?.aborted) {
      reject(new MidiError('aborted', 'Cancelled'));
      return;
    }

    unsubscribe = subscribe((message) => {
      if (done || !accept(message)) return;
      messages.push(message);
      onProgress?.(messages.length, expected);
      if (expected !== undefined && messages.length >= expected) {
        arm(settleMs, () => finish(() => resolve({ messages, complete: true })));
      } else {
        arm(idleTimeoutMs, () => finish(() => resolve({ messages, complete: expected === undefined })));
      }
    });
    signal?.addEventListener('abort', onAbort);
    arm(firstReplyTimeoutMs, () =>
      finish(() => reject(new MidiError('timeout', 'No reply from the Pacer. Check the cable and the selected ports.'))),
    );

    try {
      start();
    } catch (err) {
      finish(() => reject(err));
    }
  });
}

export interface SendQueueOptions {
  /** Pause between messages so the Pacer can store each one. */
  delayMs?: number;
  onProgress?: (sent: number, total: number) => void;
  signal?: AbortSignal;
  sleep?: (ms: number) => Promise<void>;
}

const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export async function sendQueue(
  messages: readonly Uint8Array[],
  send: (message: Uint8Array) => void,
  options: SendQueueOptions = {},
): Promise<void> {
  const { delayMs = 20, onProgress, signal, sleep = defaultSleep } = options;
  for (let i = 0; i < messages.length; i++) {
    if (signal?.aborted) {
      throw new MidiError('aborted', `Cancelled after ${i} of ${messages.length} messages`);
    }
    send(messages[i]);
    onProgress?.(i + 1, messages.length);
    if (delayMs > 0 && i < messages.length - 1) await sleep(delayMs);
  }
}
