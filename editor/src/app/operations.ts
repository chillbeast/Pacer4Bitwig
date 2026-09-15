import factoryUrl from '../assets/factory-defaults.syx?url';
import { isAbortError } from '../midi';
import {
  D6_INDEX,
  FULL_BACKUP_MESSAGES,
  SINGLE_PRESET_MESSAGES,
  concatMessages,
  describePart,
  diffParts,
  displayName,
  encodeDump,
  encodePreset,
  exportJson,
  importJson,
  parseDump,
  parseMessages,
  presetsEqual,
  requestFullBackup,
  requestPreset,
  slotLabel,
  type EncodedPart,
  type ParsedPreset,
  type Preset,
} from '../pacer';
import { isConnected, useDevice, type OperationKind } from '../store/device';
import { isSlotEdited, setDeviceTruth, slotPendingParts, slotWriteParts, useEditor, type LoadEntry } from '../store/editor';
import { confirmAction, useUi, type ToastTone } from '../store/ui';
import { MAX_IMPORT_BYTES, downloadBytes, downloadText, readFileBytes, safeFilePart, timestamp } from './files';
import { midi } from './midi';

function toast(tone: ToastTone, title: string, detail?: string, timeoutMs?: number) {
  return useUi.getState().toast({ tone, title, detail }, timeoutMs ?? (tone === 'error' ? 12000 : 5000));
}

