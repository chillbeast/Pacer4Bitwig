import { useEffect, useMemo, useState } from 'react';
import { downloadSessionBackup, executeWrite, planWrite, readAndDownloadBackup } from '../app/operations';
import { D6_INDEX, describePart, displayName, slotLabel, slotLongLabel } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button, ProgressBar, Segmented, Toggle } from './controls';
import { Dialog } from './Dialog';
import { IconCheck, IconDownload, IconWarning } from './icons';

export function WriteDialog() {
  const dialog = useUi((s) => s.dialog);
  const request = useUi((s) => s.writeRequest);
  const slots = useEditor((s) => s.slots);
  const midi = useDevice((s) => s.midi);
  const operation = useDevice((s) => s.operation);
  const backup = useDevice((s) => s.backup);
  const backupSkipped = useDevice((s) => s.backupSkipped);
  const writes = useDevice((s) => s.writesThisSession);

  const open = dialog === 'write' && request !== null;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const plan = useMemo(() => (request ? planWrite(request.slots, request.mode) : []), [request, slots]);
  const [excluded, setExcluded] = useState<Set<number>>(new Set());
  const [verify, setVerify] = useState(true);
  const [delayMs, setDelayMs] = useState(20);
  const [expanded, setExpanded] = useState<number | null>(null);

  useEffect(() => {
    if (open) {
      setExcluded(new Set());
      setExpanded(null);
    }
  }, [open]);

  const close = () => useUi.getState().closeDialog();
  const busy = operation !== null;
  const writing = busy && (operation.kind === 'write' || operation.kind === 'verify');
  const connected = isConnected(midi);
  const chosen = plan.filter((p) => !excluded.has(p.index));
  const total = chosen.reduce((n, p) => n + p.parts.length, 0);
  const needsBackup = writes === 0 && !backupSkipped && !backup?.downloaded;
  const seconds = Math.max(1, Math.round((total * (delayMs + 2)) / 1000));

  const targets = chosen.map((p) => slotLabel(p.index)).join(', ');

  return (
    <Dialog
      open={open}
      onClose={close}
      dismissable={!writing}
      size="md"
      title={chosen.length === 1 ? `Write to ${slotLongLabel(chosen[0].index)}` : 'Write to the Pacer'}
      subtitle="Review exactly what will be stored on the device."
      footer={
        <>
          <Button variant="ghost" onClick={close} disabled={writing}>
            {writing ? 'Writing…' : 'Cancel'}
          </Button>
          <Button
            variant="danger"
            disabled={!connected || busy || total === 0 || needsBackup}
            onClick={async () => {
              const ok = await executeWrite(chosen, { verify, delayMs });
              if (ok || !useDevice.getState().operation) close();
            }}
            title={needsBackup ? 'Download a backup first, or skip it' : undefined}
          >
            Write {total} message{total === 1 ? '' : 's'} to {targets || '…'}
          </Button>
        </>
      }
    >
      {!connected && (
        <div className="callout callout--warning" role="alert">
          <IconWarning /> The Pacer is not connected. Connect it and select port 1 to write.
        </div>
      )}

      <ul className="write-list">
        {plan.map((item) => {
          const slot = slots[item.index];
          const include = !excluded.has(item.index);
          return (
            <li key={item.index} className={`write-item${include ? '' : ' is-excluded'}`}>
              <div className="write-item__main">
                {plan.length > 1 && (
                  <input
                    type="checkbox"
                    aria-label={`Include ${slotLabel(item.index)}`}
                    checked={include}
                    disabled={busy}
                    onChange={(e) => {
                      const next = new Set(excluded);
                      if (e.target.checked) next.delete(item.index);
                      else next.add(item.index);
                      setExcluded(next);
                    }}
                  />
                )}
                <span className="slot-chip slot-chip--lg">{slotLabel(item.index)}</span>
                <div className="write-item__text">
                  <strong>“{displayName(item.preset.name) || 'unnamed'}”</strong>
                  <span className="hint">
                    {item.full
                      ? `Full preset (${item.parts.length} messages) — the current content of ${slotLabel(item.index)} on the Pacer is unknown and will be overwritten.`
                      : `${item.parts.length} changed part${item.parts.length === 1 ? '' : 's'} (compared with what was read from the Pacer).`}
                    {slot.device === null && slot.source === 'file' ? ' Imported from a file.' : ''}
                  </span>
                  {item.index === D6_INDEX && verify && <span className="hint hint--warning">D6 cannot be read back, so it cannot be verified.</span>}
                  {item.index === 0 && <span className="hint hint--warning">“Current” is the working preset of the Pacer.</span>}
                </div>
                {!item.full && (
                  <button type="button" className="link-button" onClick={() => setExpanded(expanded === item.index ? null : item.index)}>
                    {expanded === item.index ? 'Hide' : 'Details'}
                  </button>
                )}
              </div>
              {expanded === item.index && (
                <ul className="write-item__parts mono">
                  {item.parts.map((p, i) => (
                    <li key={i}>{describePart(p.part)}</li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
        {plan.length === 0 && <li className="hint">Nothing to write.</li>}
      </ul>

      {needsBackup ? (
        <div className="callout callout--info">
          <div>
            <strong>Save a full backup before the first write</strong>
            <p className="hint">
              {backup
                ? `A full backup was read at ${new Date(backup.time).toLocaleTimeString()} but not saved yet.`
                : 'Reads every preset and the global settings from the Pacer into a .syx file (about 5 seconds). Your edits stay untouched.'}
            </p>
          </div>
          <div className="callout__actions">
            <Button
              variant="primary"
              icon={<IconDownload />}
              disabled={busy || (!backup && !connected)}
              onClick={() => (backup ? downloadSessionBackup() : void readAndDownloadBackup())}
            >
              {backup ? 'Download backup' : 'Read & download backup'}
            </Button>
            <button type="button" className="link-button" onClick={() => useDevice.getState().skipBackup()} disabled={busy}>
              Skip backup
            </button>
          </div>
        </div>
      ) : (
        (backup?.downloaded || writes > 0 || backupSkipped) && (
          <p className="hint backup-ok">
            {backup?.downloaded ? (
              <>
                <IconCheck size={14} /> Backup saved this session.
              </>
            ) : backupSkipped ? (
              'Backup skipped for this session.'
            ) : null}
          </p>
        )
      )}

      <div className="write-options">
        <Toggle label="Verify by reading back" checked={verify} onChange={setVerify} disabled={busy} />
        <div className="labelled">
          <span className="labelled__label">Pause between messages</span>
          <Segmented
            label="Pause between messages"
            size="sm"
            value={delayMs}
            onChange={setDelayMs}
            options={[10, 20, 40].map((v) => ({ value: v, label: `${v} ms`, disabled: busy }))}
          />
        </div>
        <span className="hint">≈ {seconds} s</span>
      </div>

      {operation && (
        <div className="write-progress" role="status" aria-live="polite">
          <span>{operation.label}</span>
          <ProgressBar value={operation.done} max={operation.total} label={operation.label} />
        </div>
      )}
    </Dialog>
  );
}
