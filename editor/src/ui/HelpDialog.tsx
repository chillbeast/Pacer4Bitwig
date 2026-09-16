import type { ReactNode } from 'react';
import { useUi } from '../store/ui';
import { Button } from './controls';
import { Dialog } from './Dialog';

function Topic({ title, children, open }: { title: string; children: ReactNode; open?: boolean }) {
  return (
    <details className="help-topic" open={open}>
      <summary>{title}</summary>
      <div className="help-topic__body">{children}</div>
    </details>
  );
}

export function HelpDialog() {
  const open = useUi((s) => s.dialog === 'help');
  const ui = useUi.getState;
  return (
    <Dialog
      open={open}
      onClose={() => ui().closeDialog()}
      size="md"
      title="Help"
      subtitle="Getting started and troubleshooting"
      footer={
        <>
          <Button variant="ghost" onClick={() => ui().openDialog('shortcuts')}>
            Keyboard shortcuts
          </Button>
          <Button variant="primary" onClick={() => ui().closeDialog()}>
            Done
          </Button>
        </>
      }
    >
      <section className="help-start" aria-label="Getting started">
        <ol className="help-steps">
          <li>
            <strong>Connect.</strong> Plug the Pacer in over USB, click the connection pill and allow MIDI with SysEx. The
            Pacer&apos;s port 1 (“PACER”) is picked automatically.
          </li>
          <li>
            <strong>Read all.</strong> Reads every preset and the global settings, and keeps a full backup for this session.
          </li>
          <li>
            <strong>Edit.</strong> Click a switch on the hardware view, change steps and LEDs in the inspector. Edited presets
            get an amber dot; undo with Ctrl+Z.
          </li>
          <li>
            <strong>Send changes.</strong> Review the target slots, save the backup, write. “Verify by reading back” checks the
            result.
          </li>
        </ol>
      </section>

      <h3 className="help-heading">Troubleshooting</h3>
      <Topic title="“No Web MIDI” or nothing happens when connecting">
        <p>
          Web MIDI with SysEx works in <b>Chrome, Edge and Opera</b> on desktop. Firefox and Safari cannot talk to the Pacer
          (editing and files still work).
        </p>
        <p>
          The browser asks for permission to use MIDI devices <b>with SysEx</b>. If you blocked it: click the site settings icon
          left of the address bar → MIDI devices → Allow, then reload. The page must be served from <span className="mono">localhost</span> or HTTPS.
        </p>
      </Topic>
      <Topic title="“Port busy” or the Pacer is listed but cannot be opened">
        <p>
          On Windows without multi-client MIDI, only one application can open a MIDI port. Bitwig (with the PACER Looper or
          Nektar&apos;s own Pacer script), DAWs or other editors keep the Pacer&apos;s ports open.
        </p>
        <ul>
          <li>Close the other application, or disable the Pacer controller in Bitwig (Settings → Controllers), then click Retry.</li>
          <li>Or install Windows MIDI Services, which lets several applications share the ports.</li>
          <li>Use port 1 (“PACER”); “MIDIIN2/MIDIOUT2 (PACER)” is Nektar&apos;s DAW port and does not answer preset requests.</li>
        </ul>
      </Topic>
      <Topic title="Reading D6 fails">
        <p>
          The Pacer never answers a request for preset D6 (firmware quirk). <b>Read all</b> includes D6 because it uses the full
          backup. Writing D6 works, but it cannot be verified by reading it back — templates avoid D6 for that reason.
        </p>
      </Topic>
      <Topic title="A read is incomplete or times out">
        <p>
          Incomplete presets are requested again automatically (up to two retries). If a preset still has gaps, the message
          names the missing parts; try again with a short USB cable and no other MIDI traffic. Missing parts show defaults in
          the editor — do not write such a preset back without checking it.
        </p>
      </Topic>
      <Topic title="Backups and restoring">
        <p>
          Before the first write in a session Pacer Studio offers to download a full backup (.syx). Keep these files: they are
          the undo for anything written to the Pacer.
        </p>
        <p>
          To restore, use <b>Files → Restore from backup…</b>: pick the file, review the per-preset differences, select presets
          and write them through the normal confirmation. Global settings from a backup can be opened in the Global view.
        </p>
      </Topic>
      <Topic title="Global settings">
        <p>
          Writing global settings is experimental and unverified on hardware; values marked “unverified” are guesses. The
          current state (active preset, programs per channel) is never written.
        </p>
      </Topic>
      <Topic title="The Bitwig Pacer preset does nothing in Bitwig">
        <p>
          The preset&apos;s MIDI channel must match <b>Settings → Controllers → PACER Looper → Looper MIDI channel</b> (default
          16). Selecting the preset makes it announce itself, and the extension answers by writing the whole board — colours,
          the display name and what every switch does. If the LEDs stay as this editor left them, the extension is not
          running or is on another channel.
        </p>
      </Topic>
    </Dialog>
  );
}
