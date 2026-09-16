# Roadmap and research notes

Ideas that are not built yet, with what is already known about them. Pick one, open an issue to say you are on it.

## Done so far

- Smart loop switches, one-button looper, clear last loop, scene rows, duplicate row
- Count-in, fixed loop lengths and "match the first loop of the row"
- Exclusive arm, mute/solo, mute all, fade out/in, double/halve loops
- Assignable switches and jacks (tap + hold), safer holds for clearing actions
- Expression pedals: Bitwig parameters or MIDI (CC 1/2/7/11/74, pressure, pitch bend), response curves
- Live colours, names and modes on one preset (docs/LIVE-COLOURS-AND-MODES.md), beat-synced LEDs, LED test
- Pacer Studio editor with the Bitwig Looper template and LED Lab
- FX preset: instrument focus, FX switches on track remote controls or devices, snapshots, momentary holds
  ([FX-PRESET.md](FX-PRESET.md))

## Bitwig extension

### Combined looper + FX preset

A third preset mixing both (e.g. loops on SW 1–4, FX 1–2 on SW 5–6, instruments on SW A–C). Every looper and FX
action is already assignable on both presets, so this is mostly a new CC 119 kind (2), a set of defaults and a
generator/template variant.

### FX preset ideas

- Loop tracks record the focused instrument (research: can an extension change a track's audio input?).
- Named snapshots, more than four snapshots, snapshots of the pedal controls.

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

### Looper ideas

- **Free tempo from the first loop:** record the first loop with the transport stopped, then derive the tempo so it
  is a whole number of bars.
- **Record into the next free slot** (Bitwig's post-recording action) as an alternative to scene rows for takes.
- **Double-tap stop:** a second tap within ~300 ms stops the loop immediately instead of quantized.
- **Momentary record mode:** hold a loop switch to record, release to close.
- **Loop progress on the top row:** show the beat position of a recording loop on SW A–D.
- **Undo last layer only** for launcher overdub on note clips.
- **Row names:** name new scenes (Verse, Chorus, …) from a list in the settings.

### Hardware questions (need someone with a Pacer)

- ~~Does the multi-colour hypothesis hold?~~ No: tested 2026-09-16, one light bar per switch, colour comes from step 1
  only (PACER-MAP.md). Open instead: make the on/off colour pair of each switch configurable in the presets.
- How long does the Pacer keep a SysEx preset write in RAM vs. EEPROM — is a SET to preset idx 0 ("current") RAM only?
- Which LED numbers (bottom/middle/top) exist on SW 1–6 vs SW A–D?

## Pacer Studio (editor)

- Global settings editor (target `0x05`: MIDI channel, MIDI source, patch up/down, LED dim level, footswitch and relay
  modes, encoder, expression pedal calibration).
- Preset library: share presets as files/links; community template gallery.
- Diff view between device and file, and a "restore from backup" wizard using `backups/*.syx`.
- Installable offline app (PWA) and a hosted build on GitHub Pages (Web MIDI needs HTTPS).
- Desktop wrapper (Tauri) for browsers without Web MIDI.
- Verify the inferred protocol details listed in `editor/README.md` (relay mode labels, preset select values, LED
  number meaning, colour `0x7F`).
