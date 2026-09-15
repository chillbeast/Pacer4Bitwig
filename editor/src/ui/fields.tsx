import { useEffect, useId, useRef, useState } from 'react';
import {
  INC_DEC_OPTIONS,
  LED_COLORS,
  MMC_COMMAND_OPTIONS,
  PRESET_SELECT_OPTIONS,
  RELAY_MODE_OPTIONS,
  RELAY_OPTIONS,
  STEP_OPTIONS,
  TARGET_OPTIONS,
  ledColorInfo,
  msgTypeInfo,
  noteName,
  type EnumOption,
  type FieldSpec,
} from '../pacer';
import { NumberField, SelectField } from './controls';
import { ledAppearance, ledVars } from './led';

export function MsgTypeSelect({
  value,
  allowed,
  onChange,
  label = 'Message type',
}: {
  value: number;
  allowed: readonly number[];
  onChange: (value: number) => void;
  label?: string;
}) {
  const groups = new Map<string, number[]>();
  for (const t of allowed) {
    const g = msgTypeInfo(t).group;
    groups.set(g, [...(groups.get(g) ?? []), t]);
  }
  return (
    <SelectField label={label} value={value} onChange={(v) => onChange(Number(v))} className="field--type">
      {!allowed.includes(value) && <option value={value}>{msgTypeInfo(value).name}</option>}
      {[...groups].map(([group, types]) =>
        group === 'Off' ? (
          types.map((t) => (
            <option key={t} value={t}>
              Off
            </option>
          ))
        ) : (
          <optgroup key={group} label={group}>
            {types.map((t) => (
              <option key={t} value={t}>
                {msgTypeInfo(t).name}
              </option>
            ))}
          </optgroup>
        ),
      )}
    </SelectField>
  );
}

export function ChannelSelect({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  return (
    <SelectField label="Channel" value={value} onChange={(v) => onChange(Number(v))} mono className="field--channel">
      <option value={0}>Global</option>
      {Array.from({ length: 16 }, (_, i) => (
        <option key={i + 1} value={i + 1}>
          {i + 1}
        </option>
      ))}
    </SelectField>
  );
}

const ENUMS: Partial<Record<FieldSpec['kind'], readonly EnumOption[]>> = {
  preset: PRESET_SELECT_OPTIONS,
  target: TARGET_OPTIONS,
  incdec: INC_DEC_OPTIONS,
  relayMode: RELAY_MODE_OPTIONS,
  relay: RELAY_OPTIONS,
  mmc: MMC_COMMAND_OPTIONS,
  step: STEP_OPTIONS,
};

export function DataField({ spec, value, onChange }: { spec: FieldSpec; value: number; onChange: (value: number) => void }) {
  if (spec.kind === 'unused') return null;
  if (spec.kind === 'note') {
    return (
      <SelectField label={spec.label} value={value} onChange={(v) => onChange(Number(v))} mono>
        {Array.from({ length: 128 }, (_, n) => (
          <option key={n} value={n}>
            {noteName(n)} · {n}
          </option>
        ))}
      </SelectField>
    );
  }
  const options = ENUMS[spec.kind];
  if (options) {
    return (
      <SelectField label={spec.label} value={value} onChange={(v) => onChange(Number(v))}>
        {!options.some((o) => o.value === value) && <option value={value}>Value {value}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </SelectField>
    );
  }
  return (
    <NumberField
      label={spec.label}
      value={value}
      onChange={onChange}
      hint={spec.hint ?? (spec.kind === 'cc' ? 'Controller number 0–127' : '0–127')}
    />
  );
}

export function Swatch({ color, size = 'md' }: { color: number; size?: 'sm' | 'md' }) {
  return <span className={`swatch swatch--${size} led--${ledAppearance(color)}`} style={ledVars(color)} aria-hidden="true" />;
}

export function ColorPicker({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const id = useId();
  const info = ledColorInfo(value);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  useEffect(() => {
    if (open) root.current?.querySelector<HTMLButtonElement>('.color-grid [aria-checked="true"]')?.focus();
  }, [open]);

  const pick = (v: number) => {
    onChange(v);
    setOpen(false);
    root.current?.querySelector<HTMLButtonElement>('.color-trigger')?.focus();
  };

  const bright = LED_COLORS.filter((c) => c.value > 0 && c.value < 0x19 && !c.dim);
  const dim = LED_COLORS.filter((c) => c.value > 0 && c.value < 0x19 && c.dim);

  return (
    <div
      className="field color-picker"
      ref={root}
      onKeyDown={(e) => {
        if (e.key === 'Escape' && open) {
          e.stopPropagation();
          setOpen(false);
        }
      }}
    >
      <span className="field__label" id={id}>
        {label}
      </span>
      <button
        type="button"
        className="input color-trigger"
        aria-haspopup="true"
        aria-expanded={open}
        aria-labelledby={id}
        aria-describedby={`${id}-v`}
        onClick={() => setOpen(!open)}
      >
        <Swatch color={value} />
        <span id={`${id}-v`} className="color-trigger__name">
          {info.name}
        </span>
        <span className="color-trigger__code">{info.code}</span>
      </button>
      {open && (
        <div className="color-popover" role="radiogroup" aria-labelledby={id}>
          <div className="color-grid">
            {[bright, dim].map((row, r) => (
              <div className="color-grid__row" key={r}>
                {row.map((c) => (
                  <button
                    key={c.value}
                    type="button"
                    role="radio"
                    aria-checked={c.value === value}
                    aria-label={`${c.name} (${c.code})`}
                    title={`${c.name} · ${c.code} · 0x${c.value.toString(16).toUpperCase().padStart(2, '0')}`}
                    className="color-cell"
                    onClick={() => pick(c.value)}
                  >
                    <Swatch color={c.value} />
                  </button>
                ))}
              </div>
            ))}
          </div>
          <div className="color-popover__extra">
            {[0x00, 0x7f].map((v) => (
              <button
                key={v}
                type="button"
                role="radio"
                aria-checked={v === value}
                className={`chip${v === value ? ' is-checked' : ''}`}
                onClick={() => pick(v)}
                title={v === 0x7f ? 'Value 0x7F found in factory presets — most likely the default colour for the message type' : 'LED off'}
              >
                <Swatch color={v} size="sm" />
                {v === 0 ? 'Off' : 'Default (0x7F)'}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
