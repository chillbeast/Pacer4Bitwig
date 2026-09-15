import { useEffect, useId, useState } from 'react';
import {
  CONTROL_BY_KEY,
  CONTROL_MODES,
  MSG,
  MSG_TYPES_PRESET_MIDI,
  PRESET_NAME_LENGTH,
  allowedMsgTypes,
  displayName,
  msgTypeInfo,
  sanitizeNameInput,
  slotLongLabel,
  stepSummary,
  type ControlKey,
  type MidiSetting,
  type Preset,
} from '../pacer';
import { useEditor } from '../store/editor';
import { Button, Segmented } from './controls';
import { ChannelSelect, DataField, MsgTypeSelect } from './fields';
import { StepCard } from './StepCard';

export function Inspector() {
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const slot = useEditor((s) => s.slots[s.selectedSlot]);
  const selection = useEditor((s) => s.selection);
  const preview = useEditor((s) => s.preview);
  const newPreset = useEditor((s) => s.newPreset);

  const previewing = preview !== null;
  const index = previewing ? preview.slot : selectedSlot;
  const preset = previewing ? preview.preset : slot.preset;
  const labels = previewing ? preview.labels : slot.labels;

  if (!preset) {
    return (
      <aside className="inspector" aria-label="Inspector">
        <div className="empty">
          <p className="empty__title">{slotLongLabel(index)} is empty</p>
          <p className="empty__text">Read it from the Pacer, import a file, apply a template, or start from scratch.</p>
          <Button onClick={() => newPreset(index)}>New blank preset</Button>
        </div>
      </aside>
    );
  }

  return (
    <aside className="inspector" aria-label="Inspector">
      {previewing && (
        <div className="inspector__notice" role="status">
          Template preview — read only. Apply it to edit.
        </div>
      )}
      {selection.kind === 'preset' ? (
        <PresetSettings slot={index} preset={preset} readOnly={previewing} />
      ) : (
        <ControlEditor slot={index} preset={preset} controlKey={selection.key} label={labels[selection.key]} readOnly={previewing} />
      )}
    </aside>
  );
}

function ControlEditor({
  slot,
  preset,
  controlKey,
  label,
  readOnly,
}: {
  slot: number;
  preset: Preset;
  controlKey: ControlKey;
  label: string | undefined;
  readOnly: boolean;
}) {
  const def = CONTROL_BY_KEY[controlKey];
  const control = preset.controls[controlKey];
  const setControlMode = useEditor((s) => s.setControlMode);
  const setLabel = useEditor((s) => s.setLabel);
  const [showOff, setShowOff] = useState(false);
  const allowed = allowedMsgTypes(def.kind);
  const mode = CONTROL_MODES.find((m) => m.value === control.mode);
  const offSteps = control.steps.filter((s) => s.msgType === MSG.OFF).length;
  const labelId = useId();

  useEffect(() => setShowOff(false), [controlKey, slot]);

  const kindLabel = def.kind === 'stompswitch' ? 'Stompswitch' : def.kind === 'footswitch' ? 'Footswitch jack' : 'Expression pedal';

  return (
    <>
      <header className="inspector__header">
        <div className="inspector__heading">
          <span className="inspector__badge">{def.short}</span>
          <div>
            <h2 className="inspector__title">{def.label}</h2>
            <p className="inspector__meta">
              {kindLabel} · <span className="mono">obj 0x{def.obj.toString(16).toUpperCase().padStart(2, '0')}</span>
            </p>
          </div>
        </div>
        <div className="field field--label">
          <label className="field__label" htmlFor={labelId}>
            Label <span className="field__aside">editor only</span>
          </label>
          <input
            id={labelId}
            className="input"
            value={label ?? ''}
            maxLength={12}
            placeholder={msgTypeInfo(control.steps[0].msgType).short}
            disabled={readOnly}
            onChange={(e) => setLabel(slot, controlKey, e.target.value)}
          />
        </div>
      </header>

      <div className="inspector__section">
        <div className="section-title">
          <span>Control mode</span>
        </div>
        <Segmented
          label="Control mode"
          value={control.mode}
          options={CONTROL_MODES.map((m) => ({ value: m.value, label: m.value === 2 ? 'External step' : m.value === 0 ? 'All at once' : 'Sequence', title: m.name, disabled: readOnly }))}
          onChange={(v) => setControlMode(slot, controlKey, v)}
          className="seg--full"
        />
        <p className="hint">{mode ? mode.description : `Unknown mode ${control.mode}`}</p>
      </div>

      <div className="inspector__section">
        <div className="section-title">
          <span>Steps</span>
          {offSteps > 0 && (
            <button type="button" className="link-button" onClick={() => setShowOff(!showOff)} aria-expanded={showOff}>
              {showOff ? 'Hide' : 'Show'} {offSteps} off step{offSteps === 1 ? '' : 's'}
            </button>
          )}
        </div>
        <div className="step-list">
          {control.steps.map((step, i) =>
            step.msgType === MSG.OFF && !showOff ? null : (
              <StepCard
                key={i}
                slot={slot}
                controlKey={controlKey}
                index={i}
                step={step}
                led={control.leds?.[i] ?? null}
                allowed={allowed}
                readOnly={readOnly}
              />
            ),
          )}
          {offSteps === 6 && !showOff && (
            <p className="hint">
              All steps are off.{' '}
              <button type="button" className="link-button" onClick={() => setShowOff(true)}>
                Configure a step
              </button>
            </p>
          )}
        </div>
      </div>
    </>
  );
}

