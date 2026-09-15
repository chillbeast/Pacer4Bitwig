import { connectMidi } from '../app/midi';
import { readAllFromDevice } from '../app/operations';
import { isConnected, useDevice } from '../store/device';
import { isSlotEdited, useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button } from './controls';
import { IconCheck, IconClose } from './icons';

/** Dismissible first-run guide: connect → read all → edit → send. */
export function FirstRunGuide() {
  const dismissed = useUi((s) => s.firstRunDismissed);
  const connected = useDevice((s) => isConnected(s.midi));
  const access = useDevice((s) => s.midi.access);
  const busy = useDevice((s) => s.operation !== null);
  const writes = useDevice((s) => s.writesThisSession);
  const hasRead = useEditor((s) => s.slots.some((slot) => slot.device !== null));
  const hasEdits = useEditor((s) => s.slots.some(isSlotEdited));
  if (dismissed) return null;

  const steps = [
    { title: 'Connect', text: 'Plug in the Pacer and allow MIDI with SysEx.', done: connected },
    { title: 'Read all', text: 'Load every preset and keep a backup for the session.', done: hasRead },
    { title: 'Edit', text: 'Pick a switch, change steps and LEDs.', done: hasEdits || writes > 0 },
    { title: 'Send', text: 'Review, save the backup, write and verify.', done: writes > 0 },
  ];
  const next = steps.findIndex((s) => !s.done);

  return (
    <section className="first-run" aria-labelledby="first-run-title">
      <div className="first-run__head">
        <h2 id="first-run-title">Getting started</h2>
        <button type="button" className="btn btn--ghost btn--icon btn--sm" aria-label="Dismiss the getting started guide" onClick={() => useUi.getState().dismissFirstRun()}>
          <IconClose size={14} />
        </button>
      </div>
      <ol className="first-run__steps">
        {steps.map((s, i) => (
          <li key={s.title} className={`first-run__step${s.done ? ' is-done' : ''}${i === next ? ' is-next' : ''}`} aria-current={i === next ? 'step' : undefined}>
            <span className="first-run__num" aria-hidden="true">
              {s.done ? <IconCheck size={13} /> : i + 1}
            </span>
            <span>
              <strong>{s.title}</strong>
              <span className="first-run__text">{s.text}</span>
              <span className="sr-only">{s.done ? ' (done)' : ''}</span>
            </span>
          </li>
        ))}
      </ol>
      <div className="first-run__actions">
        {!connected && access !== 'unsupported' && (
          <Button size="sm" variant="primary" onClick={() => void connectMidi()}>
            Connect MIDI
          </Button>
        )}
        {connected && !hasRead && (
          <Button size="sm" variant="primary" disabled={busy} onClick={() => void readAllFromDevice()}>
            Read all
          </Button>
        )}
        <Button size="sm" variant="ghost" onClick={() => useUi.getState().openDialog('help')}>
          Help & troubleshooting
        </Button>
        <button type="button" className="link-button" onClick={() => useUi.getState().dismissFirstRun()}>
          Don&apos;t show again
        </button>
      </div>
    </section>
  );
}
