/**
 * Reading with verification and bounded retries. Framework-free: the MIDI round trips are injected so the retry logic
 * can be unit-tested.
 */
import {
  D6_INDEX,
  FULL_BACKUP_MESSAGES,
  GLOBAL_MESSAGE_COUNT,
  SINGLE_PRESET_MESSAGES,
  SLOT_COUNT,
  mergeGlobals,
  parseGlobalMessages,
  parseMessages,
  slotLabel,
  summarizeParts,
  type GlobalSettings,
  type ParsedPreset,
} from '../pacer';

export interface Reply {
  messages: Uint8Array[];
}

export interface ReadIO {
  fullBackup: (onProgress: (received: number) => void) => Promise<Reply>;
  preset: (index: number, onProgress: (received: number) => void) => Promise<Reply>;
  globals: (onProgress: (received: number) => void) => Promise<Reply>;
}

export interface ReadStatus {
  label: string;
  done: number;
  total: number;
}

export interface FailedPreset {
  index: number;
  /** Parts received (0 when the preset never arrived). */
  parts: number;
  reason: string;
}

export interface ReadAllResult {
  presets: Map<number, ParsedPreset>;
  globals: GlobalSettings;
  /** Messages of the first full-backup pass, as received (the session backup). */
  backupMessages: Uint8Array[];
  retried: number[];
  recovered: number[];
  failed: FailedPreset[];
  globalsComplete: boolean;
}

export interface ReadOptions {
  maxRetries?: number;
  onStatus?: (status: ReadStatus) => void;
  signal?: AbortSignal;
}

function checkAbort(signal?: AbortSignal) {
  if (signal?.aborted) throw new DOMException('Cancelled', 'AbortError');
}

export function describeIncomplete(p: ParsedPreset | undefined, index: number): string {
  if (!p) return `${slotLabel(index)}: nothing received`;
  return `${slotLabel(index)}: ${p.parts} of ${SINGLE_PRESET_MESSAGES} parts, missing ${summarizeParts(p.missing)}`;
}

const better = (a: ParsedPreset | undefined, b: ParsedPreset | undefined) => (!a ? b : !b ? a : b.parts > a.parts ? b : a);

/** Re-request one preset until it is complete (at most `maxRetries` extra attempts). */
async function retryPreset(
  io: ReadIO,
  index: number,
  current: ParsedPreset | undefined,
  options: ReadOptions,
): Promise<{ parsed: ParsedPreset | undefined; attempts: number; lastError: string | null }> {
  const maxRetries = options.maxRetries ?? 2;
  let parsed = current;
  let lastError: string | null = null;
  let attempts = 0;
  while ((!parsed || !parsed.complete) && attempts < maxRetries) {
    checkAbort(options.signal);
    attempts++;
    const label = `Re-reading ${slotLabel(index)} (attempt ${attempts}/${maxRetries})`;
    options.onStatus?.({ label, done: 0, total: SINGLE_PRESET_MESSAGES });
    try {
      const reply = await io.preset(index, (n) => options.onStatus?.({ label, done: n, total: SINGLE_PRESET_MESSAGES }));
      parsed = better(parsed, parseMessages(reply.messages).presets.get(index));
      lastError = null;
    } catch (err) {
      if (options.signal?.aborted) throw err;
      lastError = err instanceof Error ? err.message : String(err);
    }
  }
  return { parsed, attempts, lastError };
}

/** Read one preset; incomplete replies are re-requested. D6 is refused (the Pacer never answers it). */
export async function readPresetWithRetries(io: ReadIO, index: number, options: ReadOptions = {}) {
  if (index === D6_INDEX) throw new Error('D6 cannot be read on its own; use Read all.');
  const label = `Reading ${slotLabel(index)}`;
  options.onStatus?.({ label, done: 0, total: SINGLE_PRESET_MESSAGES });
  let first: ParsedPreset | undefined;
  let firstError: unknown = null;
  try {
    const reply = await io.preset(index, (n) => options.onStatus?.({ label, done: n, total: SINGLE_PRESET_MESSAGES }));
    first = parseMessages(reply.messages).presets.get(index);
  } catch (err) {
    if (options.signal?.aborted) throw err;
    firstError = err;
  }
  const { parsed, attempts, lastError } =
    first?.complete ? { parsed: first, attempts: 0, lastError: null } : await retryPreset(io, index, first, options);
  if (!parsed) {
    const reason = lastError ?? (firstError instanceof Error ? firstError.message : 'no reply');
    throw new Error(`No reply for ${slotLabel(index)} (0 of ${SINGLE_PRESET_MESSAGES} messages after ${attempts + 1} attempts): ${reason}`);
  }
  return { parsed, retries: attempts };
}

