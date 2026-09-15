import { create } from 'zustand';
import {
  CONTROL_BY_KEY,
  MSG,
  displayName,
  ledColorInfo,
  msgTypeInfo,
  slotLabel,
  stepSummary,
  type ControlKey,
  type ControlLabels,
  type Preset,
} from '../pacer';
import { useEditor } from '../store/editor';
import { FX_TAP_HOLD } from '../templates/bitwigFx';
import { LOOPER_TAP_HOLD, isLooperLayout, presetAnnouncementOf, type TapHold } from '../templates/bitwigLooper';
import { ledAppearance, ledVars } from './led';

interface PrintState {
  slot: number | null;
}

/** Slot whose cheat sheet is rendered (only visible in print media). */
export const usePrint = create<PrintState>(() => ({ slot: null }));

/** Render the cheat sheet for a slot and open the browser's print dialog. */
export function printCheatSheet(slot: number): void {
  usePrint.setState({ slot });
  const clear = () => {
    window.removeEventListener('afterprint', clear);
    usePrint.setState({ slot: null });
  };
  window.addEventListener('afterprint', clear);
  requestAnimationFrame(() => requestAnimationFrame(() => window.print()));
}

export interface CheatLabel {
  title: string;
  hold?: string;
}

export type BitwigRoles = Readonly<Record<ControlKey, TapHold>>;

/** PACER Looper default roles of a looper-layout preset (FX roles when it announces the FX preset), otherwise null. */
export function bitwigRolesOf(preset: Preset): BitwigRoles | null {
  if (!isLooperLayout(preset)) return null;
  return presetAnnouncementOf(preset)?.kind === 'fx' ? FX_TAP_HOLD : LOOPER_TAP_HOLD;
}

/** Bitwig preset → PACER Looper default roles; otherwise editor labels; otherwise the message type. */
export function cheatLabel(preset: Preset, labels: ControlLabels, key: ControlKey, roles: BitwigRoles | null): CheatLabel {
  if (roles) {
    const role = roles[key];
    return { title: role.tap, hold: role.hold };
  }
  const step = preset.controls[key].steps[0];
  const fallback = !step.active || step.msgType === MSG.OFF ? '—' : msgTypeInfo(step.msgType).name;
  return { title: labels[key] ?? fallback };
}

const TOP: ControlKey[] = ['SWA', 'SWB', 'SWC', 'SWD'];
const BOTTOM: ControlKey[] = ['SW1', 'SW2', 'SW3', 'SW4', 'SW5', 'SW6'];
const JACKS: ControlKey[] = ['FS1', 'FS2', 'FS3', 'FS4', 'EXP1', 'EXP2'];

export function CheatSheet() {
  const slotIndex = usePrint((s) => s.slot);
  const slot = useEditor((s) => (slotIndex === null ? null : s.slots[slotIndex]));
  if (slotIndex === null || !slot?.preset) return null;
  const preset = slot.preset;
  const roles = bitwigRolesOf(preset);
  const onLoad = preset.midi.filter((m) => m.msgType !== MSG.OFF);

  const cell = (key: ControlKey) => <SheetCell key={key} preset={preset} labels={slot.labels} controlKey={key} roles={roles} />;

  return (
    <div className="cheat-sheet" aria-hidden="true">
      <header className="cheat-sheet__header">
        <div>
          <h1>
            {slotLabel(slotIndex)} · {displayName(preset.name) || 'unnamed'}
          </h1>
          <p>
            {roles === FX_TAP_HOLD
              ? 'Bitwig PACER Looper, FX preset — default roles (tap · hold)'
              : roles
                ? 'Bitwig PACER Looper — default roles (tap · hold)'
                : 'Nektar Pacer preset'}
          </p>
        </div>
        <p className="cheat-sheet__meta">
          Pacer Studio · {new Date().toLocaleDateString()}
        </p>
      </header>

      <div className="cheat-sheet__row">
        <div className="sheet-cell sheet-cell--fixed">
          <span className="sheet-cell__badge">P</span>
          <strong>Preset</strong>
          <span className="sheet-cell__msg">fixed function</span>
        </div>
        {TOP.map(cell)}
        <div className="sheet-cell sheet-cell--display">
          <strong className="mono">{displayName(preset.name)}</strong>
          <span className="mono">{slotLabel(slotIndex)}</span>
        </div>
      </div>
      <div className="cheat-sheet__row">{BOTTOM.map(cell)}</div>

      <h2>Rear jacks</h2>
      <div className="cheat-sheet__row">{JACKS.map(cell)}</div>

      {onLoad.length > 0 && (
        <>
          <h2>Sent when the preset loads</h2>
          <ul className="cheat-sheet__list mono">
            {onLoad.map((m, i) => (
              <li key={i}>
                {msgTypeInfo(m.msgType).name} · {stepSummary(m)}
              </li>
            ))}
          </ul>
        </>
      )}
      <footer className="cheat-sheet__footer">
        Labels are editor-side only; they are not stored on the Pacer. LED swatches show step 1 on / off colours.
      </footer>
    </div>
  );
}

function SheetCell({ preset, labels, controlKey, roles }: { preset: Preset; labels: ControlLabels; controlKey: ControlKey; roles: BitwigRoles | null }) {
  const def = CONTROL_BY_KEY[controlKey];
  const control = preset.controls[controlKey];
  const label = cheatLabel(preset, labels, controlKey, roles);
  const active = control.steps.filter((s) => s.active && s.msgType !== MSG.OFF);
  const led = control.leds?.[0];
  return (
    <div className="sheet-cell">
      <span className="sheet-cell__badge">{def.short}</span>
      <strong className="sheet-cell__title">{label.title}</strong>
      {label.hold && <span className="sheet-cell__hold">hold: {label.hold}</span>}
      {active.slice(0, 3).map((s, i) => (
        <span key={i} className="sheet-cell__msg mono">
          {stepSummary(s)}
        </span>
      ))}
      {active.length > 3 && <span className="sheet-cell__msg">+{active.length - 3} more steps</span>}
      {active.length === 0 && <span className="sheet-cell__msg">Off</span>}
      {led && (
        <span className="sheet-cell__leds">
          <i className={`sheet-led led--${ledAppearance(led.onColor)}`} style={ledVars(led.onColor)} />
          {ledColorInfo(led.onColor).name}
          <i className={`sheet-led led--${ledAppearance(led.offColor)}`} style={ledVars(led.offColor)} />
          {ledColorInfo(led.offColor).name}
        </span>
      )}
    </div>
  );
}
