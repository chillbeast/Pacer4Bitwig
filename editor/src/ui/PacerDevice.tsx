import { useRef, type CSSProperties, type KeyboardEvent } from 'react';
import {
  CONTROL_BY_KEY,
  LED_NUM_OPTIONS,
  MSG,
  activeStepCount,
  displayName,
  ledColorInfo,
  msgTypeInfo,
  primaryStepIndex,
  stepSummary,
  type ControlKey,
  type ControlLabels,
  type Preset,
} from '../pacer';
import type { Selection } from '../store/editor';
import { useUi } from '../store/ui';

/** Timestamp of the last hardware-follow hit for this control (restarts the flash animation). */
function useFlash(key: ControlKey): number | null {
  return useUi((s) => (s.flash && s.flash.key === key ? s.flash.at : null));
}
import { ledAppearance, ledVars, previewColor } from './led';

/** Device design space: 1000 × 600 units. */
const W = 1000;
const H = 600;

export type ItemId = ControlKey | 'preset';

interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}

const COL = [105, 263, 421, 579, 737, 895];
const SW_W = 128;
const TOP_Y = 96;
const BOTTOM_Y = 346;
const SW_H = 196;

const box = (cx: number, y: number, w = SW_W, h = SW_H): Box => ({ x: cx - w / 2, y, w, h });

export const LAYOUT: Record<ItemId, Box> = {
  SWA: box(COL[1], TOP_Y),
  SWB: box(COL[2], TOP_Y),
  SWC: box(COL[3], TOP_Y),
  SWD: box(COL[4], TOP_Y),
  SW1: box(COL[0], BOTTOM_Y),
  SW2: box(COL[1], BOTTOM_Y),
  SW3: box(COL[2], BOTTOM_Y),
  SW4: box(COL[3], BOTTOM_Y),
  SW5: box(COL[4], BOTTOM_Y),
  SW6: box(COL[5], BOTTOM_Y),
  preset: { x: 830, y: 92, w: 130, h: 92 },
  FS1: { x: 236, y: 30, w: 44, h: 28 },
  FS3: { x: 284, y: 30, w: 44, h: 28 },
  FS2: { x: 364, y: 30, w: 44, h: 28 },
  FS4: { x: 412, y: 30, w: 44, h: 28 },
  EXP1: { x: 492, y: 30, w: 64, h: 28 },
  EXP2: { x: 572, y: 30, w: 64, h: 28 },
};

const ITEMS = Object.keys(LAYOUT) as ItemId[];

function pos(b: Box): CSSProperties {
  return {
    left: `${(b.x / W) * 100}%`,
    top: `${(b.y / H) * 100}%`,
    width: `${(b.w / W) * 100}%`,
    height: `${(b.h / H) * 100}%`,
  };
}

function center(id: ItemId) {
  const b = LAYOUT[id];
  return { x: b.x + b.w / 2, y: b.y + b.h / 2 };
}

/** Nearest item in the arrow direction (used for keyboard navigation). */
export function neighbour(from: ItemId, key: string): ItemId | null {
  const c = center(from);
  let best: ItemId | null = null;
  let bestScore = Infinity;
  for (const id of ITEMS) {
    if (id === from) continue;
    const o = center(id);
    const dx = o.x - c.x;
    const dy = o.y - c.y;
    let primary: number;
    let secondary: number;
    if (key === 'ArrowRight') [primary, secondary] = [dx, dy];
    else if (key === 'ArrowLeft') [primary, secondary] = [-dx, dy];
    else if (key === 'ArrowDown') [primary, secondary] = [dy, dx];
    else [primary, secondary] = [-dy, dx];
    if (primary <= 4) continue;
    const score = primary + Math.abs(secondary) * 2.5;
    if (score < bestScore) {
      bestScore = score;
      best = id;
    }
  }
  return best;
}

const selectedId = (selection: Selection): ItemId => (selection.kind === 'preset' ? 'preset' : selection.key);

export interface PacerDeviceProps {
  preset: Preset | null;
  labels?: ControlLabels;
  slotName: string;
  selection: Selection;
  onSelect: (selection: Selection) => void;
  ledMode: 'on' | 'off';
  previewStep: number;
  /** Controls to emphasise (e.g. changed by a template preview). */
  highlight?: ReadonlySet<ControlKey>;
  /** Restrict interaction to stompswitches (LED Lab). */
  switchesOnly?: boolean;
  ariaLabel?: string;
}