function errorText(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

export function isBusy(): boolean {
  return useDevice.getState().operation !== null;
}

function begin(kind: OperationKind, label: string, total: number): AbortController {
  const controller = new AbortController();
  useDevice.getState().setOperation({ kind, label, done: 0, total, cancel: () => controller.abort() });
  return controller;
}

function relabel(kind: OperationKind, label: string, total: number, controller: AbortController, done = 0) {
  useDevice.getState().setOperation({ kind, label, done, total, cancel: () => controller.abort() });
}

function end() {
  useDevice.getState().setOperation(null);
}

function requireConnection(): boolean {
  if (isConnected(useDevice.getState().midi)) return true;
  useUi.getState().toast(
    {
      tone: 'warning',
      title: 'Pacer not connected',
      detail: 'Connect the Pacer over USB and select its port 1 in the connection menu.',
    },
    6000,
  );
  return false;
}

function progress(done: number) {
  useDevice.getState().progress(done);
}

async function fetchPreset(index: number, signal: AbortSignal): Promise<ParsedPreset | null> {
  const result = await midi.request(requestPreset(index), {
    expected: SINGLE_PRESET_MESSAGES,
    signal,
    onProgress: progress,
  });
  return parseMessages(result.messages).presets.get(index) ?? null;
}

// ---------------------------------------------------------------------------------------------
// Reading
// ---------------------------------------------------------------------------------------------

export async function readPresetFromDevice(index: number): Promise<boolean> {
  if (isBusy() || !requireConnection()) return false;
  if (index === D6_INDEX) {
    useUi.getState().toast(
      {
        tone: 'warning',
        title: 'D6 cannot be read on its own',
        detail: 'The Pacer never answers a request for D6 (firmware quirk). "Read all" gets D6 from a full backup.',
        action: { label: 'Read all', run: () => void readAllFromDevice() },
      },
      12000,
    );
    return false;
  }
  const slot = useEditor.getState().slots[index];
  if (
    slot &&
    isSlotEdited(slot) &&
    !(await confirmAction(
      `Replace edits in ${slotLabel(index)}?`,
      `${slotLabel(index)} has edits that were not sent. Reading from the Pacer replaces the working copy (you can undo).`,
      'Read from Pacer',
    ))
  ) {
    return false;
  }
  const controller = begin('read', `Reading ${slotLabel(index)}`, SINGLE_PRESET_MESSAGES);
  try {
    const parsed = await fetchPreset(index, controller.signal);
    if (!parsed) throw new Error(`The Pacer replied without data for ${slotLabel(index)}.`);
    useEditor.getState().loadFromDevice([{ index, preset: parsed.preset, complete: parsed.complete }]);
    useEditor.getState().selectSlot(index);
    if (parsed.complete) {
      toast('success', `Read ${slotLabel(index)} “${displayName(parsed.preset.name)}”`, undefined, 3000);
    } else {
      toast(
        'warning',
        `Incomplete reply for ${slotLabel(index)}`,
        `Received ${parsed.parts} of ${SINGLE_PRESET_MESSAGES} parts; missing parts show defaults.`,
      );
    }
    return true;
  } catch (err) {
    if (!isAbortError(err)) toast('error', `Could not read ${slotLabel(index)}`, errorText(err));
    return false;
  } finally {
    end();
  }
}

async function fetchFullBackup(controller: AbortController) {
  const result = await midi.request(requestFullBackup(), {
    expected: FULL_BACKUP_MESSAGES,
    firstReplyTimeoutMs: 4000,
    idleTimeoutMs: 2500,
    signal: controller.signal,
    onProgress: progress,
  });
  const parsed = parseMessages(result.messages);
  if (parsed.presets.size === 0) throw new Error('The Pacer replied without preset data.');
  const bytes = concatMessages(result.messages);
  useDevice.getState().setBackup({ bytes, messages: result.messages.length, time: Date.now(), downloaded: false });
  useDevice.getState().setGlobals(parsed.globals);
  return { parsed, complete: result.complete };
}

/** Read every preset (full backup) into the editor. */
export async function readAllFromDevice(): Promise<boolean> {
  if (isBusy() || !requireConnection()) return false;
  const edited = useEditor.getState().slots.filter(isSlotEdited).length;
  if (
    edited > 0 &&
    !(await confirmAction(
      'Replace unsent edits?',
      `${edited} preset${edited === 1 ? ' has' : 's have'} edits that were not sent. Reading all presets replaces the working copies (you can undo).`,
      'Read all',
    ))
  ) {
    return false;
  }
  const controller = begin('backup', 'Reading all presets', FULL_BACKUP_MESSAGES);
  try {
    const { parsed, complete } = await fetchFullBackup(controller);
    const entries: LoadEntry[] = [...parsed.presets.values()].map((p) => ({
      index: p.index,
      preset: p.preset,
      complete: p.complete,
    }));
    useEditor.getState().loadFromDevice(entries);
    const incomplete = entries.filter((e) => !e.complete).length;
    if (!complete || incomplete > 0) {
      toast('warning', `Read ${entries.length} presets (${incomplete} incomplete)`, 'The transfer stopped early. Try again.');
    } else {
      useUi.getState().toast(
        {
          tone: 'success',
          title: `Read ${entries.length} presets from the Pacer`,
          detail: 'A full backup of this read is kept for the session.',
          action: { label: 'Download backup', run: downloadSessionBackup },
        },
        8000,
      );
    }
    return true;
  } catch (err) {
    if (!isAbortError(err)) toast('error', 'Could not read the presets', errorText(err));
    return false;
  } finally {
    end();
  }
}

/** Read a full backup for safe keeping (working copies are not touched) and download it. */
export async function readAndDownloadBackup(): Promise<boolean> {
  if (isBusy() || !requireConnection()) return false;
  const controller = begin('backup', 'Reading full backup', FULL_BACKUP_MESSAGES);
  try {
    const { parsed, complete } = await fetchFullBackup(controller);
    if (!complete) throw new Error('The backup transfer stopped early; not saved. Try again.');
    setDeviceTruth(
      [...parsed.presets.values()].filter((p) => p.complete).map((p) => ({ index: p.index, preset: p.preset })),
      false,
    );
    downloadSessionBackup();
    return true;
  } catch (err) {
    useDevice.getState().setBackup(null);
    if (!isAbortError(err)) toast('error', 'Backup failed', errorText(err));
    return false;
  } finally {
    end();
  }
}

export function downloadSessionBackup(): void {
  const backup = useDevice.getState().backup;
  if (!backup) return;
  downloadBytes(backup.bytes, `pacer-full-backup-${timestamp(new Date(backup.time))}.syx`);
  useDevice.getState().markBackupDownloaded();
  toast('success', 'Backup downloaded', `${backup.messages} messages · ${(backup.bytes.length / 1024).toFixed(0)} KB`, 3000);
}

// ---------------------------------------------------------------------------------------------
// Writing
// ---------------------------------------------------------------------------------------------

export interface WritePlanItem {
  index: number;
  preset: Preset;
  parts: EncodedPart[];
  /** True when the device content is unknown and the whole preset is sent. */
  full: boolean;
}

export function planWrite(indexes: readonly number[], mode: 'changes' | 'slot'): WritePlanItem[] {
  const { slots } = useEditor.getState();
  const plan: WritePlanItem[] = [];
  for (const index of indexes) {
    const slot = slots[index];
    if (!slot?.preset) continue;
    const parts = mode === 'changes' ? slotPendingParts(slot, index) : slotWriteParts(slot, index);
    if (parts.length === 0) continue;
    plan.push({ index, preset: slot.preset, parts, full: slot.device === null });
  }
  return plan;
}

export interface WriteOptions {
  verify: boolean;
  delayMs: number;
}

export async function executeWrite(plan: readonly WritePlanItem[], options: WriteOptions): Promise<boolean> {
  if (plan.length === 0 || isBusy() || !requireConnection()) return false;
  const total = plan.reduce((n, item) => n + item.parts.length, 0);
  const controller = begin('write', 'Writing to the Pacer', total);
  const written: WritePlanItem[] = [];
  let done = 0;
  try {
    for (const item of plan) {
      relabel('write', `Writing ${slotLabel(item.index)} · ${item.parts.length} messages`, total, controller, done);
      await midi.sendAll(
        item.parts.map((p) => p.bytes),
        { delayMs: options.delayMs, signal: controller.signal, onProgress: (sent) => progress(done + sent) },
      );
      done += item.parts.length;
      useEditor.getState().markWritten(item.index, item.preset);
      written.push(item);
    }
    useDevice.getState().countWrite();
  } catch (err) {
    end();
    const current = plan[written.length];
    if (current) setDeviceTruth([{ index: current.index, preset: null }], false);
    const partial = current
      ? `${done} of ${total} messages were sent; ${slotLabel(current.index)} may be partially written — write it again.`
      : '';
    if (isAbortError(err)) toast('warning', 'Write cancelled', partial);
    else toast('error', 'Write failed', `${errorText(err)} ${partial}`.trim());
    return false;
  }

  const labels = written.map((w) => slotLabel(w.index)).join(', ');
  if (!options.verify) {
    end();
    toast('success', `Wrote ${labels}`, `${total} messages sent.`);
    return true;
  }

  const mismatches: string[] = [];
  const toVerify = written.filter((w) => w.index !== D6_INDEX);
  try {
    for (let i = 0; i < toVerify.length; i++) {
      const item = toVerify[i];
      relabel('verify', `Verifying ${slotLabel(item.index)} (${i + 1}/${toVerify.length})`, SINGLE_PRESET_MESSAGES, controller);
      const parsed = await fetchPreset(item.index, controller.signal);
      if (!parsed || !parsed.complete) {
        mismatches.push(`${slotLabel(item.index)}: incomplete read-back`);
        continue;
      }
      if (!presetsEqual(parsed.preset, item.preset)) {
        const diffs = diffParts(parsed.preset, item.preset, item.index).map((p) => describePart(p.part));
        mismatches.push(
          `${slotLabel(item.index)}: ${diffs.slice(0, 4).join(', ')}${diffs.length > 4 ? ` +${diffs.length - 4} more` : ''}`,
        );
        setDeviceTruth([{ index: item.index, preset: parsed.preset }], true);
      }
    }
  } catch (err) {
    if (!isAbortError(err)) mismatches.push(`Read-back failed: ${errorText(err)}`);
  } finally {
    end();
  }

  if (mismatches.length > 0) {
    toast('error', 'Verification found differences', mismatches.join(' · '), 20000);
  } else {
    const skipped = written.length - toVerify.length;
    toast(
      'success',
      `Wrote and verified ${labels}`,
      skipped > 0 ? `${total} messages sent. D6 cannot be read back, so it was not verified.` : `${total} messages sent.`,
    );
  }
  return mismatches.length === 0;
}

/** Plain CC for LED tests (channel 1..16). */
export function sendControlChange(channel: number, cc: number, value: number): boolean {
  if (!midi.canSend) {
    requireConnection();
    return false;
  }
  try {
    midi.sendControlChange(channel, cc, value);
    return true;
  } catch (err) {
    toast('error', 'Could not send', errorText(err));
    return false;
  }
}

// ---------------------------------------------------------------------------------------------
// Files
// ---------------------------------------------------------------------------------------------

async function confirmReplace(indexes: readonly number[]): Promise<boolean> {
  const { slots } = useEditor.getState();
  const edited = indexes.filter((i) => slots[i] && isSlotEdited(slots[i]));
  if (edited.length === 0) return true;
  return confirmAction(
    'Replace unsent edits?',
    `${edited.map(slotLabel).join(', ')} ${edited.length === 1 ? 'has' : 'have'} edits that were not sent. Importing replaces them (you can undo).`,
    'Import',
  );
}

async function importSyx(file: File): Promise<void> {
  const parsed = parseDump(await readFileBytes(file));
  if (parsed.presets.size === 0 && parsed.globals.length === 0) {
    throw new Error('No Nektar Pacer data found in this file.');
  }
  if (parsed.globals.length > 0) useDevice.getState().setGlobals(parsed.globals);
  let entries: LoadEntry[] = [...parsed.presets.values()].map((p) => ({
    index: p.index,
    preset: p.preset,
    complete: p.complete,
  }));
  if (entries.length === 0) {
    toast('info', `${file.name}: global settings only`, 'Kept for full-backup export; presets are unchanged.');
    return;
  }

  const selected = useEditor.getState().selectedSlot;
  if (entries.length === 1 && entries[0].index !== selected) {
    const from = slotLabel(entries[0].index);
    const choice = await useUi.getState().ask({
      title: `Import “${displayName(entries[0].preset.name)}”`,
      body: `This file contains one preset stored as ${from}. Where should it go?`,
      choices: [
        { id: 'file', label: `Into ${from}` },
        { id: 'selected', label: `Into ${slotLabel(selected)}`, tone: 'primary' },
      ],
    });
    if (!choice) return;
    if (choice === 'selected') entries = [{ ...entries[0], index: selected }];
  }

  if (!(await confirmReplace(entries.map((e) => e.index)))) return;
  useEditor.getState().loadPresets(entries, 'file', `Import ${file.name}`);
  if (entries.length === 1) useEditor.getState().selectSlot(entries[0].index);

  const incomplete = entries.filter((e) => !e.complete).length;
  const notes = [
    parsed.badChecksums > 0 ? `${parsed.badChecksums} messages with bad checksums skipped` : '',
    incomplete > 0 ? `${incomplete} incomplete preset${incomplete === 1 ? '' : 's'} (missing parts use defaults)` : '',
  ].filter(Boolean);
  toast(
    notes.length > 0 ? 'warning' : 'success',
    `Imported ${entries.length} preset${entries.length === 1 ? '' : 's'} from ${file.name}`,
    notes.join(' · ') || undefined,
  );
}

async function importJsonFile(file: File): Promise<void> {
  const entries = importJson(await file.text());
  if (entries.length === 0) throw new Error('The file contains no presets.');
  if (!(await confirmReplace(entries.map((e) => e.index)))) return;
  useEditor.getState().loadPresets(entries, 'file', `Import ${file.name}`);
  if (entries.length === 1) useEditor.getState().selectSlot(entries[0].index);
  toast('success', `Imported ${entries.length} preset${entries.length === 1 ? '' : 's'} from ${file.name}`);
}

export async function importFiles(files: readonly File[]): Promise<void> {
  for (const file of files) {
    if (file.size > MAX_IMPORT_BYTES) {
      toast('error', `${file.name} is too large`, 'Pacer files are well under 1 MB.');
      continue;
    }
    try {
      if (/\.json$/i.test(file.name)) await importJsonFile(file);
      else await importSyx(file);
    } catch (err) {
      toast('error', `Could not import ${file.name}`, errorText(err));
    }
  }
}

export async function loadFactoryPresets(): Promise<void> {
  const edited = useEditor.getState().slots.filter(isSlotEdited).length;
  if (
    edited > 0 &&
    !(await confirmAction('Replace unsent edits?', 'Loading the factory presets replaces all working copies (you can undo).', 'Load factory presets'))
  ) {
    return;
  }
  try {
    const response = await fetch(factoryUrl);
    const parsed = parseDump(new Uint8Array(await response.arrayBuffer()));
    useEditor
      .getState()
      .loadPresets(
        [...parsed.presets.values()].map((p) => ({ index: p.index, preset: p.preset, complete: p.complete })),
        'file',
        'Load factory presets',
      );
    useDevice.getState().setGlobals(parsed.globals);
    toast('success', 'Factory presets loaded', 'Nothing was sent to the Pacer.', 3500);
  } catch (err) {
    toast('error', 'Could not load the factory presets', errorText(err));
  }
}

export function exportSlotSyx(index: number): void {
  const slot = useEditor.getState().slots[index];
  if (!slot?.preset) return;
  const bytes = concatMessages(encodePreset(slot.preset, index));
  downloadBytes(bytes, `pacer-${slotLabel(index)}-${safeFilePart(displayName(slot.preset.name))}.syx`);
}

export function exportAllSyx(): void {
  const { slots } = useEditor.getState();
  const entries = slots.flatMap((s, i) => (s.preset ? [[i, s.preset] as const] : []));
  if (entries.length === 0) return;
  const bytes = encodeDump(entries, useDevice.getState().globals);
  downloadBytes(bytes, `pacer-all-presets-${timestamp()}.syx`);
}

export function exportSlotsJson(indexes?: readonly number[]): void {
  const { slots } = useEditor.getState();
  const list = (indexes ?? slots.map((_, i) => i)).filter((i) => slots[i]?.preset);
  if (list.length === 0) return;
  const text = exportJson(list.map((i) => ({ index: i, preset: slots[i].preset!, labels: slots[i].labels })));
  const name = list.length === 1 ? `pacer-${slotLabel(list[0])}-${safeFilePart(displayName(slots[list[0]].preset!.name))}` : `pacer-presets-${timestamp()}`;
  downloadText(text, `${name}.json`);
}
