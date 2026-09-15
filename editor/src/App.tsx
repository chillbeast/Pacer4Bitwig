import { useEffect, useState, type DragEvent } from 'react';
import { connectMidi, midi, shouldAutoConnect } from './app/midi';
import { importFiles } from './app/operations';
import { useEditor } from './store/editor';
import { useUi } from './store/ui';
import { Inspector } from './ui/Inspector';
import { LedLab } from './ui/LedLab';
import { MidiMonitor } from './ui/MidiMonitor';
import { AboutDialog, ChoiceDialog, Toasts } from './ui/Overlays';
import { PresetBrowser } from './ui/PresetBrowser';
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
    const onKey = (e: KeyboardEvent) => {
      const mod = e.ctrlKey || e.metaKey;
      if (!mod || e.altKey) return;
      const k = e.key.toLowerCase();
      if ((k === 'z' || k === 'y') && !isTextInput(document.activeElement) && !document.querySelector('dialog[open]')) {
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
    <div
      className={`app view-${view}`}
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
        {view === 'editor' ? (
          <>
            <Stage />
            <div id="inspector" className="app__inspector">
              <Inspector />
            </div>
          </>
        ) : (
          <LedLab />
        )}
      </div>
      <MidiMonitor />

      <WriteDialog />
      <TemplatesDialog />
      <AboutDialog />
      <ChoiceDialog />
      <Toasts />
      {dragging && (
        <div className="dropzone" aria-hidden="true">
          <div className="dropzone__panel">Drop .syx or .json files to import</div>
        </div>
      )}
    </div>
  );
}