export function PacerDevice({
  preset,
  labels = {},
  slotName,
  selection,
  onSelect,
  ledMode,
  previewStep,
  highlight,
  switchesOnly = false,
  ariaLabel = 'Pacer controls',
}: PacerDeviceProps) {
  const refs = useRef(new Map<ItemId, HTMLButtonElement>());
  const current = selectedId(selection);

  const select = (id: ItemId) => onSelect(id === 'preset' ? { kind: 'preset' } : { kind: 'control', key: id });
  const enabled = (id: ItemId) =>
    !switchesOnly || (id !== 'preset' && CONTROL_BY_KEY[id].kind === 'stompswitch');

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return;
    const target = event.target as HTMLElement;
    if (!target.dataset.item) return;
    let next = neighbour(target.dataset.item as ItemId, event.key);
    while (next && !enabled(next)) next = neighbour(next, event.key);
    if (!next) return;
    event.preventDefault();
    select(next);
    refs.current.get(next)?.focus();
  };

  const itemProps = (id: ItemId) => ({
    'data-item': id,
    ref: (el: HTMLButtonElement | null) => {
      if (el) refs.current.set(id, el);
      else refs.current.delete(id);
    },
    tabIndex: id === current ? 0 : -1,
    'aria-pressed': id === current,
    disabled: !enabled(id),
    onClick: () => select(id),
  });

  const name = preset ? displayName(preset.name) : '-----';

  return (
    <div className="pacer" role="group" aria-label={ariaLabel} onKeyDown={onKeyDown}>
      <div className="pacer__canvas">
        {/* rear panel */}
        <div className="pacer__rear" aria-hidden="true" />
        <RearLabel x={96} text="R 1/3" />
        <RearLabel x={176} text="R 2/4" />
        <RearLabel x={332} text="FS 1/3" />
        <RearLabel x={436} text="FS 2/4" />
        <RearLabel x={524} text="EXP 1" />
        <RearLabel x={604} text="EXP 2" />
        <RearLabel x={700} text="MIDI OUT" />
        <RearLabel x={790} text="USB" />
        <RearLabel x={872} text="DC 9V" />
        <RearLabel x={944} text="POWER" />
        <div className="relays" style={pos({ x: 66, y: 32, w: 140, h: 24 })} title="Relay outputs R1–R4 (used by Relay messages)">
          {[1, 2, 3, 4].map((r) => (
            <span key={r} className="relay">
              <i aria-hidden="true" />R{r}
            </span>
          ))}
        </div>
        {(['FS1', 'FS3', 'FS2', 'FS4', 'EXP1', 'EXP2'] as const).map((key) => (
          <JackButton key={key} id={key} preset={preset} labels={labels} highlight={highlight} itemProps={itemProps(key)} />
        ))}

        {/* body */}
        <div className="pacer__body" aria-hidden="true">
          <div className="pacer__stripe pacer__stripe--top" />
          <div className="pacer__stripe pacer__stripe--bottom" />
          <div className="pacer__tab">PACER</div>
        </div>

        <div className="fsw fsw--fixed" style={pos(box(COL[0], TOP_Y))} aria-hidden="true" title="Preset key (fixed function)">
          <div className="fsw__screen">
            <span className="fsw__label">PRESET</span>
            <span className="fsw__led" />
          </div>
          <div className="fsw__pedal" />
        </div>

        {(['SWA', 'SWB', 'SWC', 'SWD', 'SW1', 'SW2', 'SW3', 'SW4', 'SW5', 'SW6'] as const).map((key) => (
          <SwitchButton
            key={key}
            id={key}
            preset={preset}
            labels={labels}
            ledMode={ledMode}
            previewStep={previewStep}
            highlighted={highlight?.has(key) ?? false}
            itemProps={itemProps(key)}
          />
        ))}

        <button
          type="button"
          className="pacer__display"
          style={pos(LAYOUT.preset)}
          aria-label={`Preset settings: ${slotName} ${name}`}
          {...itemProps('preset')}
        >
          <span className="pacer__display-name">{name.padEnd(5, ' ')}</span>
          <span className="pacer__display-slot">{slotName}</span>
        </button>
        <div className="pacer__knob" style={pos({ x: 853, y: 206, w: 84, h: 84 })} aria-hidden="true" />
      </div>
    </div>
  );
}

function RearLabel({ x, text }: { x: number; text: string }) {
  return (
    <span className="rear-label" style={{ left: `${(x / W) * 100}%`, top: `${(6 / H) * 100}%` }} aria-hidden="true">
      {text}
    </span>
  );
}

