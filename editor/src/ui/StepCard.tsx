import { memo } from 'react';
import {
  LED_NUM_OPTIONS,
  MSG,
  msgTypeInfo,
  stepSummary,
  type ControlKey,
  type Led,
  type Step,
} from '../pacer';
import { useEditor } from '../store/editor';
import { Segmented, Toggle } from './controls';
import { ChannelSelect, ColorPicker, DataField, MsgTypeSelect, Swatch } from './fields';

export const StepCard = memo(function StepCard({
  slot,
  controlKey,
  index,
  step,
  led,
  allowed,
  readOnly,
}: {
  slot: number;
  controlKey: ControlKey;
  index: number;
  step: Step;
  led: Led | null;
  allowed: readonly number[];
  readOnly: boolean;
}) {
  const updateStep = useEditor((s) => s.updateStep);
  const setStepType = useEditor((s) => s.setStepType);
  const updateLed = useEditor((s) => s.updateLed);
  const info = msgTypeInfo(step.msgType);
  const off = step.msgType === MSG.OFF;
  const inactive = off || !step.active;

  const setData = (i: number, v: number) => {
    const data: [number, number, number] = [step.data[0], step.data[1], step.data[2]];
    data[i] = v;
    updateStep(slot, controlKey, index, { data });
  };

  return (
    <section className={`step-card${inactive ? ' is-inactive' : ''}`} aria-label={`Step ${index + 1}`}>
      <header className="step-card__header">
        <span className="step-card__num">{index + 1}</span>
        <div className="step-card__title">
          <span className="step-card__type">{off ? 'Off' : info.name}</span>
          {!off && <span className="step-card__summary mono">{stepSummary(step)}</span>}
        </div>
        {led && !off && (
          <span className="step-card__leds" title="LED on / off colour">
            <Swatch color={led.onColor} size="sm" />
            <Swatch color={led.offColor} size="sm" />
          </span>
        )}
        {!off && (
          <Toggle
            label={`Step ${index + 1} active`}
            hideLabel
            checked={step.active}
            disabled={readOnly}
            onChange={(active) => updateStep(slot, controlKey, index, { active })}
            title={step.active ? 'Active' : 'Inactive'}
          />
        )}
      </header>

      <fieldset className="step-card__body" disabled={readOnly}>
        <div className="step-card__row">
          <MsgTypeSelect value={step.msgType} allowed={allowed} onChange={(t) => setStepType(slot, controlKey, index, t)} />
          {!off && <ChannelSelect value={step.channel} onChange={(channel) => updateStep(slot, controlKey, index, { channel })} />}
        </div>
        {!off && (
          <div className="step-card__data">
            {info.fields.map((spec, i) => (
              <DataField key={`${step.msgType}-${i}`} spec={spec} value={step.data[i]} onChange={(v) => setData(i, v)} />
            ))}
          </div>
        )}
        {led && (
          <details className="led-section" open={!off || led.midiCtrl}>
            <summary>
              <span>LED</span>
              <span className="led-section__hint">
                {led.midiCtrl ? 'MIDI controlled' : 'Local'} · {LED_NUM_OPTIONS.find((o) => o.value === led.num)?.label ?? led.num}
              </span>
            </summary>
            <div className="led-section__grid">
              <ColorPicker label="On colour" value={led.onColor} onChange={(onColor) => updateLed(slot, controlKey, index, { onColor })} />
              <ColorPicker
                label="Off colour"
                value={led.offColor}
                onChange={(offColor) => updateLed(slot, controlKey, index, { offColor })}
              />
              <div className="field">
                <span className="field__label">LED</span>
                <Segmented
                  label={`Step ${index + 1} LED number`}
                  size="sm"
                  value={led.num}
                  options={LED_NUM_OPTIONS.map((o) => ({ value: o.value, label: o.label === 'Default' ? 'Auto' : o.label[0], title: o.label }))}
                  onChange={(num) => updateLed(slot, controlKey, index, { num })}
                />
              </div>
              <div className="field">
                <span className="field__label">LED MIDI ctrl</span>
                <Toggle
                  label="Follow incoming MIDI"
                  checked={led.midiCtrl}
                  onChange={(midiCtrl) => updateLed(slot, controlKey, index, { midiCtrl })}
                  title="On/off colour follows this step's message received over USB (127 = on, 0 = off)"
                />
              </div>
            </div>
          </details>
        )}
      </fieldset>
    </section>
  );
});
