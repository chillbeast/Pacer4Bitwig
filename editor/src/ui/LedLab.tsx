import { useEffect, useRef, useState } from 'react';
import { sendControlChange } from '../app/operations';
import { CONTROL_BY_KEY, displayName, ledColorInfo, slotLabel, type ControlKey } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { useEditor, type Selection } from '../store/editor';
import {
  LOOPER_CHANNEL,
  LOOPER_LABELS,
  LOOPER_ROLES,
  LOOPER_SLOT_COLOURS,
  LOOPER_SLOT_NAMES,
  LOOPER_SWITCHES,
  buildBitwigLooperPreset,
  colourSlotCc,
} from '../templates/bitwigLooper';
import { Button, Segmented } from './controls';
import { Swatch } from './fields';
import { IconPlay, IconStop, IconWarning } from './icons';
import { PacerDevice } from './PacerDevice';

const HYPOTHESES = [
  {
    id: 'last-wins',
    title: 'The last received message wins when several steps share one LED',
    how: 'Show red, then green: the LED must end up green, not mixed or stuck on red.',
  },
  {
    id: 'inactive-steps',
    title: 'Steps respond while not being the active step',
    how: 'Every colour slot (steps 2–6) lights the LED although the switch is in "all steps at once" mode.',
  },
  {
    id: 'no-repaint',
    title: 'The Pacer does not repaint the LED on press/release in a way that fights the echo',
    how: 'Light a colour, press and release the switch: the LED keeps the colour set over MIDI.',
  },
] as const;

type Verdict = 'untested' | 'pass' | 'fail';
const STORAGE_KEY = 'pacer-studio.ledlab.hypotheses';

function loadVerdicts(): Record<string, Verdict> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : {};
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, Verdict>) : {};
  } catch {
    return {};
  }
}

/** CC of colour slot k (1..6) for a switch: slot 1 is the action CC. */
function slotCc(key: ControlKey, slot: number): number {
  const s = LOOPER_SWITCHES.indexOf(key);
  return slot === 1 ? 102 + s : colourSlotCc(s, slot);
}

