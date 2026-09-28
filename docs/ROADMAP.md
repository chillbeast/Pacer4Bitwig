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
- A shift layer on every board (SW 6 double-tap, or a jack/switch: latched, for one press, while held) with loop
  tracks 5-8 on the Looper's; up to 8 loop tracks; SW 6's double-tap as a setting
- Pedal pick-up takeover, and loop pedal targets (a loop track, the loop being recorded, all loops keeping their
  balance)
- Event words on the display (`REC 2`, `4 BAR`, `UNDO`...), a bar counter while recording
- The preset check (live writes stop on another preset, a replugged Pacer is repainted), errors contained and
  reported, a console log for bug reports, fewer LED flushes when nothing moves

## Bitwig extension

### The preset check (built, not yet tried on hardware)

`live/PresetGuard` reads the loaded preset's name back with a GET of preset index 0, object 0x01, and stops live
writes when it is not a name the extension wrote (or the stored `PACER`). Open questions for the hardware: does the
Pacer answer a GET of the name object alone (the tools only ever asked for whole presets)? Does a GET flash
`LOAD SYS` like a SET does - if so the 5 s heartbeat should default to off? If the Pacer never answers, the guard
stands aside and everything behaves as before. A cheaper signal to add later: go quiet as soon as messages arrive
that the Bitwig preset never sends (notes, CCs on other channels) - it needs a MIDI callback that sees what the note
input consumes.

### Shift layer follow-ups

- Shift layers for the Mixer and Song modes (they have none; their switches keep their jobs when shifted).
- A shift layer for the footswitch jacks, and jacks that follow the mode like the pedals do.
- A colour setting for shifted switches in the custom layout (now automatic from the action).
- Shift on SW 6 as a hold-and-tap chord is impossible with one foot; a "shift while held" on SW 6 itself would clash
  with the menu - the double-tap is the way in.

### Logged ideas (not built yet)

From the ideas list of wave 3, deferred until the shift layer has settled:

- **Loop multiply:** a loop length option "multiples of the first loop" - later loops close at the next 2x/3x/4x of
  the first loop's length.
- **Chain recording:** closing a loop starts recording on the next empty track at once.
- **Quantize MIDI loops on close** (`Clip.quantize` with a strength setting).
- **Last-bar warning:** the recording loop blinks faster in its final bar when the length is fixed or matched (a
  pattern, so no SysEx).
- **Song chains:** in the Song mode each row plays N bars and then the next launches; counts could ride on the row
  names (`Verse:8, Chorus:4`).
- **Performance capture:** one action records the launcher performance into the arranger.
- **Colour and name recorded clips** per loop track and row, where the API allows naming.
- **Share layouts as text:** one text setting that exports and imports the whole custom layout.
- **Snapshot morph:** a pedal crossfades every remote control between snapshots 1 and 2.
- **Pedal as a switch:** a toe threshold fires an action.
- **Replay real sessions as tests:** capture MIDI with `pacer-monitor`, replay it through `PacerController` in JUnit.
- Not possible as asked: a pedal crossfade *between two rows* - a track plays one clip at a time, so two rows of the
  same loop tracks cannot sound together. A crossfade between two groups of loop tracks would work.

### FX mode ideas

- Loop tracks record the focused instrument (research: can an extension change a track's audio input?).
- Named snapshots, more than four snapshots, snapshots of the pedal controls.

### Looper ideas

- **Free tempo from the first loop:** record the first loop with the transport stopped, then derive the tempo so it
  is a whole number of bars (also on the wave 3 list).
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
