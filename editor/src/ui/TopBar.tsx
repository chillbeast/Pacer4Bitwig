import { useEffect, useRef, useState } from 'react';
import { pickFiles } from '../app/files';
import { connectMidi, disconnectMidi, midi } from '../app/midi';
import {
  downloadSessionBackup,
  exportAllSyx,
  exportSlotSyx,
  exportSlotsJson,
  importFiles,
  loadFactoryPresets,
  readAllFromDevice,
  readAndDownloadBackup,
  readPresetFromDevice,
} from '../app/operations';
import { D6_INDEX, slotLabel } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { pendingSummary, useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button, ProgressBar, Segmented } from './controls';
import {
  IconBulb,
  IconChevron,
  IconClose,
  IconDownload,
  IconInfo,
  IconMoon,
  IconRead,
  IconRedo,
  IconSend,
  IconSun,
  IconTemplate,
  IconUndo,
  IconUpload,
} from './icons';

function usePopover() {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);
  return { open, setOpen, ref };
}

export function TopBar() {
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const slots = useEditor((s) => s.slots);
  const canUndo = useEditor((s) => s.past.length > 0);
  const canRedo = useEditor((s) => s.future.length > 0);
  const undoLabel = useEditor((s) => s.past[s.past.length - 1]?.label);
  const redoLabel = useEditor((s) => s.future[s.future.length - 1]?.label);
  const preview = useEditor((s) => s.preview);
  const midiState = useDevice((s) => s.midi);
  const operation = useDevice((s) => s.operation);
  const view = useUi((s) => s.view);
  const theme = useUi((s) => s.theme);

  const connected = isConnected(midiState);
  const pending = pendingSummary(slots);
  const busy = operation !== null;

  return (
    <header className="topbar">
      <div className="topbar__brand">
        <span className="brand-mark" aria-hidden="true">
          <i />
          <i />
        </span>
        <span className="brand-name">
          Pacer <b>Studio</b>
        </span>
      </div>

      <ConnectionPill />

      <Segmented
        label="View"
        size="sm"
        value={view}
        onChange={(v) => useUi.getState().setView(v)}
        options={[
          { value: 'editor', label: 'Editor' },
          { value: 'ledlab', label: <><IconBulb size={14} /> LED Lab</> },
        ]}
      />

      <div className="topbar__center">
        {operation ? (
          <div className="op" role="status" aria-live="polite">
            <span className="op__label">{operation.label}</span>
            <ProgressBar value={operation.done} max={operation.total} label={operation.label} />
            <span className="op__count mono">
              {operation.done}/{operation.total}
            </span>
            {operation.cancel && (
              <Button size="sm" variant="ghost" icon={<IconClose size={14} />} aria-label="Cancel" onClick={operation.cancel} />
            )}
          </div>
        ) : null}
      </div>

      <div className="topbar__group" role="group" aria-label="Device">
        <Button
          icon={<IconRead />}
          disabled={!connected || busy}
          title={selectedSlot === D6_INDEX ? 'D6 cannot be read on its own — use Read all' : `Read ${slotLabel(selectedSlot)} from the Pacer`}
          onClick={() => void readPresetFromDevice(selectedSlot)}
        >
          Read {slotLabel(selectedSlot)}
        </Button>
        <Button disabled={!connected || busy} onClick={() => void readAllFromDevice()} title="Read every preset (full backup)">
          Read all
        </Button>
        <Button
          variant="primary"
          icon={<IconSend />}
          disabled={pending.messages === 0 || busy || preview !== null}
          badge={pending.messages > 0 ? pending.messages : undefined}
          title={
            pending.messages > 0
              ? `${pending.messages} message${pending.messages === 1 ? '' : 's'} for ${pending.slots.map(slotLabel).join(', ')}`
              : 'No pending changes'
          }
          onClick={() => useUi.getState().openWrite({ slots: pending.slots, mode: 'changes' })}
        >
          Send changes
        </Button>
      </div>

      <div className="topbar__group" role="group" aria-label="Edit">
        <Button
          variant="ghost"
          icon={<IconUndo />}
          aria-label="Undo"
          title={undoLabel ? `Undo: ${undoLabel} (Ctrl+Z)` : 'Undo (Ctrl+Z)'}
          disabled={!canUndo}
          onClick={() => useEditor.getState().undo()}
        />
        <Button
          variant="ghost"
          icon={<IconRedo />}
          aria-label="Redo"
          title={redoLabel ? `Redo: ${redoLabel} (Ctrl+Shift+Z)` : 'Redo (Ctrl+Shift+Z)'}
          disabled={!canRedo}
          onClick={() => useEditor.getState().redo()}
        />
      </div>

      <FileMenu />

      <div className="topbar__group" role="group" aria-label="App">
        <Button variant="ghost" icon={<IconTemplate />} onClick={() => useUi.getState().openDialog('templates')}>
          Templates
        </Button>
        <Button
          variant="ghost"
          icon={theme === 'dark' ? <IconSun /> : <IconMoon />}
          aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
          title="Toggle theme"
          onClick={() => useUi.getState().toggleTheme()}
        />
        <Button variant="ghost" icon={<IconInfo />} aria-label="About Pacer Studio" onClick={() => useUi.getState().openDialog('about')} />
      </div>
    </header>
  );
}