export function LedLab() {
  const connected = useDevice((s) => isConnected(s.midi));
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const slot = useEditor((s) => s.slots[s.selectedSlot]);
  const [key, setKey] = useState<ControlKey>('SW1');
  const [lit, setLit] = useState<number | null>(null);
  const [running, setRunning] = useState<'cycle' | 'blink' | null>(null);
  const [rate, setRate] = useState(2);
  const [verdicts, setVerdicts] = useState<Record<string, Verdict>>(loadVerdicts);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);
  const litRef = useRef<number | null>(null);
  const previewPreset = useRef(buildBitwigLooperPreset('multi-colour'));

  const preset = slot.preset ?? previewPreset.current;
  const labels = slot.preset ? slot.labels : LOOPER_LABELS;

  const stop = () => {
    if (timer.current) clearInterval(timer.current);
    timer.current = null;
    setRunning(null);
  };

  useEffect(() => stop, []);
  useEffect(() => {
    if (!connected) stop();
  }, [connected]);
  useEffect(() => {
    stop();
    litRef.current = null;
    setLit(null);
  }, [key]);

  const send = (cc: number, value: number) => sendControlChange(LOOPER_CHANNEL, cc, value);

  const show = (k: number) => {
    const previous = litRef.current;
    if (previous !== null && previous !== k && !send(slotCc(key, previous), 0)) return false;
    if (!send(slotCc(key, k), 127)) return false;
    litRef.current = k;
    setLit(k);
    return true;
  };

  const allOff = () => {
    stop();
    for (let k = 1; k <= 6; k++) send(slotCc(key, k), 0);
    litRef.current = null;
    setLit(null);
  };

  const startCycle = () => {
    stop();
    let k = 1;
    if (!show(k)) return;
    setRunning('cycle');
    timer.current = setInterval(() => {
      k = (k % 6) + 1;
      if (!show(k)) stop();
    }, 800);
  };

  const startBlink = () => {
    stop();
    let on = false;
    setRunning('blink');
    const cc = slotCc(key, 1);
    timer.current = setInterval(() => {
      on = !on;
      if (!send(cc, on ? 127 : 0)) stop();
    }, 500 / rate);
  };

  const setVerdict = (id: string, v: Verdict) => {
    const next = { ...verdicts, [id]: v };
    setVerdicts(next);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      // ignore
    }
  };

  const def = CONTROL_BY_KEY[key];
  const selection: Selection = { kind: 'control', key };

  return (
    <div className="ledlab">
      <section className="ledlab__device" aria-label="Pick a switch">
        <div className="ledlab__intro">
          <h1 className="stage__name">LED Lab</h1>
          <p className="hint">
            Sends plain CCs on channel {LOOPER_CHANNEL} to the Pacer to test how its LEDs react. Load the <b>Bitwig Looper
            (multi-colour)</b> preset on the Pacer first (LED MIDI Ctrl on). Only clicks send MIDI.
          </p>
          <p className="hint">
            Rendering: {slot.preset ? `${slotLabel(selectedSlot)} “${displayName(slot.preset.name)}”` : 'Bitwig Looper multi-colour template (no preset in the selected slot)'}
          </p>
        </div>
        <PacerDevice
          preset={preset}
          labels={labels}
          slotName={slotLabel(selectedSlot)}
          selection={selection}
          onSelect={(sel) => sel.kind === 'control' && setKey(sel.key)}
          ledMode="on"
          previewStep={lit !== null ? lit - 1 : 0}
          switchesOnly
          ariaLabel="Switch picker"
        />
      </section>

      <section className="ledlab__panel" aria-label="LED tests">
        {!connected && (
          <div className="callout callout--warning" role="alert">
            <IconWarning /> Connect the Pacer to run LED tests.
          </div>
        )}
        <header className="inspector__header">
          <div className="inspector__heading">
            <span className="inspector__badge">{def.short}</span>
            <div>
              <h2 className="inspector__title">{def.label}</h2>
              <p className="inspector__meta">{LOOPER_ROLES[key]}</p>
            </div>
          </div>
        </header>

        <div className="inspector__section">
          <div className="section-title">
            <span>Colour slots</span>
            <Button size="sm" variant="ghost" disabled={!connected} onClick={allOff}>
              All off
            </Button>
          </div>
          <ul className="slot-rows">
            {LOOPER_SLOT_COLOURS.map((colour, i) => {
              const k = i + 1;
              const cc = slotCc(key, k);
              return (
                <li key={k} className={`slot-row${lit === k ? ' is-lit' : ''}`}>
                  <Swatch color={colour} />
                  <span className="slot-row__name">
                    Step {k} · {LOOPER_SLOT_NAMES[i]}
                    <span className="hint"> {k === 1 ? 'action CC' : 'colour slot'} · {ledColorInfo(colour).name}</span>
                  </span>
                  <span className="mono slot-row__cc">CC {cc}</span>
                  <Button size="sm" disabled={!connected} onClick={() => send(cc, 127)} aria-label={`Send CC ${cc} value 127`}>
                    127
                  </Button>
                  <Button size="sm" variant="ghost" disabled={!connected} onClick={() => send(cc, 0)} aria-label={`Send CC ${cc} value 0`}>
                    0
                  </Button>
                  <Button size="sm" variant="subtle" disabled={!connected} onClick={() => show(k)} title="Send 0 to the previously shown slot, then 127 here">
                    Show
                  </Button>
                </li>
              );
            })}
          </ul>
        </div>

        <div className="inspector__section">
          <div className="section-title">
            <span>Sequences</span>
          </div>
          <div className="ledlab__tests">
            <Button
              icon={running === 'cycle' ? <IconStop size={14} /> : <IconPlay size={14} />}
              disabled={!connected}
              onClick={() => (running === 'cycle' ? stop() : startCycle())}
            >
              {running === 'cycle' ? 'Stop colour cycle' : 'Colour cycle'}
            </Button>
            <Button
              icon={running === 'blink' ? <IconStop size={14} /> : <IconPlay size={14} />}
              disabled={!connected}
              onClick={() => (running === 'blink' ? stop() : startBlink())}
            >
              {running === 'blink' ? 'Stop blink' : 'Blink action CC'}
            </Button>
            <Segmented
              label="Blink rate"
              size="sm"
              value={rate}
              onChange={(v) => {
                setRate(v);
                if (running === 'blink') stop();
              }}
              options={[1, 2, 4].map((v) => ({ value: v, label: `${v} Hz` }))}
            />
          </div>
          <p className="hint">Colour cycle steps through slots 1–6 every 0.8 s. Blink toggles CC {slotCc(key, 1)} (two-colour strategy).</p>
        </div>

        <div className="inspector__section">
          <div className="section-title">
            <span>Hardware hypotheses</span>
            <span className="section-title__aside">saved in this browser</span>
          </div>
          <ol className="hypotheses">
            {HYPOTHESES.map((h, i) => {
              const v = verdicts[h.id] ?? 'untested';
              return (
                <li key={h.id} className={`hypothesis hypothesis--${v}`}>
                  <div>
                    <strong>
                      {i + 1}. {h.title}
                    </strong>
                    <p className="hint">{h.how}</p>
                  </div>
                  <Segmented<Verdict>
                    label={`Result for hypothesis ${i + 1}`}
                    size="sm"
                    value={v}
                    onChange={(nv) => setVerdict(h.id, nv)}
                    options={[
                      { value: 'untested', label: '?' , title: 'Untested' },
                      { value: 'pass', label: 'Pass' },
                      { value: 'fail', label: 'Fail' },
                    ]}
                  />
                </li>
              );
            })}
          </ol>
        </div>
      </section>
    </div>
  );
}
