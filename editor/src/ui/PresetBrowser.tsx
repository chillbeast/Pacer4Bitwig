import { useEffect, useRef, useState, type DragEvent, type KeyboardEvent } from 'react';
import { exportSlotSyx, exportSlotsJson, readPresetFromDevice } from '../app/operations';
import { copyShareLink } from '../app/share';
import { printCheatSheet } from './CheatSheet';
import { BANKS, D6_INDEX, displayName, slotLabel } from '../pacer';
import { isSlotEdited, isSlotInSync, slotPendingParts, slotWriteParts, useEditor, type Slot } from '../store/editor';
import { useDevice, isConnected } from '../store/device';
import { useUi } from '../store/ui';
import { IconDevice, IconFile, IconMore } from './icons';

const DRAG_TYPE = 'application/x-pacer-slot';

export function PresetBrowser() {
  const slots = useEditor((s) => s.slots);
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const [menu, setMenu] = useState<{ index: number; x: number; y: number } | null>(null);
  const [swapFrom, setSwapFrom] = useState<number | null>(null);
  const [dropTarget, setDropTarget] = useState<number | null>(null);
  const gridRef = useRef<HTMLDivElement>(null);

  const choose = (index: number) => {
    const state = useEditor.getState();
    if (swapFrom !== null) {
      if (swapFrom !== index) state.swapSlots(swapFrom, index);
      setSwapFrom(null);
    }
    state.selectSlot(index);
  };

  const focusSlot = (index: number) => gridRef.current?.querySelector<HTMLElement>(`[data-slot="${index}"]`)?.focus();

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement;
    const index = Number(target.dataset.slot);
    if (!Number.isInteger(index)) return;
    const state = useEditor.getState();
    const mod = event.ctrlKey || event.metaKey;
    let next: number | null = null;
    if (event.key === 'ArrowRight') next = index === 0 ? 1 : Math.min(24, index + 1);
    if (event.key === 'ArrowLeft') next = index <= 1 ? 0 : index - 1;
    if (event.key === 'ArrowDown') next = index === 0 ? 1 : index + 6 <= 24 ? index + 6 : index;
    if (event.key === 'ArrowUp') next = index === 0 ? 0 : index - 6 >= 1 ? index - 6 : 0;
    if (next !== null) {
      event.preventDefault();
      choose(next);
      focusSlot(next);
      return;
    }
    if (mod && event.key.toLowerCase() === 'c') {
      event.preventDefault();
      state.copySlot(index);
      useUi.getState().toast({ tone: 'info', title: `Copied ${slotLabel(index)}` }, 1800);
    } else if (mod && event.key.toLowerCase() === 'v') {
      event.preventDefault();
      state.pasteSlot(index);
    } else if (mod && event.key.toLowerCase() === 'd') {
      event.preventDefault();
      const target = state.duplicateSlot(index);
      if (target !== null) focusSlot(target);
    } else if (event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) {
      event.preventDefault();
      const rect = target.getBoundingClientRect();
      setMenu({ index, x: rect.left + rect.width / 2, y: rect.bottom });
    } else if (event.key === 'Escape' && swapFrom !== null) {
      setSwapFrom(null);
    }
  };

  const tileProps = (index: number) => ({
    index,
    slot: slots[index],
    selected: index === selectedSlot,
    swapSource: swapFrom === index,
    dropTarget: dropTarget === index,
    onChoose: () => choose(index),
    onMenu: (x: number, y: number) => setMenu({ index, x, y }),
    onDragOverSlot: (over: boolean) => setDropTarget(over ? index : null),
  });

  return (
    <nav className="browser" aria-label="Preset browser">
      <div className="browser__header">
        <h2 className="panel-title">Presets</h2>
        {swapFrom !== null ? (
          <button type="button" className="link-button" onClick={() => setSwapFrom(null)}>
            Cancel swap
          </button>
        ) : (
          <span className="browser__legend">
            <i className="dot dot--edited" /> edited
          </span>
        )}
      </div>
      {swapFrom !== null && (
        <p className="browser__banner" role="status">
          Pick the slot to swap with {slotLabel(swapFrom)}
        </p>
      )}
      <div className="browser__grid" ref={gridRef} onKeyDown={onKeyDown}>
        <div className="browser__current">
          <SlotTile {...tileProps(0)} wide />
        </div>
        {BANKS.map((bank, b) => (
          <div className="bank" key={bank} role="group" aria-label={`Bank ${bank}`}>
            <span className="bank__label" aria-hidden="true">
              {bank}
            </span>
            <div className="bank__slots">
              {Array.from({ length: 6 }, (_, n) => (
                <SlotTile key={n} {...tileProps(b * 6 + n + 1)} />
              ))}
            </div>
          </div>
        ))}
      </div>
      <p className="browser__tip">Drag a preset onto another slot to copy it · Alt-drop to swap</p>
      {menu && (
        <SlotMenu
          index={menu.index}
          x={menu.x}
          y={menu.y}
          onClose={() => {
            setMenu(null);
            focusSlot(menu.index);
          }}
          onSwap={() => {
            setSwapFrom(menu.index);
            setMenu(null);
          }}
        />
      )}
    </nav>
  );
}

