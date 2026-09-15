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
import { describeIdentity } from '../midi';
import { D6_INDEX, slotLabel } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { globalPendingParts, pendingSummary, useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button, ProgressBar, Segmented } from './controls';
import {
  IconBulb,
  IconChevron,
  IconClose,
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

const IconFollow = ({ size = 16 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true">
    <circle cx="12" cy="12" r="7" />
    <circle cx="12" cy="12" r="2.2" fill="currentColor" />
    <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
  </svg>
);

export function TopBar() {
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const slots = useEditor((s) => s.slots);
  const globals = useEditor((s) => s.globals);
  const canUndo = useEditor((s) => s.past.length > 0);
  const canRedo = useEditor((s) => s.future.length > 0);
  const undoLabel = useEditor((s) => s.past[s.past.length - 1]?.label);
  const redoLabel = useEditor((s) => s.future[s.future.length - 1]?.label);
  const preview = useEditor((s) => s.preview);
  const midiState = useDevice((s) => s.midi);
  const operation = useDevice((s) => s.operation);
  const view = useUi((s) => s.view);
  const theme = useUi((s) => s.theme);
  const follow = useUi((s) => s.follow);

  const connected = isConnected(midiState);
  const pending = pendingSummary(slots);
  const globalPending = globalPendingParts(globals).length;
  const pendingTotal = pending.messages + globalPending;
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
          { value: 'global', label: 'Global' },
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
        <Button disabled={!connected || busy} onClick={() => void readAllFromDevice()} title="Read every preset and the global settings (full backup)">
          Read all
        </Button>
        <Button
          variant="primary"
          icon={<IconSend />}
          disabled={pendingTotal === 0 || busy || preview !== null}
          badge={pendingTotal > 0 ? pendingTotal : undefined}
          title={
            pendingTotal > 0
              ? `${pendingTotal} message${pendingTotal === 1 ? '' : 's'} for ${[...pending.slots.map(slotLabel), ...(globalPending > 0 ? ['global settings'] : [])].join(', ')}`
              : 'No pending changes'
          }
          onClick={() => useUi.getState().openWrite({ slots: pending.slots, mode: 'changes', globals: true })}
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
        <Button
          variant="ghost"
          icon={<IconFollow />}
          aria-pressed={follow}
          className={follow ? 'is-toggled' : undefined}
          title={follow ? 'Hardware follow is on: pressing a switch on the Pacer selects it here' : 'Hardware follow is off'}
          onClick={() => useUi.getState().setFollow(!follow)}
        >
          Follow
        </Button>
        <Button variant="ghost" icon={<IconTemplate />} onClick={() => useUi.getState().openDialog('templates')}>
          Templates
        </Button>
        <Button variant="ghost" className="topbar__palette" aria-label="Command palette" title="Command palette (Ctrl+K)" onClick={() => useUi.getState().openDialog('palette')}>
          <kbd className="palette__kbd">Ctrl K</kbd>
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
          {item('Restore from backup…', () => useUi.getState().openDialog('restore'))}
        </div>
      )}
    </div>
  );
}

function ConnectionPill() {
  const { open, setOpen, ref } = usePopover();
  const m = useDevice((s) => s.midi);
  const identity = useDevice((s) => s.identity);
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
  const firmware = connected && identity.status === 'ok' ? identity.identity.versionText : null;

  return (
    <div className="popover-anchor" ref={ref}>
      <button
        type="button"
        className={`pill pill--${tone}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`MIDI connection: ${text}${firmware ? `, firmware ${firmware}` : ''}`}
        onClick={() => {
          if (m.access === 'idle') void connectMidi();
          setOpen(m.access !== 'idle' || !open ? !open : false);
        }}
      >
        <i className="pill__dot" aria-hidden="true" />
        <span className="pill__text">{text}</span>
        {firmware && <span className="pill__fw mono">fw {firmware}</span>}
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
  const identity = useDevice((s) => s.identity);
  const connected = isConnected(m);

  if (m.access === 'idle' || m.access === 'requesting') {
    return (
      <div className="connection__body">
        <p className="connection__title">Connect to the Pacer</p>
        <p className="hint">
          The browser will ask for permission to use MIDI devices with SysEx. After connecting, Pacer Studio sends one
          standard Identity Request to read the firmware version; nothing else is sent until you click Read or Send.
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
      <PortSelect label="Input (from Pacer)" ports={m.inputs} value={m.inputId} status={m.inputStatus} onChange={(id) => void midi.selectInput(id)} />
      <PortSelect label="Output (to Pacer)" ports={m.outputs} value={m.outputId} status={m.outputStatus} onChange={(id) => void midi.selectOutput(id)} />
      {connected && (
        <p className="hint connection__identity">
          {identity.status === 'ok'
            ? describeIdentity(identity.identity)
            : identity.status === 'pending'
              ? 'Asking the device for its identity…'
              : identity.status === 'none'
                ? 'No reply to the Identity Request (the firmware may not support it).'
                : ''}
        </p>
      )}
      {m.portError && (
        <p className="connection__error" role="alert">
          {m.portError}
        </p>
      )}
      <p className="hint">
        Use the Pacer&apos;s <b>port 1</b> (“PACER”), not “MIDIIN2/MIDIOUT2 (PACER)”.
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
