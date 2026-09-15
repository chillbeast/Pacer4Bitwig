import { useEffect, useRef } from 'react';
import { toHex } from '../pacer';
import { useMonitor, type MonitorFilter } from '../store/monitor';
import { useUi } from '../store/ui';
import { Button, Segmented, Toggle } from './controls';
import { IconChevron, IconMonitor } from './icons';

function time(t: number): string {
  const d = new Date(t);
  const p = (n: number, l = 2) => String(n).padStart(l, '0');
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}.${p(d.getMilliseconds(), 3)}`;
}

export function MidiMonitor() {
  const open = useUi((s) => s.monitorOpen);
  const entries = useMonitor((s) => s.entries);
  const total = useMonitor((s) => s.total);
  const paused = useMonitor((s) => s.paused);
  const filter = useMonitor((s) => s.filter);
  const showRealtime = useMonitor((s) => s.showRealtime);
  const listRef = useRef<HTMLDivElement>(null);
  const stick = useRef(true);

  const visible = entries.filter((e) =>
    filter === 'all' ? true : filter === 'sysex' ? e.kind === 'sysex' : e.kind === 'channel',
  );
  const shown = visible.slice(-300);

  useEffect(() => {
    const el = listRef.current;
    if (el && stick.current) el.scrollTop = el.scrollHeight;
  }, [shown.length, open]);

  const last = entries[entries.length - 1];

  return (
    <section className={`monitor${open ? ' is-open' : ''}`} aria-label="MIDI monitor">
      <div className="monitor__bar">
        <button
          type="button"
          className="monitor__toggle"
          aria-expanded={open}
          onClick={() => useUi.getState().setMonitorOpen(!open)}
        >
          <IconMonitor size={15} />
          <span>MIDI monitor</span>
          <span className="monitor__count mono">{total}</span>
          {!open && last && (
            <span className={`monitor__last mono dir-${last.direction}`}>
              {last.direction === 'in' ? '←' : '→'} {last.summary}
            </span>
          )}
          <IconChevron size={14} className="monitor__chevron" />
        </button>
        {open && (
          <div className="monitor__tools">
            <Segmented<MonitorFilter>
              label="Monitor filter"
              size="sm"
              value={filter}
              onChange={(v) => useMonitor.getState().setFilter(v)}
              options={[
                { value: 'all', label: 'All' },
                { value: 'sysex', label: 'SysEx' },
                { value: 'channel', label: 'Channel' },
              ]}
            />
            <Toggle label="Clock/sensing" checked={showRealtime} onChange={(v) => useMonitor.getState().setShowRealtime(v)} />
            <Toggle label="Pause" checked={paused} onChange={(v) => useMonitor.getState().setPaused(v)} />
            <Button size="sm" variant="ghost" onClick={() => useMonitor.getState().clear()}>
              Clear
            </Button>
          </div>
        )}
      </div>
      {open && (
        <div
          className="monitor__list"
          ref={listRef}
          role="log"
          aria-live="off"
          onScroll={(e) => {
            const el = e.currentTarget;
            stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
          }}
        >
          {shown.length === 0 ? (
            <p className="monitor__empty">No messages yet. Incoming and outgoing MIDI appears here, decoded.</p>
          ) : (
            <table className="monitor__table">
              <thead className="sr-only">
                <tr>
                  <th>Time</th>
                  <th>Direction</th>
                  <th>Message</th>
                  <th>Bytes</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((e) => (
                  <tr key={e.id} className={`dir-${e.direction}${e.badChecksum ? ' is-bad' : ''}`}>
                    <td className="mono monitor__time">{time(e.time)}</td>
                    <td className="monitor__dir">
                      <span aria-label={e.direction === 'in' ? 'received' : 'sent'}>{e.direction === 'in' ? 'IN' : 'OUT'}</span>
                    </td>
                    <td className="monitor__summary">
                      {e.summary}
                      {e.badChecksum && <span className="tag tag--error">bad checksum</span>}
                    </td>
                    <td className="mono monitor__hex" title={toHex(e.data)}>
                      {toHex(e.data.length > 24 ? e.data.subarray(0, 24) : e.data)}
                      {e.data.length > 24 ? ` … (${e.data.length})` : ''}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </section>
  );
}