function FileMenu() {
  const { open, setOpen, ref } = usePopover();
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const hasSelected = useEditor((s) => s.slots[s.selectedSlot].preset !== null);
  const hasAny = useEditor((s) => s.slots.some((slot) => slot.preset !== null));
  const connected = useDevice((s) => isConnected(s.midi));
  const backup = useDevice((s) => s.backup);
  const busy = useDevice((s) => s.operation !== null);

  const item = (label: string, action: () => void, disabled = false, hint?: string) => (
    <button
      type="button"
      role="menuitem"
      className="menu__item"
      disabled={disabled}
      onClick={() => {
        setOpen(false);
        action();
      }}
    >
      <span>{label}</span>
      {hint && <span className="menu__hint">{hint}</span>}
    </button>
  );

  return (
    <div className="popover-anchor" ref={ref}>
      <Button variant="ghost" icon={<IconUpload />} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(!open)}>
        Files <IconChevron size={12} />
      </Button>
      {open && (
        <div className="menu menu--anchored" role="menu" aria-label="Files">
          <div className="menu__title">Import</div>
          {item('Import .syx or .json…', () => void pickFiles('.syx,.bin,.json,application/json').then(importFiles), false, 'or drop')}
          {item('Load factory presets', () => void loadFactoryPresets())}
          <div className="menu__title">Export</div>
          {item(`Preset ${slotLabel(selectedSlot)} as .syx`, () => exportSlotSyx(selectedSlot), !hasSelected)}
          {item('All presets as .syx', exportAllSyx, !hasAny)}
          {item('All presets as .json', () => exportSlotsJson(), !hasAny)}
          <div className="menu__title">Backup</div>
          {backup
            ? item('Download session backup', downloadSessionBackup, false, new Date(backup.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
            : null}
          {item('Read & download full backup', () => void readAndDownloadBackup(), !connected || busy)}
        </div>
      )}
    </div>
  );
}

function ConnectionPill() {
  const { open, setOpen, ref } = usePopover();
  const m = useDevice((s) => s.midi);
  const connected = isConnected(m);

  let tone: 'ok' | 'warn' | 'error' | 'idle' = 'idle';
  let text = 'Connect MIDI';
  if (m.access === 'unsupported') {
    tone = 'error';
    text = 'No Web MIDI';
  } else if (m.access === 'denied') {
    tone = 'error';
    text = 'MIDI blocked';
  } else if (m.access === 'error') {
    tone = 'error';
    text = 'MIDI error';
  } else if (m.access === 'requesting') {
    tone = 'warn';
    text = 'Requesting access…';
  } else if (m.access === 'ready') {
    if (connected) {
      tone = 'ok';
      text = m.outputName ?? 'Connected';
    } else if (m.inputStatus === 'busy' || m.outputStatus === 'busy') {
      tone = 'error';
      text = 'Port busy';
    } else if (m.inputStatus === 'disconnected' || m.outputStatus === 'disconnected') {
      tone = 'warn';
      text = 'Pacer unplugged';
    } else if (m.inputStatus === 'opening' || m.outputStatus === 'opening') {
      tone = 'warn';
      text = 'Opening…';
    } else {
      tone = 'warn';
      text = 'No Pacer found';
    }
  }

  return (
    <div className="popover-anchor" ref={ref}>
      <button
        type="button"
        className={`pill pill--${tone}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`MIDI connection: ${text}`}
        onClick={() => {
          if (m.access === 'idle') void connectMidi();
          setOpen(m.access !== 'idle' || !open ? !open : false);
        }}
      >
        <i className="pill__dot" aria-hidden="true" />
        <span className="pill__text">{text}</span>
        <IconChevron size={12} />
      </button>
      {open && (
        <div className="popover connection" role="dialog" aria-label="MIDI connection">
          <ConnectionPanel />
        </div>
      )}
    </div>
  );
}

function ConnectionPanel() {
  const m = useDevice((s) => s.midi);
  const connected = isConnected(m);

  if (m.access === 'idle' || m.access === 'requesting') {
    return (
      <div className="connection__body">
        <p className="connection__title">Connect to the Pacer</p>
        <p className="hint">
          The browser will ask for permission to use MIDI devices with SysEx. Nothing is sent to the Pacer until you click
          Read or Send.
        </p>
        <Button variant="primary" disabled={m.access === 'requesting'} onClick={() => void connectMidi()}>
          {m.access === 'requesting' ? 'Waiting for permission…' : 'Allow MIDI access'}
        </Button>
      </div>
    );
  }

  if (m.access !== 'ready') {
    return (
      <div className="connection__body">
        <p className="connection__title">{m.access === 'unsupported' ? 'Web MIDI unavailable' : 'MIDI access problem'}</p>
        <p className="hint">{m.accessError}</p>
        <p className="hint">You can still edit presets offline and import/export .syx and .json files.</p>
        {m.access !== 'unsupported' && <Button onClick={() => void connectMidi()}>Try again</Button>}
      </div>
    );
  }

  return (
    <div className="connection__body">
      <p className="connection__title">
        <i className={`status-dot${connected ? ' is-ok' : ''}`} /> {connected ? 'Pacer connected' : 'Choose the Pacer ports'}
      </p>
      <PortSelect
        label="Input (from Pacer)"
        ports={m.inputs}
        value={m.inputId}
        status={m.inputStatus}
        onChange={(id) => void midi.selectInput(id)}
      />
      <PortSelect
        label="Output (to Pacer)"
        ports={m.outputs}
        value={m.outputId}
        status={m.outputStatus}
        onChange={(id) => void midi.selectOutput(id)}
      />
      {m.portError && (
        <p className="connection__error" role="alert">
          {m.portError}
        </p>
      )}
      <p className="hint">
        Use the Pacer&apos;s <b>port 1</b> (“PACER”), not “MIDIIN2/MIDIOUT2 (PACER)”. On Windows a port can only be used by one
        application at a time.
      </p>
      <div className="connection__actions">
        <Button size="sm" onClick={() => void midi.retry()}>
          Retry
        </Button>
        <Button size="sm" variant="ghost" onClick={() => void disconnectMidi()}>
          Disconnect
        </Button>
      </div>
    </div>
  );
}

function PortSelect({
  label,
  ports,
  value,
  status,
  onChange,
}: {
  label: string;
  ports: { id: string; name: string; primary: boolean; connected: boolean }[];
  value: string | null;
  status: string;
  onChange: (id: string | null) => void;
}) {
  return (
    <label className="field">
      <span className="field__label">
        {label} <span className={`port-status port-status--${status}`}>{status === 'none' ? '' : status}</span>
      </span>
      <span className="select">
        <select className="input select__input" value={value ?? ''} onChange={(e) => onChange(e.target.value || null)}>
          <option value="">— none —</option>
          {ports.map((p) => (
            <option key={p.id} value={p.id} disabled={!p.connected}>
              {p.name}
              {p.primary ? '  ★ Pacer port 1' : ''}
            </option>
          ))}
        </select>
      </span>
    </label>
  );
}

export { IconDownload };
