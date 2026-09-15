import { useMemo } from 'react';
import { pickFiles } from '../app/files';
import { importFiles, loadFactoryPresets, readAllFromDevice } from '../app/operations';
import { CONTROL_KEYS, displayName, presetKey, slotLabel, slotLongLabel, type ControlKey, type Preset } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { isSlotEdited, isSlotInSync, slotPendingParts, useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button, Segmented } from './controls';
import { PacerDevice } from './PacerDevice';

function changedControls(a: Preset | null, b: Preset): Set<ControlKey> {
  const out = new Set<ControlKey>();
  for (const key of CONTROL_KEYS) {
    if (!a) {
      out.add(key);
      continue;
    }
    const x = { name: '', controls: { [key]: a.controls[key] }, midi: [] };
    const y = { name: '', controls: { [key]: b.controls[key] }, midi: [] };
    if (JSON.stringify(x) !== JSON.stringify(y)) out.add(key);
  }
  return out;
}

export function Stage() {
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const slot = useEditor((s) => s.slots[s.selectedSlot]);
  const anyLoaded = useEditor((s) => s.slots.some((x) => x.preset !== null));
  const selection = useEditor((s) => s.selection);
  const preview = useEditor((s) => s.preview);
  const ledMode = useUi((s) => s.ledPreview);
  const previewStep = useUi((s) => s.previewStep);
  const connected = useDevice((s) => isConnected(s.midi));

  const index = preview ? preview.slot : selectedSlot;
  const target = useEditor((s) => s.slots[index]);
  const preset = preview ? preview.preset : slot.preset;
  const labels = preview ? preview.labels : slot.labels;

  const highlight = useMemo(
    () => (preview ? changedControls(target.preset, preview.preset) : undefined),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [preview, target.preset ? presetKey(target.preset) : null],
  );

  const edited = isSlotEdited(slot);
  const pending = edited ? slotPendingParts(slot, selectedSlot).length : 0;
  const status = !slot.preset
    ? 'Empty slot'
    : slot.device
      ? isSlotInSync(slot)
        ? 'Matches the Pacer'
        : `Edited · ${pending} message${pending === 1 ? '' : 's'} pending`
      : edited
        ? `Not on the Pacer yet · ${pending} messages`
        : slot.source === 'file'
          ? 'From file · not read from the Pacer'
          : 'Local';

  return (
    <main className="stage" aria-label="Hardware view">
      <div className="stage__toolbar">
        <div className="stage__title">
          <span className="slot-chip">{slotLabel(index)}</span>
          <div>
            <h1 className="stage__name">{preset ? displayName(preset.name) || 'Unnamed' : slotLongLabel(index)}</h1>
            <p className={`stage__status${edited && !preview ? ' is-edited' : ''}`}>{preview ? `Preview for ${slotLabel(index)}` : status}</p>
          </div>
        </div>
        <div className="stage__controls">
          <div className="labelled">
            <span className="labelled__label" id="led-preview-label">
              LEDs
            </span>
            <Segmented
              label="LED preview"
              size="sm"
              value={ledMode}
              onChange={(v) => useUi.getState().setLedPreview(v)}
              options={[
                { value: 'on', label: 'On colour' },
                { value: 'off', label: 'Off colour' },
              ]}
            />
          </div>
          <div className="labelled">
            <span className="labelled__label">Step</span>
            <Segmented
              label="LED preview step"
              size="sm"
              value={previewStep}
              onChange={(v) => useUi.getState().setPreviewStep(v)}
              options={[0, 1, 2, 3, 4, 5].map((i) => ({ value: i, label: String(i + 1), title: `Show step ${i + 1} LED colours` }))}
            />
          </div>
        </div>
      </div>

      {preview && (
        <div className="preview-banner" role="region" aria-label="Template preview">
          <div>
            <strong>{preview.title}</strong>
            <span>
              {' '}
              → {slotLabel(preview.slot)}
              {target.preset ? ` (replaces “${displayName(target.preset.name)}”)` : ''} · {highlight?.size ?? 0} controls change
            </span>
          </div>
          <div className="preview-banner__actions">
            <Button size="sm" variant="ghost" onClick={() => useUi.getState().openDialog('templates')}>
              Options
            </Button>
            <Button size="sm" onClick={() => useEditor.getState().cancelPreview()}>
              Cancel
            </Button>
            <Button size="sm" variant="primary" onClick={() => useEditor.getState().applyPreview()}>
              Apply to {slotLabel(preview.slot)}
            </Button>
          </div>
        </div>
      )}

      <div className="stage__device">
        <PacerDevice
          preset={preset}
          labels={labels}
          slotName={slotLabel(index)}
          selection={selection}
          onSelect={(sel) => useEditor.getState().select(sel)}
          ledMode={ledMode}
          previewStep={previewStep}
          highlight={highlight}
        />
        {!anyLoaded && !preview && (
          <div className="stage__empty">
            <h2>Start with your presets</h2>
            <p>Read the Pacer, open a .syx/.json file, or begin from a template. Everything works offline.</p>
            <div className="stage__empty-actions">
              <Button variant="primary" disabled={!connected} onClick={() => void readAllFromDevice()}>
                Read all from Pacer
              </Button>
              <Button onClick={() => void pickFiles('.syx,.bin,.json').then(importFiles)}>Import file…</Button>
              <Button onClick={() => void loadFactoryPresets()}>Factory presets</Button>
              <Button variant="ghost" onClick={() => useUi.getState().openDialog('templates')}>
                Templates…
              </Button>
            </div>
          </div>
        )}
      </div>
      <p className="stage__hint">Click a switch, jack or the display · arrow keys move between controls</p>
    </main>
  );
}
