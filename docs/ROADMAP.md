# Roadmap and research notes

Ideas that are not built yet, with what is already known about them. Pick one, open an issue to say you are on it.

## Done so far

- Smart loop switches, one-button looper, clear last loop, scene rows, duplicate row
- Count-in, fixed loop lengths and "match the first loop of the row"
- Exclusive arm, mute/solo, mute all, fade out/in, double/halve loops
- Hold to record (release closes the loop, also after a count-in), the beat counter on SW A–D while counting in or
  recording, names for new rows (Verse, Chorus, …) from a list in the settings
- Assignable footswitch jacks and a custom layout in the settings (tap, double-tap, hold, colour, LED) - a mode of
  its own or changes to a built-in mode; loop switches and per-loop mutes on any switch or jack; safer holds for
  clearing actions
- Rows that play along (previous / next row can launch the row moved to), the row on the display
- Expression pedals: Bitwig parameters or MIDI (CC 1/2/7/11/74, pressure, pitch bend), response curves, a target per
  mode
- Five modes on one preset, painted live: LOOP, FX, MIX, SONG and CUST (docs/LIVE-COLOURS-AND-MODES.md); live state
  colours, beat-synced LEDs, LED test
- Nektar DAW mode on USB port 2: serves the Pacer's Track and Transport presets, so Nektar's script can stay off
  (protocol notes below)
- Pacer Studio editor with the Bitwig Pacer template, LED Lab, a global settings view, a "restore from backup"
  wizard with per-control differences, and an installable offline app (PWA) hosted on GitHub Pages
- FX mode: instrument focus, FX switches on track remote controls or devices, snapshots, momentary holds
  ([FX-PRESET.md](FX-PRESET.md))

## Bitwig extension

### Guard live writes when another preset is loaded

A known limitation. The extension paints preset index 0 (the loaded preset's RAM copy) whatever preset is loaded:
it only learns that its own preset is up from CC 119, and nothing tells it when the Pacer leaves it. After switching
the Pacer to another preset, state changes can recolour that preset's switches in RAM (and flash `LOAD SYS`) until a
preset is selected again, which throws the edits away. Ideas: read the name back before a burst (a GET of preset
index 0 mirrors RAM) and skip it unless it is the name the extension last wrote, or go quiet when messages arrive
that the Bitwig Pacer preset never sends.

### FX mode ideas

- Loop tracks record the focused instrument (research: can an extension change a track's audio input?).
- Named snapshots, more than four snapshots, snapshots of the pedal controls.

### Looper ideas

- **Free tempo from the first loop:** record the first loop with the transport stopped, then derive the tempo so it
  is a whole number of bars.
- **Record into the next free slot** (Bitwig's post-recording action) as an alternative to scene rows for takes.
- **Double-tap stop:** a second tap within ~300 ms stops the loop immediately instead of quantized.
- **Undo last layer only** for launcher overdub on note clips.

### Nektar DAW mode (built; protocol notes)

Nektar's `PACER.control.js` has to be disabled for PACER Looper because both claim the `PACER` port, which also
switches off the Pacer's built-in **Track** and **Transport** DAW presets. PACER Looper serves port 2 itself instead
(`pacer/daw/DawModeController`, opt-in setting, docs/LOOPER.md). What Nektar's script (v1.0.2, API 1) does, observed
from its behaviour, and what the extension reproduces:

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
  `F0 00 01 77 7F 01 06 18` followed by, for slots 1–10, `00 <slot> 02 <off colour> <on colour>`, then `1F` (the sum
  of command, target and index, not the usual checksum - reproduced as is) and `F7`. Target `0x06` looks like a
  display/RAM target, so this should not wear the EEPROM — to be confirmed. Outside the Pacer's own DAW presets it is
  ignored (tested 2026-09-16, docs/LIVE-COLOURS-AND-MODES.md).
- Open: the extension gives every slot the same white pair, because the function numbers in the `0x10` report are
  not mapped to names yet. Log the reports while switching DAW presets to map them, then colour slots per function.

### Hardware questions (need someone with a Pacer)

- ~~Does the multi-colour hypothesis hold?~~ No: tested 2026-09-16, one light bar per switch, colour comes from step 1
  only (PACER-MAP.md). Colours are now written live instead (loop colour settings, Custom mode colours).
- ~~Is a SET to preset idx 0 ("current") RAM only?~~ Yes: it edits the loaded preset in RAM, and selecting any preset
  restores the stored one; the stored slot read back unchanged (PACER-MAP.md, verified 2026-09-16).
- ~~Which LED numbers (bottom/middle/top) exist on SW 1–6 vs SW A–D?~~ SW 1–6: colour strip, icon row, word row;
  SW A–D: colour strip and their single label row. Only one lights at a time (PACER-MAP.md, verified 2026-09-16).

## Pacer Studio (editor)

- Global settings: writing them (target `0x05`) is built but untested on hardware, and most value meanings are
  still guesses marked *unverified*.
- Preset library: share presets as files/links; community template gallery.
- Diff view between device and file outside the restore wizard.
- Desktop wrapper (Tauri) for browsers without Web MIDI.
- Verify the inferred protocol details (relay mode labels, preset select values, colour `0x7F`). The LED number
  meaning is verified (PACER-MAP.md).
