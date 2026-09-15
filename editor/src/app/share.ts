import { create } from 'zustand';
import {
  ShareLinkError,
  decodeShareToken,
  displayName,
  encodeShareToken,
  shareTokenFromHash,
  shareUrl,
  slotLabel,
  type SharedPreset,
} from '../pacer';
import { useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { confirmReplace } from './operations';

interface ShareImportState {
  shared: SharedPreset | null;
}

export const useShareImport = create<ShareImportState>(() => ({ shared: null }));

/** Encode a slot into a link and copy it to the clipboard. */
export async function copyShareLink(index: number): Promise<void> {
  const slot = useEditor.getState().slots[index];
  if (!slot?.preset) return;
  const ui = useUi.getState();
  try {
    const token = await encodeShareToken({ preset: slot.preset, labels: slot.labels, slot: index });
    const url = shareUrl(window.location.href, token);
    try {
      await navigator.clipboard.writeText(url);
      ui.toast(
        {
          tone: 'success',
          title: `Share link for ${slotLabel(index)} copied`,
          detail: `“${displayName(slot.preset.name)}” · ${url.length} characters. Opening it lets anyone import the preset into their editor.`,
        },
        5000,
      );
    } catch {
      window.prompt('Copy this share link:', url);
    }
  } catch (err) {
    ui.toast({ tone: 'error', title: 'Could not create a share link', detail: err instanceof Error ? err.message : String(err) });
  }
}

function clearHash(): void {
  try {
    window.history.replaceState(null, '', window.location.pathname + window.location.search);
  } catch {
    window.location.hash = '';
  }
}

/** Open the import dialog when the URL carries a shared preset. */
export async function openShareFromLocation(): Promise<void> {
  const token = shareTokenFromHash(window.location.hash);
  if (!token) return;
  clearHash();
  try {
    const shared = await decodeShareToken(token);
    useShareImport.setState({ shared });
    useUi.getState().openDialog('share-import');
  } catch (err) {
    useUi.getState().toast(
      {
        tone: 'error',
        title: 'This share link cannot be opened',
        detail: err instanceof ShareLinkError ? err.message : 'The link is damaged or incomplete.',
      },
      10000,
    );
  }
}

/** Import the pending shared preset into a slot of the editor (never sends anything to the Pacer). */
export async function importSharedPreset(index: number): Promise<boolean> {
  const shared = useShareImport.getState().shared;
  if (!shared) return false;
  if (!(await confirmReplace([index], 'Importing the shared preset'))) return false;
  useEditor.getState().loadPresets([{ index, preset: shared.preset, labels: shared.labels }], 'copy', `Import shared preset into ${slotLabel(index)}`);
  useEditor.getState().selectSlot(index);
  useShareImport.setState({ shared: null });
  useUi.getState().toast(
    { tone: 'success', title: `Imported “${displayName(shared.preset.name)}” into ${slotLabel(index)}`, detail: 'Use Send changes to write it to the Pacer.' },
    5000,
  );
  return true;
}
