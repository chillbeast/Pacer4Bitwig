import { useEffect, useRef, useState } from 'react';
import { sendControlChange } from '../app/operations';
import { CONTROL_BY_KEY, displayName, slotLabel, type ControlKey } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { useEditor, type Selection } from '../store/editor';
import {
  LOOPER_ACTION_CC,
  LOOPER_CHANNEL,
  looperChannelOf,
  LOOPER_LABELS,
  LOOPER_ROLES,
  buildBitwigLooperPreset,
} from '../templates/bitwigLooper';
import { Button, Segmented } from './controls';
import { IconPlay, IconStop, IconWarning } from './icons';
import { PacerDevice } from './PacerDevice';

/** Verified on a real Pacer on 2026-09-16 (docs/PACER-MAP.md, docs/LIVE-COLOURS-AND-MODES.md). */
const HARDWARE_FACTS = [
  'Each switch has one light bar, and its colours are the on / off colour pair of step 1. Steps 2–6 do not act as colour slots.',
  'With LED MIDI Ctrl on, the switch’s own CC sent back to the Pacer picks the colour: 127 the on colour, 0 the off colour.',
  'SW 1–6 can light their colour strip, the transport icon or the printed word instead (LED number 1–3) — one at a time. SW A–D have no word row.',
  'PACER Looper recolours switches live through preset index 0 (RAM only): selecting any preset brings the stored colours back.',
] as const;

/** The action CC of a switch (step 1): what the LED listens to. */
function actionCc(key: ControlKey): number {
  return LOOPER_ACTION_CC[key];
}

export function LedLab() {
  const connected = useDevice((s) => isConnected(s.midi));
  const selectedSlot = useEditor((s) => s.selectedSlot);
  const slot = useEditor((s) => s.slots[s.selectedSlot]);
  const [key, setKey] = useState<ControlKey>('SW1');
  const [running, setRunning] = useState(false);
  const [rate, setRate] = useState(2);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);
  const previewPreset = useRef(buildBitwigLooperPreset());

  const preset = slot.preset ?? previewPreset.current;
  const labels = slot.preset ? slot.labels : LOOPER_LABELS;

  const stop = () => {
    if (timer.current) clearInterval(timer.current);
    timer.current = null;
    setRunning(false);
  };

  useEffect(() => stop, []);
  useEffect(() => {
    if (!connected) stop();
  }, [connected]);
  useEffect(() => stop(), [key]);

  const channel = looperChannelOf(slot.preset) ?? LOOPER_CHANNEL;
  const send = (cc: number, value: number) => sendControlChange(channel, cc, value);

  const cc = actionCc(key);

  const startBlink = () => {
    stop();
    let on = false;
    setRunning(true);
    timer.current = setInterval(() => {
      on = !on;
      if (!send(cc, on ? 127 : 0)) stop();
    }, 500 / rate);
  };

  const def = CONTROL_BY_KEY[key];
  const selection: Selection = { kind: 'control', key };

  return (
    <div className="ledlab">
      <section className="ledlab__device" aria-label="Pick a switch">
        <div className="ledlab__intro">
          <h1 className="stage__name">LED Lab</h1>
          <p className="hint">
            Sends plain CCs on channel {channel} to the Pacer to test how a switch&rsquo;s LED reacts to its own CC. The
            switch needs <b>LED MIDI Ctrl</b> on for step 1: PACER Looper turns it on while it runs, and on any other
            preset you can set it in the step&rsquo;s LED settings. Only clicks send MIDI.
          </p>
          <p className="hint">
            Rendering: {slot.preset ? `${slotLabel(selectedSlot)} “${displayName(slot.preset.name)}”` : 'Bitwig Pacer template (no preset in the selected slot)'}
          </p>
        </div>
        <PacerDevice
          preset={preset}
          labels={labels}
          slotName={slotLabel(selectedSlot)}
          selection={selection}
          onSelect={(sel) => sel.kind === 'control' && setKey(sel.key)}
          ledMode="on"
          previewStep={0}
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
            <span>Action CC {cc}</span>
          </div>
          <div className="ledlab__tests">
            <Button size="sm" disabled={!connected} onClick={() => send(cc, 127)} aria-label={`Send CC ${cc} value 127`}>
              On (127)
            </Button>
            <Button size="sm" variant="ghost" disabled={!connected} onClick={() => send(cc, 0)} aria-label={`Send CC ${cc} value 0`}>
              Off (0)
            </Button>
            <Button
              icon={running ? <IconStop size={14} /> : <IconPlay size={14} />}
              disabled={!connected}
              onClick={() => (running ? stop() : startBlink())}
            >
              {running ? 'Stop blink' : 'Blink'}
            </Button>
            <Segmented
              label="Blink rate"
              size="sm"
              value={rate}
              onChange={(v) => {
                setRate(v);
                if (running) stop();
              }}
              options={[1, 2, 4].map((v) => ({ value: v, label: `${v} Hz` }))}
            />
          </div>
          <p className="hint">127 shows the on colour, 0 the off colour. Blink alternates them — how PACER Looper flashes a switch without sending any SysEx.</p>
        </div>

        <div className="inspector__section">
          <div className="section-title">
            <span>What the hardware does</span>
          </div>
          <ul className="hint ledlab__facts">
            {HARDWARE_FACTS.map((fact) => (
              <li key={fact}>{fact}</li>
            ))}
          </ul>
        </div>
      </section>
    </div>
  );
}
