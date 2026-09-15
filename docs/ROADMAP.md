# Roadmap and research notes

Ideas that are not built yet, with what is already known about them. Pick one, open an issue to say you are on it.

## Bitwig extension

### Nektar DAW mode on USB port 2 (replaces Nektar's script)

Nektar's `PACER.control.js` has to be disabled for PACER Looper because both claim the `PACER` port, which also
switches off the Pacer's built-in **Track** and **Transport** DAW presets. The extension could serve port 2 itself.
What Nektar's script (v1.0.2, API 1) does, observed from its behaviour:

- Ports: in `MIDIIN2 (PACER)` = control, in `PACER` = keyboard note input, out `MIDIOUT2 (PACER)`.
- On init it sends `F0 00 01 77 7F 01 09 01 00 00 01 3E 36 F7` (target `0x09`, value `0x3E` — presumably "DAW
  connected"); on exit `F0 00 01 77 7F 01 09 00 00 00 01 00 75 F7`.
- DAW functions arrive as CC on channel 16. Value 0 is ignored except for held functions and the volume faders.

  | CC | Function | LED feedback (CC 127/0 back on channel 16) |
  |----|----------|--------------------------------------------|
  | 84 | play/pause toggle | playing |
  | 83 | stop | not playing |
  | 85 | record | arranger recording |
  | 80 | loop on/off | loop active |
  | 81 / 82 | rewind / fast forward (repeats while held) | pressed |
  | 89 | metronome on/off | metronome on |
  | 25 | pre-roll none ⇄ 1 bar | pre-roll on |
  | 22 | arranger overdub | overdub on |
  | 86 | go to loop start (stops first) | – |
  | 18 / 19 | move loop region left / right | – |
  | 30 / 31 / 90 | selected track mute / solo / arm | state |
  | 88 | undo | – |
  | 92 / 91 | select previous / next track (double press: new audio track) | pressed |
  | 94 / 93 | preset browser previous / next (double press: commit) | pressed |
  | 15 / 9 | selected track volume absolute / relative | – |
  | 14 / 8 | master volume absolute / relative | – |

- When the active DAW preset changes, the Pacer sends a SysEx report with target `0x10` listing the DAW function
  number of each of its 10 switches; the script answers with LED colours per switch:
  `F0 00 01 77 7F 01 06 18` followed by, for slots 1–10, `00 <slot> 02 <off colour> <on colour>`, then the checksum.
  Target `0x06` looks like a display/RAM target, so this should not wear the EEPROM — to be confirmed.

Work needed: second MIDI port pair in `PacerControllerDefinition`, a `DawModeController`, and a hardware session to
map DAW function numbers to names (log the `0x10` reports while switching DAW presets).

### Looper

- **Sync-to-first-loop:** after the first free-length recording, set the loop length setting (and optionally the
  tempo) from its length so later loops match.
- **Record into the next free slot** (Bitwig's post-recording action) as an alternative to scene rows for takes.
- **Double-tap stop:** second tap within ~300 ms stops the loop immediately instead of quantized.
- **Per-loop volume on EXP while holding a loop switch.**
- **Count-in** from a stopped transport (`ITransport.setPrerollMeasures`).
- **Undo last layer only** for launcher overdub on note clips.

### Hardware questions (need someone with a Pacer)

- Does the multi-colour hypothesis hold (see PACER-MAP.md)? Results decide whether multi-colour becomes the default.
- How long does the Pacer keep a SysEx preset write in RAM vs. EEPROM — is a SET to preset idx 0 ("current") RAM only?
- Which LED numbers (bottom/middle/top) exist on SW 1–6 vs SW A–D?

## Pacer Studio (editor)

- Global settings editor (target `0x05`: MIDI channel, MIDI source, patch up/down, LED dim level, footswitch and relay
  modes, encoder, expression pedal calibration).
- Preset library: share presets as files/links; community template gallery.
- Diff view between device and file, and a "restore from backup" wizard using `backups/*.syx`.
- Installable offline app (PWA) and a hosted build on GitHub Pages (Web MIDI needs HTTPS).
- Desktop wrapper (Tauri) for browsers without Web MIDI.
