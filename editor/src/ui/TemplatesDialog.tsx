import { useEffect, useState } from 'react';
import { D6_INDEX, BANKS, CURRENT_PRESET_INDEX, displayName, slotLabel } from '../pacer';
import { useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import {
  LOOPER_DEFAULT_SLOT,
  LOOPER_LABELS,
  buildBitwigLooperPreset,
  type LooperLedMode,
} from '../templates/bitwigLooper';
import { Button, Segmented } from './controls';
import { Dialog } from './Dialog';

const TITLE = 'Bitwig Looper';

let lastMode: LooperLedMode = 'two-colour';
let lastSlot = LOOPER_DEFAULT_SLOT;

export function TemplatesDialog() {
  const open = useUi((s) => s.dialog === 'templates');
  const slots = useEditor((s) => s.slots);
  const preview = useEditor((s) => s.preview);
  const [mode, setMode] = useState<LooperLedMode>(lastMode);
  const [slot, setSlot] = useState(lastSlot);

  useEffect(() => {
    if (!open) return;
    if (preview && preview.title.startsWith(TITLE)) {
      setSlot(preview.slot);
      setMode(preview.title.includes('multi') ? 'multi-colour' : 'two-colour');
    } else {
      setMode(lastMode);
      setSlot(lastSlot);
    }
  }, [open, preview]);

  const close = () => useUi.getState().closeDialog();
  const target = slots[slot];

  const build = () => {
    lastMode = mode;
    lastSlot = slot;
    return {
      preset: buildBitwigLooperPreset(mode),
      labels: LOOPER_LABELS,
      slot,
      title: `${TITLE} (${mode})`,
    };
  };

  return (
    <Dialog
      open={open}
      onClose={close}
      size="lg"
      title="Templates"
      subtitle="Templates fill a preset slot in the editor. Nothing is sent until you choose Send changes."
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            Close
          </Button>
          <Button
            onClick={() => {
              useEditor.getState().startPreview(build());
              useEditor.getState().applyPreview();
              close();
            }}
          >
            Apply to {slotLabel(slot)}
          </Button>
          <Button
            variant="primary"
            onClick={() => {
              useEditor.getState().startPreview(build());
              useEditor.getState().select({ kind: 'control', key: 'SW1' });
              useUi.getState().setView('editor');
              close();
            }}
          >
            Preview on hardware
          </Button>
        </>
      }
    >
      <article className="template-card is-selected">
        <header className="template-card__header">
          <div>
            <h3>Bitwig Looper</h3>
            <p className="hint">
              Implements <span className="mono">docs/PACER-MAP.md</span>: CC Trigger 127/0 on channel 16 — switches CC 102–111,
              footswitches CC 112–115, expression pedals CC 116/117, preset-loaded CC 119. Preset name “LOOPS”.
            </p>
          </div>
        </header>

        <div className="template-card__options">
          <div className="field">
            <span className="field__label">LED strategy</span>
            <Segmented
              label="LED strategy"
              value={mode}
              onChange={setMode}
              options={[
                { value: 'two-colour', label: 'Two-colour' },
                { value: 'multi-colour', label: 'Multi-colour (experimental)' },
              ]}
            />
            <p className="hint">
              {mode === 'two-colour'
                ? 'Documented behaviour: step 1 carries the LED (red for loop switches 1–4, white for the rest, off when idle). The extension blinks to show state.'
                : 'Hypothesis: steps 2–6 act as colour slots (CC 20–69) on the same LED — white, red, green, amber, blue, purple. Verify in the LED Lab first.'}
            </p>
          </div>

          <div className="field">
            <span className="field__label">Target slot</span>
            <div className="slot-picker" role="radiogroup" aria-label="Target slot">
              <SlotOption index={CURRENT_PRESET_INDEX} current={slot} onPick={setSlot} name={slots[0].preset?.name} />
              {BANKS.map((bank, b) => (
                <div key={bank} className="slot-picker__row">
                  {Array.from({ length: 6 }, (_, n) => {
                    const i = b * 6 + n + 1;
                    return <SlotOption key={i} index={i} current={slot} onPick={setSlot} name={slots[i].preset?.name} />;
                  })}
                </div>
              ))}
            </div>
            <p className="hint">
              {target.preset
                ? `${slotLabel(slot)} currently holds “${displayName(target.preset.name)}” — it will be replaced (undoable).`
                : `${slotLabel(slot)} is empty in the editor.`}{' '}
              D6 is disabled: the Pacer cannot read it back.
            </p>
          </div>
        </div>
      </article>
    </Dialog>
  );
}

function SlotOption({
  index,
  current,
  onPick,
  name,
}: {
  index: number;
  current: number;
  onPick: (i: number) => void;
  name: string | undefined;
}) {
  const disabled = index === D6_INDEX;
  const checked = index === current;
  return (
    <button
      type="button"
      role="radio"
      aria-checked={checked}
      disabled={disabled}
      className={`slot-option${checked ? ' is-checked' : ''}${index === 0 ? ' slot-option--wide' : ''}`}
      title={disabled ? 'D6 cannot be read back by the Pacer' : name ? displayName(name) : 'empty'}
      onClick={() => onPick(index)}
    >
      {index === 0 ? 'Current' : slotLabel(index)}
      {index === LOOPER_DEFAULT_SLOT && <span className="slot-option__tag">default</span>}
    </button>
  );
}