function PresetSettings({ slot, preset, readOnly }: { slot: number; preset: Preset; readOnly: boolean }) {
  const setName = useEditor((s) => s.setName);
  const [text, setText] = useState(displayName(preset.name));
  const [focused, setFocused] = useState(false);
  const [showOff, setShowOff] = useState(false);
  const nameId = useId();

  useEffect(() => {
    if (!focused) setText(displayName(preset.name));
  }, [preset.name, focused]);

  const used = preset.midi.filter((m) => m.msgType !== MSG.OFF).length;

  return (
    <>
      <header className="inspector__header">
        <div className="inspector__heading">
          <span className="inspector__badge inspector__badge--display">P</span>
          <div>
            <h2 className="inspector__title">Preset settings</h2>
            <p className="inspector__meta">{slotLongLabel(slot)}</p>
          </div>
        </div>
      </header>
      <div className="inspector__section">
        <div className="field">
          <label className="field__label" htmlFor={nameId}>
            Name <span className="field__aside">{PRESET_NAME_LENGTH} characters, shown on the display</span>
          </label>
          <input
            id={nameId}
            className="input input--display"
            value={text}
            maxLength={PRESET_NAME_LENGTH}
            spellCheck={false}
            autoComplete="off"
            disabled={readOnly}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            onChange={(e) => {
              const clean = sanitizeNameInput(e.target.value.toUpperCase());
              setText(clean);
              setName(slot, clean);
            }}
          />
        </div>
      </div>
      <div className="inspector__section">
        <div className="section-title">
          <span>On-load MIDI</span>
          <span className="section-title__aside">{used} of 16 used</span>
        </div>
        <p className="hint">Sent by the Pacer every time this preset is loaded.</p>
        <div className="midi-list">
          {preset.midi.map((m, i) =>
            m.msgType === MSG.OFF && !showOff ? null : <MidiRow key={i} slot={slot} index={i} setting={m} readOnly={readOnly} />,
          )}
        </div>
        <button type="button" className="link-button" onClick={() => setShowOff(!showOff)} aria-expanded={showOff}>
          {showOff ? 'Hide unused slots' : `Show all 16 slots`}
        </button>
      </div>
    </>
  );
}

function MidiRow({ slot, index, setting, readOnly }: { slot: number; index: number; setting: MidiSetting; readOnly: boolean }) {
  const updateMidi = useEditor((s) => s.updateMidi);
  const setMidiType = useEditor((s) => s.setMidiType);
  const info = msgTypeInfo(setting.msgType);
  const off = setting.msgType === MSG.OFF;
  const setData = (i: number, v: number) => {
    const data: [number, number, number] = [setting.data[0], setting.data[1], setting.data[2]];
    data[i] = v;
    updateMidi(slot, index, { data });
  };
  return (
    <section className={`step-card step-card--compact${off ? ' is-inactive' : ''}`} aria-label={`On-load message ${index + 1}`}>
      <header className="step-card__header">
        <span className="step-card__num">{index + 1}</span>
        <div className="step-card__title">
          <span className="step-card__type">{off ? 'Off' : info.name}</span>
          {!off && <span className="step-card__summary mono">{stepSummary(setting)}</span>}
        </div>
      </header>
      <fieldset className="step-card__body" disabled={readOnly}>
        <div className="step-card__row">
          <MsgTypeSelect value={setting.msgType} allowed={MSG_TYPES_PRESET_MIDI} onChange={(t) => setMidiType(slot, index, t)} />
          {!off && <ChannelSelect value={setting.channel} onChange={(channel) => updateMidi(slot, index, { channel })} />}
        </div>
        {!off && (
          <div className="step-card__data">
            {info.fields.map((spec, i) => (
              <DataField key={`${setting.msgType}-${i}`} spec={spec} value={setting.data[i]} onChange={(v) => setData(i, v)} />
            ))}
          </div>
        )}
      </fieldset>
    </section>
  );
}
