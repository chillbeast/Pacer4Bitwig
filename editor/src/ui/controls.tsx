import {
  useEffect,
  useId,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type KeyboardEvent,
  type ReactNode,
} from 'react';

// ---------------------------------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------------------------------

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'default' | 'primary' | 'ghost' | 'danger' | 'subtle';
  size?: 'sm' | 'md';
  icon?: ReactNode;
  badge?: ReactNode;
}

export function Button({ variant = 'default', size = 'md', icon, badge, className, children, type, ...rest }: ButtonProps) {
  return (
    <button
      type={type ?? 'button'}
      className={`btn btn--${variant} btn--${size}${icon && !children ? ' btn--icon' : ''}${className ? ` ${className}` : ''}`}
      {...rest}
    >
      {icon}
      {children !== undefined && children !== null && <span className="btn__label">{children}</span>}
      {badge !== undefined && badge !== null && <span className="btn__badge">{badge}</span>}
    </button>
  );
}

// ---------------------------------------------------------------------------------------------
// Segmented control (radio group with arrow-key navigation)
// ---------------------------------------------------------------------------------------------

export interface SegmentOption<T> {
  value: T;
  label: ReactNode;
  title?: string;
  disabled?: boolean;
}

export function Segmented<T extends string | number>({
  value,
  options,
  onChange,
  label,
  size = 'md',
  className,
}: {
  value: T;
  options: readonly SegmentOption<T>[];
  onChange: (value: T) => void;
  label: string;
  size?: 'sm' | 'md';
  className?: string;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const enabled = options.map((o, i) => ({ o, i })).filter(({ o }) => !o.disabled);
    const current = enabled.findIndex(({ o }) => o.value === value);
    let next = -1;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') next = (current + 1) % enabled.length;
    if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') next = (current - 1 + enabled.length) % enabled.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = enabled.length - 1;
    if (next >= 0) {
      event.preventDefault();
      event.stopPropagation();
      onChange(enabled[next].o.value);
      refs.current[enabled[next].i]?.focus();
    }
  };
  return (
    <div role="radiogroup" aria-label={label} className={`seg seg--${size}${className ? ` ${className}` : ''}`} onKeyDown={onKeyDown}>
      {options.map((o, i) => {
        const checked = o.value === value;
        return (
          <button
            key={String(o.value)}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked ? 0 : -1}
            title={o.title}
            disabled={o.disabled}
            className={`seg__option${checked ? ' is-checked' : ''}`}
            onClick={() => onChange(o.value)}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Toggle switch
// ---------------------------------------------------------------------------------------------

export function Toggle({
  checked,
  onChange,
  label,
  hideLabel = false,
  disabled,
  title,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  hideLabel?: boolean;
  disabled?: boolean;
  title?: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={hideLabel ? label : undefined}
      disabled={disabled}
      title={title}
      className={`toggle${checked ? ' is-on' : ''}`}
      onClick={() => onChange(!checked)}
    >
      <span className="toggle__track" aria-hidden="true">
        <span className="toggle__thumb" />
      </span>
      {!hideLabel && <span className="toggle__label">{label}</span>}
    </button>
  );
}

// ---------------------------------------------------------------------------------------------
// Number field (7-bit values) with stepper keys
// ---------------------------------------------------------------------------------------------

export function NumberField({
  value,
  onChange,
  label,
  min = 0,
  max = 127,
  disabled,
  hint,
  className,
}: {
  value: number;
  onChange: (value: number) => void;
  label: string;
  min?: number;
  max?: number;
  disabled?: boolean;
  hint?: string;
  className?: string;
}) {
  const id = useId();
  const [text, setText] = useState(String(value));
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (!focused) setText(String(value));
  }, [value, focused]);

  const commit = (raw: string) => {
    setText(raw);
    if (raw.trim() === '') return;
    const n = Number(raw);
    if (Number.isInteger(n) && n >= min && n <= max && n !== value) onChange(n);
  };
  const invalid = (() => {
    const n = Number(text);
    return text.trim() === '' || !Number.isInteger(n) || n < min || n > max;
  })();

  const step = (delta: number) => {
    const next = Math.min(max, Math.max(min, value + delta));
    if (next !== value) onChange(next);
    setText(String(next));
  };

  return (
    <div className={`field field--number${className ? ` ${className}` : ''}`}>
      <label className="field__label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className={`input input--mono${invalid ? ' is-invalid' : ''}`}
        inputMode="numeric"
        value={text}
        disabled={disabled}
        aria-invalid={invalid || undefined}
        title={hint ?? `${min}–${max}`}
        onFocus={() => setFocused(true)}
        onBlur={() => {
          setFocused(false);
          setText(String(value));
        }}
        onChange={(e) => commit(e.target.value.replace(/[^0-9]/g, ''))}
        onKeyDown={(e) => {
          if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
            e.preventDefault();
            e.stopPropagation();
            step((e.key === 'ArrowUp' ? 1 : -1) * (e.shiftKey ? 10 : 1));
          }
        }}
      />
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Select field
// ---------------------------------------------------------------------------------------------

export function SelectField({
  value,
  onChange,
  label,
  children,
  disabled,
  mono,
  className,
  hideLabel,
}: {
  value: number | string;
  onChange: (value: string) => void;
  label: string;
  children: ReactNode;
  disabled?: boolean;
  mono?: boolean;
  className?: string;
  hideLabel?: boolean;
}) {
  const id = useId();
  return (
    <div className={`field${className ? ` ${className}` : ''}`}>
      <label className={`field__label${hideLabel ? ' sr-only' : ''}`} htmlFor={id}>
        {label}
      </label>
      <div className="select">
        <select
          id={id}
          className={`input select__input${mono ? ' input--mono' : ''}`}
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
        >
          {children}
        </select>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------
// Progress bar
// ---------------------------------------------------------------------------------------------

export function ProgressBar({ value, max, label }: { value: number; max: number; label: string }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0;
  return (
    <div className="progress" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={max} aria-valuenow={value}>
      <div className="progress__bar" style={{ width: `${pct}%` }} />
    </div>
  );
}
