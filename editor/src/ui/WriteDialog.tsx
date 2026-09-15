import { useEffect, useMemo, useState } from 'react';
import { downloadSessionBackup, executeWrite, planSize, planWrite, readAndDownloadBackup } from '../app/operations';
import { D6_INDEX, describePart, displayName, slotLabel, slotLongLabel } from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button, ProgressBar, Segmented, Toggle } from './controls';
import { Dialog } from './Dialog';
import { IconCheck, IconDownload, IconWarning } from './icons';

const OBJ_NAMES: Record<number, string> = {
  0x01: 'settings',
  0x23: 'footswitch modes',
  0x25: 'relay modes',
  0x26: 'encoder',
  0x27: 'expression pedals',
};

export function WriteDialog() {
  const dialog = useUi((s) => s.dialog);
  const request = useUi((s) => s.writeRequest);
  const slots = useEditor((s) => s.slots);
  const globals = useEditor((s) => s.globals);
  const midi = useDevice((s) => s.midi);
  const operation = useDevice((s) => s.operation);
  const backup = useDevice((s) => s.backup);
  const backupSkipped = useDevice((s) => s.backupSkipped);
  const writes = useDevice((s) => s.writesThisSession);

  const open = dialog === 'write' && request !== null;
  const plan = useMemo(
    () => (request ? planWrite(request) : { presets: [], globals: [], globalsFull: false }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [request, slots, globals],
  );
  const [excluded, setExcluded] = useState<Set<number>>(new Set());
  const [includeGlobals, setIncludeGlobals] = useState(true);
  const [verify, setVerify] = useState(true);
  const [delayMs, setDelayMs] = useState(20);
  const [expanded, setExpanded] = useState<number | 'globals' | null>(null);

  useEffect(() => {
    if (open) {
      setExcluded(new Set());
      setIncludeGlobals(true);
      setExpanded(null);
    }
  }, [open]);

  const close = () => useUi.getState().closeDialog();
  const busy = operation !== null;
  const writing = busy && (operation.kind === 'write' || operation.kind === 'verify');
  const connected = isConnected(midi);
  const chosen = {
    presets: plan.presets.filter((p) => !excluded.has(p.index)),
    globals: includeGlobals ? plan.globals : [],
    globalsFull: plan.globalsFull,
  };
  const total = planSize(chosen);
  const needsBackup = writes === 0 && !backupSkipped && !backup?.downloaded;
  const seconds = Math.max(1, Math.round((total * (delayMs + 2)) / 1000));
  const items = plan.presets.length + (plan.globals.length > 0 ? 1 : 0);
  const targets = [...chosen.presets.map((p) => slotLabel(p.index)), ...(chosen.globals.length > 0 ? ['globals'] : [])].join(', ');

  const title =
    chosen.presets.length === 1 && chosen.globals.length === 0
      ? `Write to ${slotLongLabel(chosen.presets[0].index)}`
      : chosen.presets.length === 0 && chosen.globals.length > 0
        ? 'Write global settings'
        : 'Write to the Pacer';

  return (
    <Dialog
      open={open}
      onClose={close}
      dismissable={!writing}
      size="md"
      title={title}
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
        {plan.presets.map((item) => {
          const slot = slots[item.index];
          const include = !excluded.has(item.index);
          return (
            <li key={item.index} className={`write-item${include ? '' : ' is-excluded'}`}>
              <div className="write-item__main">
                {items > 1 && (
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
                    {slot.device === null && slot.source === 'file' ? ' Loaded from a file.' : ''}
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

        {plan.globals.length > 0 && (
          <li className={`write-item write-item--global${includeGlobals ? '' : ' is-excluded'}`}>
            <div className="write-item__main">
              {items > 1 && (
                <input
                  type="checkbox"
                  aria-label="Include global settings"
                  checked={includeGlobals}
                  disabled={busy}
                  onChange={(e) => setIncludeGlobals(e.target.checked)}
                />
              )}
              <span className="slot-chip slot-chip--lg">GLB</span>
              <div className="write-item__text">
                <strong>Global settings</strong>
                <span className="hint">
                  {plan.globalsFull
                    ? `All ${plan.globals.length} global config messages (configs 1–4) — the Pacer's global settings were not read in this session.`
                    : `${plan.globals.length} changed message${plan.globals.length === 1 ? '' : 's'}.`}{' '}
                  The current state (idx 0) is never written.
                </span>
                <span className="hint hint--warning">Experimental: writing global settings has not been verified on hardware.</span>
              </div>
              <button type="button" className="link-button" onClick={() => setExpanded(expanded === 'globals' ? null : 'globals')}>
                {expanded === 'globals' ? 'Hide' : 'Details'}
              </button>
            </div>
            {expanded === 'globals' && (
              <ul className="write-item__parts mono">
                {plan.globals.map((p, i) => (
                  <li key={i}>
                    Config {p.message.index} · {OBJ_NAMES[p.message.obj] ?? `obj 0x${p.message.obj.toString(16)}`}
                  </li>
                ))}
              </ul>
            )}
          </li>
        )}
        {items === 0 && <li className="hint">Nothing to write.</li>}
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
        (backup?.downloaded || backupSkipped) && (
          <p className="hint backup-ok">
            {backup?.downloaded ? (
              <>
                <IconCheck size={14} /> Backup saved this session.
              </>
            ) : (
              'Backup skipped for this session.'
            )}
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
