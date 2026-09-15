import { useEffect, useMemo, useState } from 'react';
import { pickFiles, readFileBytes } from '../app/files';
import { confirmReplace, errorText } from '../app/operations';
import {
  D6_INDEX,
  diffByControl,
  describePart,
  describePartValue,
  displayName,
  parseDump,
  parseGlobalMessages,
  presetsEqual,
  slotLabel,
  type ParseResult,
} from '../pacer';
import { useDevice } from '../store/device';
import { useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button } from './controls';
import { Dialog } from './Dialog';
import { IconFile, IconWarning } from './icons';

interface Source {
  name: string;
  result: ParseResult;
}

export function RestoreDialog() {
  const open = useUi((s) => s.dialog === 'restore');
  const slots = useEditor((s) => s.slots);
  const sessionBackup = useDevice((s) => s.backup);
  const [source, setSource] = useState<Source | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [focus, setFocus] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setSource(null);
      setSelected(new Set());
      setFocus(null);
      setError(null);
    }
  }, [open]);

  const close = () => useUi.getState().closeDialog();

  const load = (name: string, bytes: Uint8Array) => {
    const result = parseDump(bytes);
    if (result.presets.size === 0) {
      setError(`${name} contains no presets.`);
      return;
    }
    setError(null);
    setSource({ name, result });
    const differing = [...result.presets.values()]
      .filter((p) => !presetsEqual(p.preset, slots[p.index]?.preset))
      .map((p) => p.index);
    setSelected(new Set(differing));
    setFocus(differing[0] ?? [...result.presets.keys()][0]);
  };

  const entries = useMemo(() => (source ? [...source.result.presets.values()] : []), [source]);
  const focused = focus !== null ? source?.result.presets.get(focus) : undefined;
  const diff = focused ? diffByControl(slots[focused.index]?.preset ?? null, focused.preset) : [];
  const globals = source ? parseGlobalMessages(source.result.globals) : null;

  const toggle = (index: number) => {
    const next = new Set(selected);
    if (next.has(index)) next.delete(index);
    else next.add(index);
    setSelected(next);
  };

  const restore = async () => {
    if (!source) return;
    const indexes = [...selected].sort((a, b) => a - b);
    if (!(await confirmReplace(indexes, 'Restoring'))) return;
    useEditor.getState().loadPresets(
      indexes.map((i) => {
        const p = source.result.presets.get(i)!;
        return { index: i, preset: p.preset, complete: p.complete };
      }),
      'file',
      `Restore ${indexes.length} preset${indexes.length === 1 ? '' : 's'} from ${source.name}`,
    );
    useUi.getState().openWrite({ slots: indexes, mode: 'slot' });
  };

  return (
    <Dialog
      open={open}
      onClose={close}
      size="lg"
      title="Restore from backup"
      subtitle="Pick presets from a .syx backup, review the differences, then write them through the normal confirmation."
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            Cancel
          </Button>
          {source && (
            <Button variant="primary" disabled={selected.size === 0} onClick={() => void restore()}>
              Load {selected.size} preset{selected.size === 1 ? '' : 's'} & review write…
            </Button>
          )}
        </>
      }
    >
      {!source ? (
        <div className="restore-pick">
          <Button
            variant="primary"
            icon={<IconFile />}
            onClick={() =>
              void pickFiles('.syx,.bin', false).then(async ([file]) => {
                if (!file) return;
                try {
                  load(file.name, await readFileBytes(file));
                } catch (err) {
                  setError(errorText(err));
                }
              })
            }
          >
            Choose a .syx backup…
          </Button>
          {sessionBackup && (
            <Button onClick={() => load(`session backup (${new Date(sessionBackup.time).toLocaleTimeString()})`, sessionBackup.bytes)}>
              Use this session&apos;s backup
            </Button>
          )}
          <p className="hint">Full backups (all presets) and single-preset files both work. Nothing is written until you confirm.</p>
          {error && (
            <p className="connection__error" role="alert">
              {error}
            </p>
          )}
        </div>
      ) : (
        <div className="restore">
          <div className="restore__list">
            <div className="restore__toolbar">
              <strong className="restore__file">{source.name}</strong>
              <span className="hint">{entries.length} presets</span>
              <button type="button" className="link-button" onClick={() => setSelected(new Set(entries.map((e) => e.index)))}>
                All
              </button>
              <button
                type="button"
                className="link-button"
                onClick={() => setSelected(new Set(entries.filter((e) => !presetsEqual(e.preset, slots[e.index]?.preset)).map((e) => e.index)))}
              >
                Differing
              </button>
              <button type="button" className="link-button" onClick={() => setSelected(new Set())}>
                None
              </button>
            </div>
            <ul className="restore__rows">
              {entries.map((e) => {
                const holding = slots[e.index]?.preset ?? null;
                const same = presetsEqual(e.preset, holding);
                return (
                  <li key={e.index} className={`restore-row${focus === e.index ? ' is-focused' : ''}`}>
                    <input
                      type="checkbox"
                      checked={selected.has(e.index)}
                      onChange={() => toggle(e.index)}
                      aria-label={`Restore ${slotLabel(e.index)}`}
                    />
                    <button type="button" className="restore-row__main" onClick={() => setFocus(e.index)} aria-pressed={focus === e.index}>
                      <span className="slot-chip">{slotLabel(e.index)}</span>
                      <span className="restore-row__names">
                        <strong className="mono">{displayName(e.preset.name) || '·····'}</strong>
                        <span className="hint">editor: {holding ? displayName(holding.name) || 'unnamed' : 'empty'}</span>
                      </span>
                      <span className={`badge ${same ? 'badge--same' : 'badge--diff'}`}>{same ? 'same' : holding ? 'differs' : 'new'}</span>
                      {!e.complete && <span className="badge badge--unverified">incomplete</span>}
                      {e.index === D6_INDEX && <span className="badge">no verify</span>}
                    </button>
                  </li>
                );
              })}
            </ul>
            {globals && globals.messages.length > 0 && (
              <div className="callout callout--info restore__globals">
                <span className="hint">This backup also contains global settings ({globals.messages.length} messages).</span>
                <Button
                  size="sm"
                  onClick={() => {
                    useEditor.getState().loadGlobals(globals, 'file');
                    useUi.getState().setView('global');
                    close();
                  }}
                >
                  Open in Global view
                </Button>
              </div>
            )}
          </div>

          <div className="restore__diff" aria-live="polite">
            {focused ? (
              <>
                <h3 className="restore__diff-title">
                  {slotLabel(focused.index)} · backup “{displayName(focused.preset.name)}” vs editor
                </h3>
                {diff.length === 0 ? (
                  <p className="hint">Identical to what the editor holds.</p>
                ) : (
                  diff.map((group) => (
                    <section key={group.group} className="diff-group">
                      <h4>{group.label}</h4>
                      <ul>
                        {group.parts.map((part, i) => (
                          <li key={i}>
                            <span className="diff-part">{describePart(part).replace(`${group.label} · `, '')}</span>
                            <span className="diff-before">{describePartValue(part, slots[focused.index]?.preset ?? null)}</span>
                            <span className="diff-arrow" aria-label="becomes">
                              →
                            </span>
                            <span className="diff-after">{describePartValue(part, focused.preset)}</span>
                          </li>
                        ))}
                      </ul>
                    </section>
                  ))
                )}
              </>
            ) : (
              <p className="hint">Select a preset to see its differences.</p>
            )}
            {!slots.some((s) => s.device) && (
              <p className="hint restore__note">
                <IconWarning size={14} /> Differences are against the editor, not the Pacer. Read the Pacer first to compare with the device.
              </p>
            )}
          </div>
        </div>
      )}
    </Dialog>
  );
}
