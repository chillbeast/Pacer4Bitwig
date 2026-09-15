import { create } from 'zustand';

export type Theme = 'dark' | 'light';
export type View = 'editor' | 'ledlab';
export type DialogId = 'templates' | 'about' | 'write' | null;
export type ToastTone = 'info' | 'success' | 'warning' | 'error';

export interface Toast {
  id: number;
  tone: ToastTone;
  title: string;
  detail?: string;
  action?: { label: string; run: () => void };
}

export interface WriteRequest {
  /** Slots proposed for writing. */
  slots: number[];
  /** "changes": pending edits; "slot": explicit full/diff write of the given slots. */
  mode: 'changes' | 'slot';
}

export interface ChoiceOption {
  id: string;
  label: string;
  tone?: 'primary' | 'danger' | 'default';
}

export interface ChoiceRequest {
  title: string;
  body: string;
  choices: ChoiceOption[];
  cancelLabel?: string;
  resolve: (id: string | null) => void;
}

const THEME_KEY = 'pacer-studio.theme';

function loadTheme(): Theme {
  try {
    const t = localStorage.getItem(THEME_KEY);
    if (t === 'light' || t === 'dark') return t;
  } catch {
    // storage unavailable
  }
  return 'dark';
}

export interface UiState {
  theme: Theme;
  view: View;
  monitorOpen: boolean;
  ledPreview: 'on' | 'off';
  /** Step whose LED config is previewed on the hardware rendering (0-based). */
  previewStep: number;
  dialog: DialogId;
  writeRequest: WriteRequest | null;
  choice: ChoiceRequest | null;
  toasts: Toast[];

  toggleTheme: () => void;
  setView: (view: View) => void;
  setMonitorOpen: (open: boolean) => void;
  setLedPreview: (mode: 'on' | 'off') => void;
  setPreviewStep: (step: number) => void;
  openDialog: (dialog: Exclude<DialogId, null | 'write'>) => void;
  openWrite: (request: WriteRequest) => void;
  closeDialog: () => void;
  ask: (request: Omit<ChoiceRequest, 'resolve'>) => Promise<string | null>;
  resolveChoice: (id: string | null) => void;
  toast: (toast: Omit<Toast, 'id'>, timeoutMs?: number) => number;
  dismissToast: (id: number) => void;
}

let toastId = 0;

export const useUi = create<UiState>()((set, get) => ({
  theme: loadTheme(),
  view: 'editor',
  monitorOpen: false,
  ledPreview: 'on',
  previewStep: 0,
  dialog: null,
  writeRequest: null,
  choice: null,
  toasts: [],

  toggleTheme: () => {
    const theme: Theme = get().theme === 'dark' ? 'light' : 'dark';
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch {
      // ignore
    }
    set({ theme });
  },
  setView: (view) => set({ view }),
  setMonitorOpen: (monitorOpen) => set({ monitorOpen }),
  setLedPreview: (ledPreview) => set({ ledPreview }),
  setPreviewStep: (previewStep) => set({ previewStep: Math.max(0, Math.min(5, previewStep)) }),
  openDialog: (dialog) => set({ dialog }),
  openWrite: (writeRequest) => set({ dialog: 'write', writeRequest }),
  closeDialog: () => set({ dialog: null, writeRequest: null }),
  ask: (request) =>
    new Promise<string | null>((resolve) => {
      get().choice?.resolve(null);
      set({ choice: { ...request, resolve } });
    }),
  resolveChoice: (id) => {
    const choice = get().choice;
    set({ choice: null });
    choice?.resolve(id);
  },
  toast: (toast, timeoutMs = 6000) => {
    const id = ++toastId;
    set((s) => ({ toasts: [...s.toasts.slice(-4), { ...toast, id }] }));
    if (timeoutMs > 0) setTimeout(() => get().dismissToast(id), timeoutMs);
    return id;
  },
  dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));

/** Ask a yes/no question; resolves true when confirmed. */
export async function confirmAction(
  title: string,
  body: string,
  confirmLabel: string,
  tone: 'primary' | 'danger' = 'primary',
): Promise<boolean> {
  const id = await useUi.getState().ask({ title, body, choices: [{ id: 'ok', label: confirmLabel, tone }] });
  return id === 'ok';
}
