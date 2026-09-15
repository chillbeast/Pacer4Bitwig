import { useEffect, useState } from 'react';
import { importSharedPreset, useShareImport } from '../app/share';
import { CONTROLS, MSG, displayName, slotLabel, stepSummary } from '../pacer';
import { useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button, SelectField } from './controls';
import { Dialog } from './Dialog';

export function ShareImportDialog() {
  const open = useUi((s) => s.dialog === 'share-import');
  const shared = useShareImport((s) => s.shared);
  const slots = useEditor((s) => s.slots);
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const [target, setTarget] = useState(selectedSlot);

  useEffect(() => {
    if (open && shared) setTarget(shared.slot ?? selectedSlot);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, shared]);

  const close = () => {
    useShareImport.setState({ shared: null });
    useUi.getState().closeDialog();
  };

  if (!shared) return null;
  const current = slots[target]?.preset;

  return (
    <Dialog
      open={open}
      onClose={close}
      size="md"
      title={`Import shared preset “${displayName(shared.preset.name) || 'unnamed'}”`}
      subtitle="Opened from a share link. It goes into the editor only — nothing is sent to the Pacer."
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={async () => {
              if (await importSharedPreset(target)) useUi.getState().closeDialog();
            }}
          >
            Import into {slotLabel(target)}
          </Button>
        </>
      }
    >
      <div className="share-summary">
        {CONTROLS.map((def) => {
          const step = shared.preset.controls[def.key].steps[0];
          const off = !step.active || step.msgType === MSG.OFF;
          return (
            <div key={def.key} className={`share-summary__row${off ? ' is-off' : ''}`}>
              <span className="fsw__badge">{def.short}</span>
              <span className="share-summary__label">{shared.labels[def.key] ?? ''}</span>
              <span className="mono">{off ? 'Off' : stepSummary(step)}</span>
            </div>
          );
        })}
      </div>
      <SelectField label="Target slot" value={target} onChange={(v) => setTarget(Number(v))} mono>
        {slots.map((s, i) => (
          <option key={i} value={i}>
            {slotLabel(i)}
            {s.preset ? ` — ${displayName(s.preset.name)}` : ' — empty'}
            {i === shared.slot ? ' (shared from)' : ''}
          </option>
        ))}
      </SelectField>
      {current && (
        <p className="hint">
          {slotLabel(target)} currently holds “{displayName(current.name)}”. Importing replaces it in the editor (undoable).
        </p>
      )}
    </Dialog>
  );
}
