import { useEffect, useMemo, useRef, useState } from 'react';
import { pickFiles } from '../app/files';
import { connectMidi } from '../app/midi';
import {
  downloadSessionBackup,
  exportAllSyx,
  exportSlotSyx,
  exportSlotsJson,
  importFiles,
  loadFactoryPresets,
  readAllFromDevice,
  readAndDownloadBackup,
  readGlobalsFromDevice,
  readPresetFromDevice,
} from '../app/operations';
import { copyShareLink } from '../app/share';
import { ALL_SLOTS, D6_INDEX, displayName, slotLabel } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { globalPendingParts, pendingSummary, useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { printCheatSheet } from './CheatSheet';
import { Dialog } from './Dialog';

export interface Command {
  id: string;
  label: string;
  group: string;
  shortcut?: string;
  keywords?: string;
  disabled?: boolean;
  run: () => void;
}

export const SHORTCUTS: [string, string][] = [
  ['Ctrl+K', 'Command palette'],
  ['?', 'This shortcut overview'],
  ['Ctrl+Z / Ctrl+Shift+Z', 'Undo / redo'],
  ['Arrow keys', 'Move between switches (hardware view) or presets (browser)'],
  ['Ctrl+C / Ctrl+V / Ctrl+D', 'Copy, paste, duplicate the focused preset'],
  ['Shift+F10', 'Preset actions menu'],
  ['Esc', 'Close dialogs and menus'],
  ['Drag & drop', 'Copy a preset onto another slot (Alt: swap); drop .syx/.json files to import'],
];

function useCommands(): Command[] {
  const editor = useEditor();
  const device = useDevice();
  const ui = useUi();
  const connected = isConnected(device.midi);
  const busy = device.operation !== null;
  const slot = editor.selectedSlot;
  const hasPreset = editor.slots[slot].preset !== null;
  const pending = pendingSummary(editor.slots);
  const globalPending = globalPendingParts(editor.globals).length;
  const label = slotLabel(slot);
  const close = () => ui.closeDialog();
  const then = (fn: () => void) => () => {
    close();
    fn();
  };

  const commands: Command[] = [
    { id: 'send', group: 'Pacer', label: `Send changes (${pending.messages + globalPending} messages)`, disabled: pending.messages + globalPending === 0 || busy, run: () => ui.openWrite({ slots: pending.slots, mode: 'changes', globals: true }) },
    { id: 'read', group: 'Pacer', label: `Read ${label} from the Pacer`, disabled: !connected || busy || slot === D6_INDEX, run: then(() => void readPresetFromDevice(slot)) },
    { id: 'read-all', group: 'Pacer', label: 'Read all presets', disabled: !connected || busy, run: then(() => void readAllFromDevice()) },
    { id: 'read-globals', group: 'Pacer', label: 'Read global settings', disabled: !connected || busy, run: then(() => void readGlobalsFromDevice()) },
    { id: 'write-slot', group: 'Pacer', label: `Write ${label} to the Pacer…`, disabled: !hasPreset || busy, run: () => ui.openWrite({ slots: [slot], mode: 'slot' }) },
    { id: 'backup', group: 'Pacer', label: 'Read & download full backup', disabled: !connected || busy, run: then(() => void readAndDownloadBackup()) },
    { id: 'session-backup', group: 'Pacer', label: 'Download session backup', disabled: !device.backup, run: then(downloadSessionBackup) },
    { id: 'restore', group: 'Pacer', label: 'Restore from backup…', keywords: 'syx wizard', run: () => ui.openDialog('restore') },
    { id: 'connect', group: 'Pacer', label: 'Connect MIDI', disabled: device.midi.access === 'ready', run: then(() => void connectMidi()) },
    { id: 'follow', group: 'Pacer', label: `${ui.follow ? 'Disable' : 'Enable'} hardware follow`, keywords: 'select press', run: then(() => ui.setFollow(!ui.follow)) },

    { id: 'import', group: 'Files', label: 'Import .syx or .json…', run: then(() => void pickFiles('.syx,.bin,.json').then(importFiles)) },
    { id: 'factory', group: 'Files', label: 'Load factory presets', run: then(() => void loadFactoryPresets()) },
    { id: 'export-slot', group: 'Files', label: `Export ${label} as .syx`, disabled: !hasPreset, run: then(() => exportSlotSyx(slot)) },
    { id: 'export-all', group: 'Files', label: 'Export all presets as .syx', run: then(exportAllSyx) },
    { id: 'export-json', group: 'Files', label: 'Export all presets as .json', run: then(() => exportSlotsJson()) },
    { id: 'share', group: 'Files', label: `Copy share link for ${label}`, disabled: !hasPreset, run: then(() => void copyShareLink(slot)) },
    { id: 'print', group: 'Files', label: `Print cheat sheet for ${label}`, disabled: !hasPreset, run: then(() => printCheatSheet(slot)) },
    { id: 'templates', group: 'Files', label: 'Templates…', run: () => ui.openDialog('templates') },

    { id: 'undo', group: 'Edit', label: 'Undo', shortcut: 'Ctrl+Z', disabled: editor.past.length === 0, run: then(editor.undo) },
    { id: 'redo', group: 'Edit', label: 'Redo', shortcut: 'Ctrl+Shift+Z', disabled: editor.future.length === 0, run: then(editor.redo) },
    { id: 'copy', group: 'Edit', label: `Copy ${label}`, disabled: !hasPreset, run: then(() => editor.copySlot(slot)) },
    { id: 'paste', group: 'Edit', label: `Paste into ${label}`, disabled: !editor.clipboard, run: then(() => editor.pasteSlot(slot)) },
    { id: 'duplicate', group: 'Edit', label: `Duplicate ${label} to the next free slot`, disabled: !hasPreset, run: then(() => void editor.duplicateSlot(slot)) },

    { id: 'view-editor', group: 'View', label: 'Editor', run: then(() => ui.setView('editor')) },
    { id: 'view-global', group: 'View', label: 'Global settings', run: then(() => ui.setView('global')) },
    { id: 'view-ledlab', group: 'View', label: 'LED Lab', run: then(() => ui.setView('ledlab')) },
    { id: 'monitor', group: 'View', label: `${ui.monitorOpen ? 'Close' : 'Open'} MIDI monitor`, run: then(() => ui.setMonitorOpen(!ui.monitorOpen)) },
    { id: 'theme', group: 'View', label: `Switch to ${ui.theme === 'dark' ? 'light' : 'dark'} theme`, run: then(ui.toggleTheme) },
    { id: 'shortcuts', group: 'View', label: 'Keyboard shortcuts', shortcut: '?', run: () => ui.openDialog('shortcuts') },
    { id: 'about', group: 'View', label: 'About Pacer Studio', run: () => ui.openDialog('about') },
  ];

  for (const i of ALL_SLOTS) {
    const p = editor.slots[i].preset;
    commands.push({
      id: `goto-${i}`,
      group: 'Go to preset',
      label: `${slotLabel(i)}${p ? ` — ${displayName(p.name)}` : ''}`,
      keywords: `go preset ${slotLabel(i)} ${i === 0 ? 'current' : ''}`,
      run: then(() => {
        editor.selectSlot(i);
        ui.setView('editor');
      }),
    });
  }
  return commands;
}

function score(command: Command, query: string): number {
  if (!query) return command.group === 'Go to preset' ? 0 : 1;
  const hay = `${command.label} ${command.group} ${command.keywords ?? ''}`.toLowerCase();
  const tokens = query.toLowerCase().split(/\s+/).filter(Boolean);
  if (!tokens.every((t) => hay.includes(t))) return 0;
  return (command.label.toLowerCase().startsWith(tokens[0]) ? 3 : 2) - (command.disabled ? 1 : 0);
}

export function CommandPalette() {
  const open = useUi((s) => s.dialog === 'palette');
  if (!open) return null;
  return <PaletteBody />;
}

function PaletteBody() {
  const commands = useCommands();
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const listRef = useRef<HTMLUListElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // showModal() runs after this component mounts and moves focus to the first focusable element (the close button);
  // focus the search field on the next frame instead.
  useEffect(() => {
    const id = requestAnimationFrame(() => inputRef.current?.focus());
    return () => cancelAnimationFrame(id);
  }, []);

  const results = useMemo(
    () =>
      commands
        .map((c) => ({ c, s: score(c, query) }))
        .filter((r) => r.s > 0)
        .sort((a, b) => b.s - a.s)
        .map((r) => r.c)
        .slice(0, 40),
    [commands, query],
  );

  useEffect(() => setActive(0), [query]);
  useEffect(() => {
    listRef.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [active]);

  const run = (c: Command | undefined) => {
    if (!c || c.disabled) return;
    c.run();
  };

  return (
    <Dialog open onClose={() => useUi.getState().closeDialog()} size="md" title="Commands">
      <div className="palette">
        <input
          ref={inputRef}
          className="input palette__input"
          placeholder="Type a command or a preset (e.g. “read all”, “D1”)"
          aria-label="Search commands"
          aria-controls="palette-list"
          aria-activedescendant={results[active] ? `palette-${results[active].id}` : undefined}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') {
              e.preventDefault();
              setActive((a) => Math.min(results.length - 1, a + 1));
            } else if (e.key === 'ArrowUp') {
              e.preventDefault();
              setActive((a) => Math.max(0, a - 1));
            } else if (e.key === 'Enter') {
              e.preventDefault();
              run(results[active]);
            }
          }}
        />
        <ul className="palette__list" id="palette-list" role="listbox" ref={listRef}>
          {results.map((c, i) => (
            <li
              key={c.id}
              id={`palette-${c.id}`}
              data-index={i}
              role="option"
              aria-selected={i === active}
              aria-disabled={c.disabled || undefined}
              className={`palette__item${i === active ? ' is-active' : ''}${c.disabled ? ' is-disabled' : ''}`}
              onMouseEnter={() => setActive(i)}
              onClick={() => run(c)}
            >
              <span className="palette__group">{c.group}</span>
              <span className="palette__label">{c.label}</span>
              {c.shortcut && <kbd className="palette__kbd">{c.shortcut}</kbd>}
            </li>
          ))}
          {results.length === 0 && <li className="hint palette__empty">No matching command.</li>}
        </ul>
      </div>
    </Dialog>
  );
}

export function ShortcutsDialog() {
  const open = useUi((s) => s.dialog === 'shortcuts');
  return (
    <Dialog open={open} onClose={() => useUi.getState().closeDialog()} size="sm" title="Keyboard shortcuts">
      <dl className="shortcuts">
        {SHORTCUTS.map(([k, v]) => (
          <div key={k}>
            <dt>
              <kbd className="palette__kbd">{k}</kbd>
            </dt>
            <dd>{v}</dd>
          </div>
        ))}
      </dl>
    </Dialog>
  );
}