function SlotTile({
  index,
  slot,
  selected,
  swapSource,
  dropTarget,
  wide,
  onChoose,
  onMenu,
  onDragOverSlot,
}: {
  index: number;
  slot: Slot;
  selected: boolean;
  swapSource: boolean;
  dropTarget: boolean;
  wide?: boolean;
  onChoose: () => void;
  onMenu: (x: number, y: number) => void;
  onDragOverSlot: (over: boolean) => void;
}) {
  const edited = isSlotEdited(slot);
  const inSync = isSlotInSync(slot);
  const pending = edited ? slotPendingParts(slot, index).length : 0;
  const name = slot.preset ? displayName(slot.preset.name) : '';
  const label = slotLabel(index);

  const status = [
    slot.preset ? `“${name || 'unnamed'}”` : 'empty',
    edited ? `edited, ${pending} message${pending === 1 ? '' : 's'} pending` : null,
    slot.device ? (inSync ? 'matches the Pacer' : 'loaded from Pacer') : slot.source === 'file' ? 'from file' : null,
    index === D6_INDEX ? 'cannot be read back individually' : null,
  ]
    .filter(Boolean)
    .join(', ');

  const onDragStart = (e: DragEvent<HTMLButtonElement>) => {
    if (!slot.preset) {
      e.preventDefault();
      return;
    }
    e.dataTransfer.setData(DRAG_TYPE, String(index));
    e.dataTransfer.effectAllowed = 'copyMove';
  };
  const onDrop = (e: DragEvent<HTMLButtonElement>) => {
    const raw = e.dataTransfer.getData(DRAG_TYPE);
    onDragOverSlot(false);
    if (!raw) return;
    e.preventDefault();
    const from = Number(raw);
    if (from === index) return;
    const state = useEditor.getState();
    if (e.altKey) state.swapSlots(from, index);
    else state.copySlotTo(from, index);
    state.selectSlot(index);
  };

  return (
    <button
      type="button"
      data-slot={index}
      className={[
        'slot',
        wide ? 'slot--wide' : '',
        selected ? 'is-selected' : '',
        slot.preset ? '' : 'is-empty',
        edited ? 'is-edited' : '',
        swapSource ? 'is-swap-source' : '',
        dropTarget ? 'is-drop-target' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      tabIndex={selected ? 0 : -1}
      aria-current={selected ? 'true' : undefined}
      aria-label={`${index === 0 ? 'Current preset' : `Preset ${label}`}: ${status}`}
      draggable={!!slot.preset}
      onClick={onChoose}
      onContextMenu={(e) => {
        e.preventDefault();
        onChoose();
        onMenu(e.clientX, e.clientY);
      }}
      onDragStart={onDragStart}
      onDragOver={(e) => {
        if (e.dataTransfer.types.includes(DRAG_TYPE)) {
          e.preventDefault();
          e.dataTransfer.dropEffect = e.altKey ? 'move' : 'copy';
          onDragOverSlot(true);
        }
      }}
      onDragLeave={() => onDragOverSlot(false)}
      onDrop={onDrop}
    >
      <span className="slot__id">{index === 0 ? 'CURRENT' : label}</span>
      <span className="slot__name">{slot.preset ? name || '·····' : '—'}</span>
      <span className="slot__status" aria-hidden="true">
        {slot.device && <IconDevice size={11} className={inSync ? 'is-sync' : ''} />}
        {!slot.device && slot.source === 'file' && <IconFile size={11} />}
        {edited && <i className="dot dot--edited" />}
      </span>
    </button>
  );
}

function SlotMenu({ index, x, y, onClose, onSwap }: { index: number; x: number; y: number; onClose: () => void; onSwap: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const slot = useEditor((s) => s.slots[index]);
  const clipboard = useEditor((s) => s.clipboard);
  const connected = useDevice((s) => isConnected(s.midi));

  useEffect(() => {
    ref.current?.querySelector<HTMLButtonElement>('button:not([disabled])')?.focus();
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [onClose]);

  const run = (fn: () => void) => () => {
    fn();
    onClose();
  };
  const state = useEditor.getState();
  const hasPreset = !!slot.preset;
  const writeCount = hasPreset ? slotWriteParts(slot, index).length : 0;

  const items: { label: string; hint?: string; disabled?: boolean; action: () => void; danger?: boolean }[] = [
    { label: 'Read from Pacer', disabled: !connected || index === D6_INDEX, hint: index === D6_INDEX ? 'not readable' : undefined, action: run(() => void readPresetFromDevice(index)) },
    {
      label: 'Write to Pacer…',
      disabled: !connected || !hasPreset || writeCount === 0,
      hint: hasPreset ? `${writeCount} msg` : undefined,
      action: run(() => useUi.getState().openWrite({ slots: [index], mode: 'slot' })),
    },
    { label: 'Copy', hint: 'Ctrl+C', disabled: !hasPreset, action: run(() => state.copySlot(index)) },
    {
      label: clipboard ? `Paste ${slotLabel(clipboard.from)}` : 'Paste',
      hint: 'Ctrl+V',
      disabled: !clipboard,
      action: run(() => state.pasteSlot(index)),
    },
    { label: 'Duplicate to next free slot', hint: 'Ctrl+D', disabled: !hasPreset, action: run(() => void state.duplicateSlot(index)) },
    { label: 'Swap with…', action: onSwap },
    { label: 'Export .syx', disabled: !hasPreset, action: run(() => exportSlotSyx(index)) },
    { label: 'Export .json', disabled: !hasPreset, action: run(() => exportSlotsJson([index])) },
    { label: 'Copy share link', disabled: !hasPreset, action: run(() => void copyShareLink(index)) },
    { label: 'Print cheat sheet', disabled: !hasPreset, action: run(() => printCheatSheet(index)) },
    { label: hasPreset ? 'Reset to blank preset' : 'New blank preset', danger: hasPreset, action: run(() => state.newPreset(index)) },
  ];

  const left = Math.min(x, window.innerWidth - 240);
  const top = Math.min(y, window.innerHeight - 360);

  return (
    <div
      ref={ref}
      className="menu"
      role="menu"
      aria-label={`${slotLabel(index)} actions`}
      style={{ left, top }}
      onKeyDown={(e) => {
        const buttons = Array.from(ref.current?.querySelectorAll<HTMLButtonElement>('button:not([disabled])') ?? []);
        const i = buttons.indexOf(document.activeElement as HTMLButtonElement);
        if (e.key === 'Escape') {
          e.preventDefault();
          e.stopPropagation();
          onClose();
        } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          e.preventDefault();
          e.stopPropagation();
          const n = (i + (e.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
          buttons[n]?.focus();
        } else if (e.key === 'Tab') {
          onClose();
        }
      }}
    >
      <div className="menu__title">
        <IconMore size={14} /> {slotLabel(index)} {slot.preset ? `· ${displayName(slot.preset.name)}` : ''}
      </div>
      {items.map((item) => (
        <button
          key={item.label}
          type="button"
          role="menuitem"
          className={`menu__item${item.danger ? ' menu__item--danger' : ''}`}
          disabled={item.disabled}
          onClick={item.action}
        >
          <span>{item.label}</span>
          {item.hint && <span className="menu__hint">{item.hint}</span>}
        </button>
      ))}
    </div>
  );
}
