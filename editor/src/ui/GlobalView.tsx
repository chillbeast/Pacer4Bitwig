import { useState } from 'react';
import { pickFiles } from '../app/files';
import { importFiles, loadFactoryPresets, readGlobalsFromDevice } from '../app/operations';
import {
  ACTIVE_PRESET_OPTIONS,
  GLOBAL_CONFIG_SECTIONS,
  GLOBAL_OBJ,
  MSG_TYPES_ENCODER,
  enumLabel,
  getGlobalValue,
  readCurrentState,
  slotLabel,
  unknownElements,
  type GlobalFieldSpec,
} from '../pacer';
import { isConnected, useDevice } from '../store/device';
import { globalPendingParts, isGlobalsEdited, useEditor } from '../store/editor';
import { useUi } from '../store/ui';
import { Button, NumberField, Segmented, SelectField } from './controls';
import { ChannelSelect, MsgTypeSelect } from './fields';
import { IconRead, IconSend, IconWarning } from './icons';

export function GlobalView() {
  const globals = useEditor((s) => s.globals);
  const connected = useDevice((s) => isConnected(s.midi));
  const busy = useDevice((s) => s.operation !== null);
  const [config, setConfig] = useState(1);

  const working = globals.working;
  const state = readCurrentState(working);
  const pending = globalPendingParts(globals).length;
  const edited = isGlobalsEdited(globals);

  const status = !working
    ? 'Not loaded'
    : edited
      ? `Edited · ${pending} message${pending === 1 ? '' : 's'} pending`
      : globals.source === 'device'
        ? 'Read from the Pacer'
        : 'From file · not read from the Pacer';

  return (
    <main className="global-view" aria-label="Global settings">
      <header className="global-view__header">
        <div>
          <h1 className="stage__name">Global settings</h1>
          <p className={`stage__status${edited ? ' is-edited' : ''}`}>{status}</p>
        </div>
        <div className="global-view__actions">
          <Button icon={<IconRead />} disabled={!connected || busy} onClick={() => void readGlobalsFromDevice()}>
            Read from Pacer
          </Button>
          <Button
            variant="primary"
            icon={<IconSend />}
            disabled={pending === 0 || busy}
            badge={pending > 0 ? pending : undefined}
            onClick={() => useUi.getState().openWrite({ slots: [], mode: 'changes', globals: true })}
          >
            Write global changes
          </Button>
        </div>
      </header>

      <div className="callout callout--warning global-view__warning">
        <IconWarning />
        <span>
          Element names come from Nektar&apos;s SysEx notes; value meanings marked <span className="badge badge--unverified">unverified</span>{' '}
          are guesses from dumps. Writing global settings has not been tested on hardware. The current state (active preset,
          programs per channel) is shown read-only and is never written.
        </span>
      </div>

      {!working ? (
        <div className="global-view__empty">
          <p>Global settings come with a full backup or a global .syx file.</p>
          <div className="stage__empty-actions">
            <Button variant="primary" disabled={!connected || busy} onClick={() => void readGlobalsFromDevice()}>
              Read global settings
            </Button>
            <Button onClick={() => void pickFiles('.syx,.bin').then(importFiles)}>Import .syx…</Button>
            <Button onClick={() => void loadFactoryPresets()}>Factory defaults</Button>
          </div>
        </div>
      ) : (
        <>
          <div className="global-view__tabs">
            <span className="labelled__label">Global config</span>
            <Segmented
              label="Global config"
              value={config}
              onChange={setConfig}
              options={[1, 2, 3, 4].map((n) => ({
                value: n,
                label: (
                  <>
                    {n}
                    {state?.currentGlobalConfig === n && <span className="tab-dot" title="Active config" />}
                  </>
                ),
                title: state?.currentGlobalConfig === n ? `Config ${n} (active)` : `Config ${n}`,
              }))}
            />
            {state?.currentGlobalConfig !== undefined && <span className="hint">Active on the Pacer: config {state.currentGlobalConfig}</span>}
          </div>

          <div className="global-grid">
            {GLOBAL_CONFIG_SECTIONS.map((section) => (
              <section key={section.title} className="global-card">
                <h2 className="section-title">{section.title}</h2>
                {section.fields.map((field) => (
                  <GlobalField key={`${field.obj}-${field.elm}`} index={config} field={field} />
                ))}
              </section>
            ))}
            <UnknownCard index={config} />
            {state && (
              <section className="global-card global-card--wide">
                <h2 className="section-title">
                  Current state <span className="section-title__aside">read-only (idx 0)</span>
                </h2>
                <dl className="kv">
                  <div>
                    <dt>Active preset</dt>
                    <dd>{state.activePreset === undefined ? '—' : enumLabel(ACTIVE_PRESET_OPTIONS, state.activePreset)}</dd>
                  </div>
                  <div>
                    <dt>Current user preset</dt>
                    <dd className="mono">{state.currentUserPreset === undefined ? '—' : slotLabel(state.currentUserPreset + 1)}</dd>
                  </div>
                  <div>
                    <dt>Current global config</dt>
                    <dd className="mono">{state.currentGlobalConfig ?? '—'}</dd>
                  </div>
                </dl>
                <div className="table-wrap">
                  <table className="state-table">
                    <thead>
                      <tr>
                        <th>Channel</th>
                        {state.channels.map((c) => (
                          <th key={c.channel} className="mono">
                            {c.channel}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {(
                        [
                          ['Program', 'program'],
                          ['Bank (0x42–0x51)', 'bankA'],
                          ['Bank (0x52–0x61)', 'bankB'],
                        ] as const
                      ).map(([label, key]) => (
                        <tr key={key}>
                          <th>{label}</th>
                          {state.channels.map((c) => (
                            <td key={c.channel} className="mono">
                              {c[key] ?? '—'}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="hint">
                  Nektar&apos;s notes label the two bank ranges inconsistently (LSB/MSB), so they are shown by element range.
                </p>
              </section>
            )}
          </div>
        </>
      )}
    </main>
  );
}

function GlobalField({ index, field }: { index: number; field: GlobalFieldSpec }) {
  const working = useEditor((s) => s.globals.working);
  const base = useEditor((s) => s.globals.base);
  const setGlobal = useEditor((s) => s.setGlobal);
  const value = getGlobalValue(working, index, field.obj, field.elm);
  const original = getGlobalValue(base, index, field.obj, field.elm);
  if (value === undefined) {
    return (
      <div className="global-field is-missing">
        <span className="field__label">{field.label}</span>
        <span className="hint">not in the loaded data</span>
      </div>
    );
  }
  const onChange = (v: number) => setGlobal(index, field.obj, field.elm, v);
  const changed = original !== undefined && original !== value;

  let control;
  switch (field.kind) {
    case 'enum':
      control = (
        <SelectField label={field.label} value={value} onChange={(v) => onChange(Number(v))}>
          {!field.options?.some((o) => o.value === value) && <option value={value}>Value {value}</option>}
          {field.options?.map((o) => (
            <option key={o.value} value={o.value}>
              {o.value} · {o.label}
            </option>
          ))}
        </SelectField>
      );
      break;
    case 'channel':
      control = <ChannelSelect value={value} onChange={onChange} />;
      break;
    case 'msgType':
      control = <MsgTypeSelect value={value} allowed={MSG_TYPES_ENCODER} onChange={onChange} label={field.label} />;
      break;
    default:
      control = <NumberField label={field.label} value={value} onChange={onChange} hint={field.note} />;
  }

  return (
    <div className={`global-field${changed ? ' is-changed' : ''}`}>
      {control}
      <div className="global-field__meta">
        {!field.verified && <span className="badge badge--unverified">unverified</span>}
        <span className="mono global-field__addr">
          {field.obj === GLOBAL_OBJ.SETTINGS ? '' : `obj ${hex(field.obj)} · `}elm {hex(field.elm)} = {value}
          {changed ? ` (was ${original})` : ''}
        </span>
      </div>
      {field.note && <p className="hint">{field.note}</p>}
    </div>
  );
}

function UnknownCard({ index }: { index: number }) {
  const working = useEditor((s) => s.globals.working);
  const unknown = unknownElements(working, index);
  if (unknown.length === 0) return null;
  return (
    <section className="global-card">
      <h2 className="section-title">Undocumented elements</h2>
      <p className="hint">Present in the device data but not described anywhere. Kept unchanged.</p>
      <ul className="raw-list mono">
        {unknown.map((u) => (
          <li key={`${u.obj}-${u.elm}`}>
            obj {hex(u.obj)} · elm {hex(u.elm)} = {u.value}
          </li>
        ))}
      </ul>
    </section>
  );
}

function hex(n: number): string {
  return `0x${n.toString(16).toUpperCase().padStart(2, '0')}`;
}