/** Full backup, then verify: 25 complete presets and 37 global messages; re-request what is missing. */
export async function readAllWithRetries(io: ReadIO, options: ReadOptions = {}): Promise<ReadAllResult> {
  const backupLabel = 'Reading all presets';
  options.onStatus?.({ label: backupLabel, done: 0, total: FULL_BACKUP_MESSAGES });
  const reply = await io.fullBackup((n) => options.onStatus?.({ label: backupLabel, done: n, total: FULL_BACKUP_MESSAGES }));
  const parsed = parseMessages(reply.messages);
  if (parsed.presets.size === 0) {
    throw new Error(`The Pacer replied without preset data (${reply.messages.length} of ${FULL_BACKUP_MESSAGES} messages).`);
  }
  const presets = new Map(parsed.presets);
  const retried: number[] = [];
  const recovered: number[] = [];
  const failed: FailedPreset[] = [];

  for (let index = 0; index < SLOT_COUNT; index++) {
    const current = presets.get(index);
    if (current?.complete) continue;
    if (index === D6_INDEX) {
      failed.push({
        index,
        parts: current?.parts ?? 0,
        reason: `${describeIncomplete(current, index)} (D6 cannot be re-requested on its own)`,
      });
      continue;
    }
    retried.push(index);
    const { parsed: result, lastError } = await retryPreset(io, index, current, options);
    if (result) presets.set(index, result);
    if (result?.complete) recovered.push(index);
    else failed.push({ index, parts: result?.parts ?? 0, reason: `${describeIncomplete(result, index)}${lastError ? ` — ${lastError}` : ''}` });
  }

  let globals = parseGlobalMessages(parsed.globals);
  const maxRetries = options.maxRetries ?? 2;
  for (let attempt = 1; globals.messages.length < GLOBAL_MESSAGE_COUNT && attempt <= maxRetries; attempt++) {
    checkAbort(options.signal);
    const label = `Re-reading global settings (attempt ${attempt}/${maxRetries})`;
    options.onStatus?.({ label, done: globals.messages.length, total: GLOBAL_MESSAGE_COUNT });
    try {
      const g = await io.globals((n) => options.onStatus?.({ label, done: n, total: GLOBAL_MESSAGE_COUNT }));
      globals = mergeGlobals(globals, parseGlobalMessages(g.messages));
    } catch (err) {
      if (options.signal?.aborted) throw err;
    }
  }

  return {
    presets,
    globals,
    backupMessages: reply.messages,
    retried,
    recovered,
    failed,
    globalsComplete: globals.messages.length >= GLOBAL_MESSAGE_COUNT,
  };
}

/** "25 presets, 1 retried · global settings complete" */
export function summarizeReadAll(result: ReadAllResult): { ok: boolean; title: string; detail: string } {
  const complete = [...result.presets.values()].filter((p) => p.complete).length;
  const parts = [`${complete} of ${SLOT_COUNT} presets complete`];
  if (result.retried.length > 0) {
    parts.push(`${result.retried.length} retried (${result.retried.map(slotLabel).join(', ')})`);
  }
  parts.push(result.globalsComplete ? 'global settings complete' : `global settings ${result.globals.messages.length} of ${GLOBAL_MESSAGE_COUNT}`);
  const ok = result.failed.length === 0 && result.globalsComplete;
  return {
    ok,
    title: ok ? `Read ${complete} presets and the global settings` : 'Read finished with gaps',
    detail: [parts.join(' · '), ...result.failed.map((f) => f.reason)].join('. '),
  };
}