interface ItemProps {
  'data-item': ItemId;
  ref: (el: HTMLButtonElement | null) => void;
  tabIndex: number;
  'aria-pressed': boolean;
  disabled: boolean;
  onClick: () => void;
}

function controlText(preset: Preset | null, key: ControlKey, labels: ControlLabels) {
  if (!preset) return { label: labels[key] ?? '—', summary: 'empty slot', steps: 0, stepIndex: 0, off: true };
  const control = preset.controls[key];
  const stepIndex = primaryStepIndex(control);
  const step = control.steps[stepIndex];
  const steps = activeStepCount(control);
  const off = steps === 0 || step.msgType === MSG.OFF;
  return {
    label: labels[key] ?? (off ? 'OFF' : msgTypeInfo(step.msgType).short.toUpperCase()),
    summary: off ? 'Off' : stepSummary(step),
    steps,
    stepIndex,
    off,
  };
}

function SwitchButton({
  id,
  preset,
  labels,
  ledMode,
  previewStep,
  highlighted,
  itemProps,
}: {
  id: ControlKey;
  preset: Preset | null;
  labels: ControlLabels;
  ledMode: 'on' | 'off';
  previewStep: number;
  highlighted: boolean;
  itemProps: ItemProps;
}) {
  const def = CONTROL_BY_KEY[id];
  const b = LAYOUT[id];
  const flash = useFlash(id);
  const text = controlText(preset, id, labels);
  const control = preset?.controls[id];
  const led = control?.leds?.[previewStep];
  const color = previewColor(led, ledMode);
  const appearance = preset ? ledAppearance(color) : 'off';
  const ledNum = led && led.num !== 0 ? LED_NUM_OPTIONS.find((o) => o.value === led.num)?.label : undefined;
  const mode = control && control.mode !== 0 ? (control.mode === 1 ? 'SEQ' : 'EXT') : null;
  const top = b.y < 200;
  const ledName = preset ? ledColorInfo(color).name : 'none';

  return (
    <>
      <button
        type="button"
        className={`fsw${text.off ? ' is-off' : ''}${highlighted ? ' is-highlighted' : ''}`}
        style={pos(b)}
        aria-label={`${def.label}: ${text.summary}${text.steps > 1 ? `, ${text.steps} active steps` : ''}. LED ${ledMode} colour ${ledName}.`}
        {...itemProps}
      >
        {flash !== null && <span key={flash} className="flash" aria-hidden="true" />}
        <span className="fsw__screen">
          <span className="fsw__label">{text.label}</span>
          <span className={`fsw__led led--${appearance}`} style={ledVars(color)}>
            {ledNum && <span className="fsw__lednum">{ledNum[0]}</span>}
          </span>
        </span>
        <span className="fsw__pedal">
          <span className="fsw__dots" aria-hidden="true">
            {control?.steps.map((s, i) => (
              <i key={i} className={s.active && s.msgType !== MSG.OFF ? 'on' : ''} />
            ))}
          </span>
          {mode && <span className="fsw__mode">{mode}</span>}
        </span>
      </button>
      <span
        className={`fsw__caption${top ? ' fsw__caption--top' : ''}`}
        style={pos({ x: b.x - 14, y: b.y + b.h + 4, w: b.w + 28, h: 40 })}
        aria-hidden="true"
      >
        <span className="fsw__badge">{def.short}</span>
        <span className="fsw__summary">{text.summary}</span>
      </span>
    </>
  );
}

function JackButton({
  id,
  preset,
  labels,
  highlight,
  itemProps,
}: {
  id: ControlKey;
  preset: Preset | null;
  labels: ControlLabels;
  highlight?: ReadonlySet<ControlKey>;
  itemProps: ItemProps;
}) {
  const def = CONTROL_BY_KEY[id];
  const flash = useFlash(id);
  const text = controlText(preset, id, labels);
  return (
    <button
      type="button"
      className={`jack${text.off ? ' is-off' : ''}${highlight?.has(id) ? ' is-highlighted' : ''}`}
      style={pos(LAYOUT[id])}
      aria-label={`${def.label}: ${text.summary}`}
      title={`${def.label} · ${text.summary}`}
      {...itemProps}
    >
      {flash !== null && <span key={flash} className="flash" aria-hidden="true" />}
      <i className="jack__socket" aria-hidden="true" />
      <span className="jack__name">{def.short}</span>
    </button>
  );
}
