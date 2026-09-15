import { useUi } from '../store/ui';
import { Button } from './controls';
import { Dialog } from './Dialog';
import { IconCheck, IconClose, IconInfo, IconWarning } from './icons';

export function ChoiceDialog() {
  const choice = useUi((s) => s.choice);
  const resolve = useUi((s) => s.resolveChoice);
  return (
    <Dialog
      open={choice !== null}
      onClose={() => resolve(null)}
      size="sm"
      title={choice?.title ?? ''}
      footer={
        choice && (
          <>
            <Button variant="ghost" onClick={() => resolve(null)}>
              {choice.cancelLabel ?? 'Cancel'}
            </Button>
            {choice.choices.map((c, i) => (
              <Button
                key={c.id}
                variant={c.tone === 'danger' ? 'danger' : c.tone === 'primary' ? 'primary' : 'default'}
                autoFocus={i === choice.choices.length - 1}
                onClick={() => resolve(c.id)}
              >
                {c.label}
              </Button>
            ))}
          </>
        )
      }
    >
      <p className="dialog__text">{choice?.body}</p>
    </Dialog>
  );
}

export function Toasts() {
  const toasts = useUi((s) => s.toasts);
  const dismiss = useUi((s) => s.dismissToast);
  return (
    <div className="toasts" aria-live="polite" aria-relevant="additions">
      {toasts.map((t) => (
        <div key={t.id} className={`toast toast--${t.tone}`} role={t.tone === 'error' ? 'alert' : 'status'}>
          <span className="toast__icon" aria-hidden="true">
            {t.tone === 'success' ? <IconCheck size={16} /> : t.tone === 'info' ? <IconInfo size={16} /> : <IconWarning size={16} />}
          </span>
          <div className="toast__body">
            <strong>{t.title}</strong>
            {t.detail && <p>{t.detail}</p>}
            {t.action && (
              <button
                type="button"
                className="link-button"
                onClick={() => {
                  dismiss(t.id);
                  t.action?.run();
                }}
              >
                {t.action.label}
              </button>
            )}
          </div>
          <button type="button" className="toast__close" aria-label="Dismiss" onClick={() => dismiss(t.id)}>
            <IconClose size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}

const SHORTCUTS: [string, string][] = [
  ['Ctrl+Z / Ctrl+Shift+Z', 'Undo / redo'],
  ['Arrow keys', 'Move between switches (hardware view) or presets (browser)'],
  ['Ctrl+C / Ctrl+V / Ctrl+D', 'Copy, paste, duplicate the focused preset'],
  ['Shift+F10', 'Preset actions menu'],
  ['Drag & drop', 'Copy a preset onto another slot (Alt: swap); drop .syx/.json files to import'],
];

export function AboutDialog() {
  const open = useUi((s) => s.dialog === 'about');
  const close = useUi((s) => s.closeDialog);
  return (
    <Dialog open={open} onClose={close} size="md" title="About Pacer Studio" subtitle={`Version ${__APP_VERSION__}`}>
      <div className="about">
        <p>
          A hardware-first editor for the Nektar Pacer MIDI footswitch controller: read, edit and write presets over Web MIDI,
          or work offline with .syx and .json files.
        </p>
        <h3>Credits</h3>
        <p>
          The SysEx protocol knowledge comes from <b>pacer-editor</b> by <b>François Georgy</b>{' '}
          (<span className="mono">github.com/francoisgeorgy/pacer-editor</span>, studiocode.dev), which in turn thanks
          Nektar support for documenting the format. Pacer Studio is a rewrite with a new interface and architecture.
        </p>
        <h3>License</h3>
        <p>
          Pacer Studio is free software, released under the <b>GNU General Public License v3.0 or later</b> (GPL-3.0-or-later).
          It comes with ABSOLUTELY NO WARRANTY. See the LICENSE file.
        </p>
        <h3>Trademarks</h3>
        <p>
          “Nektar Technology”, the logo and all other Nektar Technology product, technology or service names and logos are
          trademarks or registered trademarks of Nektar Technology, Inc. This application is not endorsed by, directly
          affiliated with, maintained, or sponsored by Nektar Technology.
        </p>
        <h3>Safety</h3>
        <p>
          Nothing is sent to the Pacer unless you click Read, Write or an LED Lab button. Every write asks for confirmation,
          shows the target slot and offers a full backup first.
        </p>
        <h3>Keyboard</h3>
        <dl className="shortcuts">
          {SHORTCUTS.map(([k, v]) => (
            <div key={k}>
              <dt className="mono">{k}</dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>
      </div>
    </Dialog>
  );
}
