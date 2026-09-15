import { useEffect, useState, type DragEvent } from 'react';
import { connectMidi, midi, shouldAutoConnect } from './app/midi';
import { importFiles } from './app/operations';
import { openShareFromLocation } from './app/share';
import { useEditor } from './store/editor';
import { useUi } from './store/ui';
import { CheatSheet } from './ui/CheatSheet';
import { CommandPalette, ShortcutsDialog } from './ui/CommandPalette';
import { GlobalView } from './ui/GlobalView';
import { HelpDialog } from './ui/HelpDialog';
import { Inspector } from './ui/Inspector';
import { LedLab } from './ui/LedLab';
import { MidiMonitor } from './ui/MidiMonitor';
import { AboutDialog, ChoiceDialog, Toasts } from './ui/Overlays';
import { PresetBrowser } from './ui/PresetBrowser';
import { RestoreDialog } from './ui/RestoreDialog';
import { ShareImportDialog } from './ui/ShareImportDialog';
import { Stage } from './ui/Stage';
import { TemplatesDialog } from './ui/TemplatesDialog';
import { TopBar } from './ui/TopBar';
import { WriteDialog } from './ui/WriteDialog';

function isTextInput(el: Element | null): boolean {
  if (!el) return false;
  const tag = el.tagName;
  return (
    (tag === 'INPUT' && !['checkbox', 'radio', 'button'].includes((el as HTMLInputElement).type)) ||
    tag === 'TEXTAREA' ||
    tag === 'SELECT' ||
    (el as HTMLElement).isContentEditable
  );
}

export function App() {
  const theme = useUi((s) => s.theme);
  const view = useUi((s) => s.view);
  const browserOpen = useUi((s) => s.browserOpen);
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  useEffect(() => {
    // Only reconnect automatically when the user connected in an earlier visit.
    if (!midi.supported) void midi.connect();
    else if (shouldAutoConnect()) void connectMidi();
  }, []);

  useEffect(() => {
    void openShareFromLocation();
    const onHash = () => void openShareFromLocation();
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const ui = useUi.getState();
      const dialogOpen = !!document.querySelector('dialog[open]');
      const mod = e.ctrlKey || e.metaKey;
      const k = e.key.toLowerCase();
      if (mod && !e.altKey && k === 'k') {
        e.preventDefault();
        if (ui.dialog === 'palette') ui.closeDialog();
        else if (!dialogOpen) ui.openDialog('palette');
        return;
      }
      if (e.key === 'Escape' && ui.browserOpen && !dialogOpen) {
        ui.setBrowserOpen(false);
        return;
      }
      if (e.key === '?' && !mod && !isTextInput(document.activeElement) && !dialogOpen) {
        e.preventDefault();
        ui.openDialog('shortcuts');
        return;
      }
      if (!mod || e.altKey) return;
      if ((k === 'z' || k === 'y') && !isTextInput(document.activeElement) && !dialogOpen) {
        e.preventDefault();
        if (k === 'y' || e.shiftKey) useEditor.getState().redo();
        else useEditor.getState().undo();
      }
    };
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (useEditor.getState().past.length > 0) e.preventDefault();
    };
    window.addEventListener('keydown', onKey);
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('beforeunload', onBeforeUnload);
    };
  }, []);

  const hasFiles = (e: DragEvent) => e.dataTransfer.types.includes('Files');

  return (
    <>
      <div
        className={`app view-${view}${browserOpen ? ' browser-open' : ''}`}
        onDragOver={(e) => {
          if (hasFiles(e)) {
            e.preventDefault();
            setDragging(true);
          }
        }}
        onDragLeave={(e) => {
          if (e.currentTarget === e.target || !e.currentTarget.contains(e.relatedTarget as Node)) setDragging(false);
        }}
        onDrop={(e) => {
          if (!hasFiles(e)) return;
          e.preventDefault();
          setDragging(false);
          void importFiles(Array.from(e.dataTransfer.files));
        }}
      >
        <a className="skip-link" href="#inspector">
          Skip to inspector
        </a>
        <TopBar />
        <div className="app__main">
          <PresetBrowser />
          {browserOpen && <div className="drawer-scrim" aria-hidden="true" onClick={() => useUi.getState().setBrowserOpen(false)} />}
          {view === 'editor' && (
            <>
              <Stage />
              <div id="inspector" className="app__inspector">
                <Inspector />
              </div>
            </>
          )}
          {view === 'global' && <GlobalView />}
          {view === 'ledlab' && <LedLab />}
        </div>
        <MidiMonitor />

        <WriteDialog />
        <TemplatesDialog />
        <RestoreDialog />
        <ShareImportDialog />
        <AboutDialog />
        <CommandPalette />
        <ShortcutsDialog />
        <HelpDialog />
        <ChoiceDialog />
        <Toasts />
        {dragging && (
          <div className="dropzone" aria-hidden="true">
            <div className="dropzone__panel">Drop .syx or .json files to import</div>
          </div>
        )}
      </div>
      <CheatSheet />
    </>
  );
}
