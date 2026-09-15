import { useEffect, useState } from 'react';
import { BANKS, CURRENT_PRESET_INDEX, D6_INDEX, displayName, slotLabel } from '../pacer';
import { useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { TEMPLATES, templateById } from '../templates';
import { Button, Segmented } from './controls';
import { Dialog } from './Dialog';

/** Remembered between openings (and restored when reopening from a preview). */
const memory = {
  templateId: TEMPLATES[0].id,
  choices: {} as Record<string, string>,
  slots: {} as Record<string, number>,
};

export function TemplatesDialog() {
  const open = useUi((s) => s.dialog === 'templates');
  const slots = useEditor((s) => s.slots);
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const [templateId, setTemplateId] = useState(memory.templateId);
  const [choice, setChoice] = useState<string | undefined>(undefined);
  const [slot, setSlot] = useState(selectedSlot);

  const template = templateById(templateId) ?? TEMPLATES[0];

  useEffect(() => {
    if (!open) return;
    selectTemplate(memory.templateId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  function selectTemplate(id: string) {
    const t = templateById(id) ?? TEMPLATES[0];
    setTemplateId(t.id);
    setChoice(memory.choices[t.id] ?? t.choices?.options[0].value);
    setSlot(memory.slots[t.id] ?? t.defaultSlot ?? (selectedSlot === D6_INDEX ? 1 : selectedSlot));
  }

  const close = () => useUi.getState().closeDialog();
  const target = slots[slot];

  const build = () => {
    memory.templateId = template.id;
    if (choice) memory.choices[template.id] = choice;
    memory.slots[template.id] = slot;
    const result = template.build(choice);
    return { preset: result.preset, labels: result.labels, slot, title: result.title };
  };

  const activeChoice = template.choices?.options.find((o) => o.value === choice);

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
      <div className="templates">
        <div className="template-list" role="radiogroup" aria-label="Template">
          {TEMPLATES.map((t) => (
            <button
              key={t.id}
              type="button"
              role="radio"
              aria-checked={t.id === template.id}
              className={`template-option${t.id === template.id ? ' is-checked' : ''}`}
              onClick={() => selectTemplate(t.id)}
            >
              <strong>{t.name}</strong>
              <span className="hint">{t.choices ? `${t.choices.options.length} variants` : 'Channel-based pedalboard'}</span>
            </button>
          ))}
        </div>

        <article className="template-card is-selected">
          <header className="template-card__header">
            <div>
              <h3>{template.name}</h3>
              <p className="hint">{template.summary}</p>
            </div>
          </header>

          <div className="template-card__options">
            {template.choices && choice && (
              <div className="field">
                <span className="field__label">{template.choices.label}</span>
                <Segmented
                  label={template.choices.label}
                  value={choice}
                  onChange={setChoice}
                  options={template.choices.options.map((o) => ({ value: o.value, label: o.label }))}
                />
                {activeChoice && <p className="hint">{activeChoice.description}</p>}
              </div>
            )}

            <div className="field">
              <span className="field__label">Target slot</span>
              <div className="slot-picker" role="radiogroup" aria-label="Target slot">
                <SlotOption index={CURRENT_PRESET_INDEX} current={slot} onPick={setSlot} name={slots[0].preset?.name} preferred={template.defaultSlot} />
                {BANKS.map((bank, b) => (
                  <div key={bank} className="slot-picker__row">
                    {Array.from({ length: 6 }, (_, n) => {
                      const i = b * 6 + n + 1;
                      return (
                        <SlotOption key={i} index={i} current={slot} onPick={setSlot} name={slots[i].preset?.name} preferred={template.defaultSlot} />
                      );
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
      </div>
    </Dialog>
  );
}

function SlotOption({
  index,
  current,
  onPick,
  name,
  preferred,
}: {
  index: number;
  current: number;
  onPick: (i: number) => void;
  name: string | undefined;
  preferred: number | undefined;
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
      {index === preferred && <span className="slot-option__tag">default</span>}
    </button>
  );
}
