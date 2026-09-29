# Changelog

## 0.3.0 — unreleased

### Modes (new)

The Pacer stays on **one preset** and the extension paints it live: colours, the display name and what every switch
does. Writing to preset index 0 changes only the loaded preset's RAM copy, so the device's stored presets are never
touched and nothing wears the EEPROM. See [docs/LIVE-COLOURS-AND-MODES.md](docs/LIVE-COLOURS-AND-MODES.md).

- **SW 6 is the mode switch.** Hold it for the mode menu, which **stays open when your foot comes off** — SW 1–5
  then pick a mode (and close the menu), SW A–D move the loop track window and the scene row and leave it open. A
  tap of SW 6 closes the menu without changing mode; with the menu shut, a tap toggles between the last two modes.
  The whole gesture works with one foot.
- **Four modes to start with:** *LOOP* (the looper preset's layout: loop tracks on SW 1–4, undo on SW 5, scene rows
  on SW A / SW B with the loop track window on their holds, play/stop all on SW C and launcher overdub / metronome on
  SW D), *FX*
  (instruments on SW 1–4, snapshots on SW 5, their FX switches on SW A–D), *MIX* (solo, mute, input monitoring and the metronome) and
  *SONG* (playing, stopping and navigating scene rows). The built-in boards are fixed; the custom layout (below)
  changes them.
- **Modes on a footswitch jack:** new actions *Next mode*, *Previous mode (toggle)* and *Go to the … mode*, so a
  spare jack or switch can jump straight to one.
- **Your own layout** (*Settings > Custom layout*): tap, double-tap, hold, colour and which of the switch's three
  LEDs lights, for each of the nine switches, plus a five-character display name. It is either a fifth mode of its
  own (`CUST`, starting empty) or **changes a built-in mode in place** — *The custom layout changes = The Looper
  mode* starts from the Looper's board, every switch setting defaults to *As in the mode*, and the Looper keeps its
  slot, name, pedals and beat counter. Tap tempo back on SW D is one setting. Editing it repaints the Pacer straight
  away. A custom mode laid out with an earlier 0.3.0 build (*Settings > Custom mode*) does not carry over:
  the settings were renamed, so set it up again.
- **Loops anywhere:** *Loop track 1–8* as a tap makes any switch or footswitch jack a full loop switch for that track
  (hold, double-tap and hold to record included), so loops can sit on the top row or a jack. *Mute/unmute loop
  track 1–8* gives each loop a mute switch of its own. *Loop tracks* goes up to 8.
- **The expression pedals follow the mode:** ten settings, *EXP 1 · Looper* through *EXP 2 · Custom*. On a Pacer
  with no spare footswitches this is what turns two controls into ten. Defaults: selected volume and master in the
  looper, the instrument's remotes 7 and 8 in FX, volume and send 1 in the mixer, master and a project remote in
  song, nothing in custom. Response curve and heel/toe stay shared - they are the pedal's calibration.
- **The display shows what the mode is doing:** the focused instrument in FX, the row in the Looper and Song (the
  scene's name, or `ROW 3`), the mode's name
  otherwise. *Show what the mode is doing on the display* turns it off.
- **Mode at startup**, including *whatever this project used last* (the default) - a looping project opens in the
  looper, a guitar project in the pedalboard.
- **Switching the extension off darkens the Pacer** and writes `OFF`, so a board nobody is driving does not look
  live.
- **Put the mode name back on the display after a press** (each restore costs one SysEx, so it can be turned off).
- **The mode names itself on the Pacer's display.** Pressing a switch replaces the display with that switch's CC
  readout, so the name is written again shortly after.
- **Colours follow state.** A switch rests at its mode's colour, dimmed, and lights in whatever colour its state
  asks for. Colours are only written when they actually change - a steady trickle of SysEx would leave the Pacer
  showing `LOAD SYS` instead of the mode name.
- **MIX and SONG light the printed legends.** The Pacer has three LEDs per switch on the bottom row — the colour
  strip, the transport-icon row and the word row — and which one lights is ours to choose. MIX puts solo, mute,
  input monitoring and the metronome under `Solo`, `Mute`, `Rec Arm` and `Click`; SONG puts play, rewind, forward,
  stop and transport under the loop, ◀◀, ▶▶, ■ and ▶ icons. The panel labels itself.

### Shift layer, pedals, display, robustness (new)

- **A shift layer on every board.** Double-tap SW 6 (the new *SW 6 double-tap* setting, default *Shift layer
  on/off*) and every switch with something on its shift layer does that; SW 6 turns gold. Actions *Shift layer
  on/off*, *Shift layer for the next press* (SW 6 blinks gold) and *Shift layer while held* put it on a jack or a
  switch too. The Looper's shift layer holds **loop tracks 5–8** on SW 1–4 (resting in cyan), mute all / reset, duplicate row,
  status, fades and transport / tap tempo; the FX mode's holds FX 5, FX 6 and previous / next instrument. The custom
  layout lays it out per switch (*Custom layout: shift layer*: shift tap / double-tap / hold, *As in the mode* by
  default). A switch with nothing on its shift layer keeps its job; changing mode drops the layer.
- **SW 6 double-tap** runs any action. So that a double-tap never toggles the mode first, SW 6's tap now waits for
  the double-tap window; *Nothing* makes it instant again.
- *Go to the Custom mode* action (goes to the mode the custom layout changes, if it changes one).
- **Pedal takeover:** *Jump* (as before) or *Pick up* — after a mode change or a new target the pedal leaves the
  target alone until it reaches or passes its value. MIDI targets pick up from the last value sent.
- **Loop pedal targets:** loop track 1–8 volume, the loop being recorded (else the last recorded), and all loop
  tracks at once keeping their balance.
- **Events on the display:** `REC 2`, `COUNT`, `4 BAR` when a loop closes, `CLR 2`, `CLEAR`, `UNDO`, `REDO`,
  `MUTE2`/`UNMT2`, `MUTED`/`UNMUT`, `STOP`, `PLAY`, `FDOUT`/`FD IN`, `RESET`, `SNAP2`/`SAVE2`, `SHIFT` — for 1.5 s,
  then the name is back (*Show events on the display*).
- **Bar counter:** *While recording, SW A-D count = Bars since the recording started*.
- **The preset check:** the extension reads the loaded preset's name back before painting (and every 5 s) and leaves
  another preset alone until the Bitwig preset is selected again; a Pacer unplugged and plugged back in is painted
  again. *Check the Pacer is on its preset* (every 5 s and before painting / only before painting / off). Not yet
  tried on hardware; a Pacer that does not answer is treated as before.
- **Errors are contained:** an exception in the periodic tick used to stop it for good (no more LEDs, count-ins or
  fades until a restart); now every entry point reports errors to the controller console (once as a notification)
  and carries on.
- **Console log for bug reports:** *Log to the controller console* — presses and what they were taken for, actions,
  mode and shift changes, the preset check, optionally every SysEx message.
- **Lighter when idle:** the LEDs are flushed on every tick only while one of them moves or just after a press.

### Changed

- **Previous / next row while loops play** (new Looper setting): *Move to it and play it* launches the row you move
  to when the song is playing and the row has loops, so a section change is one press; an empty row is left alone so
  the old loops play on while you record the next section.
- The custom mode's settings moved to *Custom layout* with new names (*SW 1 · tap* …), and its *Loop switches*
  setting gave way to the *Loop track* actions. Anything laid out in the custom mode before needs setting again.
- **Hold time for clearing actions now defaults to *Long* (1.5 s).** At the old default of *Normal* the deleting
  holds had no margin over the plain half-second hold, so a foot resting on a loop switch deleted the take it had
  just started. Bitwig keeps the value you already have, so set it by hand if you never changed it.

### Removed

- The *Loop switches* setting: which switches are loop tracks is the mode's business now, capped by *Loop tracks*.
  The custom mode has its own version of it.
- Four settings categories that no longer had settings, and two that still called modes "presets" — *FX preset* is
  now *FX* and *Footswitch jacks FS 1-4 (both presets)* is now *Footswitch jacks FS 1-4*. Bitwig keys settings by
  category, so the handful inside them go back to their defaults once. (*FX* is now called *FX mode*.)

### One preset, and the multi-colour strategy retired

- **The Pacer needs one preset**, `PACER` on D1, generated by `tools/pacer-preset.mjs` or Pacer Studio's *Bitwig
  Pacer* template. The separate FX preset is gone — it is a mode now. Presets already on a Pacer keep announcing
  themselves correctly, so nothing breaks before you rewrite D1.
- **Multi-colour LEDs are gone.** Hardware testing disproved the idea that steps 2–6 could act as colour slots on one
  LED, and live colour writes make it unnecessary: colours now reach the hardware directly. The *LED mode* setting,
  the colour-slot CCs 20–69 and the multi-colour preset variants are removed, and *Multi-colour: … loop* is now
  *Loop colour: …* (renaming a setting resets it to its default).
- **The per-switch settings are gone**, because the modes own the stomp switches. The footswitch jacks and the
  expression pedals keep theirs, and the pedals still have their own targets in the FX mode.
- *Active preset* is gone with the second preset. Selecting the preset on the Pacer (CC 119 = 127) makes the extension
  write the whole board again and keeps the mode you were in; the retired FX preset (17 / 18) still selects the FX
  mode.

### FX mode (new)

A mode that turns the Pacer into a pedalboard for the instruments played live through
Bitwig, while the looper keeps running. See [docs/FX-PRESET.md](docs/FX-PRESET.md).

- **Instruments:** up to four tracks (found by name, saved per project). Hold SW 1–4 to assign the track selected in
  Bitwig, tap to focus, double-tap to mute. The FX mode follows instruments with its own cursor, so Bitwig's
  selection (and a pinned Push) never moves it and it never moves them.
- **FX switches SW A–D:** a track remote controls page named "Pacer" when the track has one, otherwise the first
  devices of the chain. Tap latches, hold is momentary. FX 5 and 6 fit on a footswitch jack.
- **Snapshots on SW 5:** 2–4 per instrument; tap for the next, double-tap for the first, hold to store. The LED blinks
  when the sound changed since.
- **Pedals** in the FX mode have their own targets (defaults: remote controls 7 and 8 of the focused instrument).
- FS 3 / FS 4 default to "focus the next instrument" / "next snapshot"; FS 1–2 keep looping in every mode.

### PACER Looper (Bitwig extension)

- New hold action **Momentary: tap again on release** for any switch or jack, and FX / instrument / snapshot actions
  assignable anywhere.
- Pedal targets *Focused instrument: remote control 1–8*.
- New action **Loop tracks start at the selected track**: after moving tracks around in Bitwig, select the first loop
  track and the window (and the setting) follow it.
- **Loop tracks start at track** moved from the project to the normal settings and applies to every project. Bitwig 6
  shows a controller's project settings in no panel, so a stale per-project value could neither be seen nor corrected
  - it beat the setting shown in the panel until the controller was switched off and on. Moving the window from the
  Pacer (hold SW A / SW B) writes the setting, and the position is stored as asked for rather than read back after a
  delay, which could scroll the window straight back.

### Fixed

- **A press that changes the mode finishes as the switch that was pressed.** Holding a menu slot a moment too long
  ran the new mode's hold on that switch — picking Song and resting on SW 4 faded out every loop, SW 1 deleted
  loop 1, SW 2 reassigned instrument B — and a loop switch set to fire on release started recording when the
  Looper was picked. A held momentary FX switch now also switches off when the mode changes under it.
- **The LED test no longer runs every time the extension starts**, and starting in a mode other than the Looper
  points the pedals at that mode's targets straight away.
- **Hold to record with a count-in** closes the loop when you let go during the count-in; it used to record for
  ever.
- **Stop all, fades and Reset stop only the loop tracks**, not the tracks to the right of them (a backing track).
- **Count-ins, waiting mutes and fades survive the arranger loop wrapping**: they used to wait for a beat the play
  position never reached (the metronome stayed on, a mute blinked for ever, a fade restarted at every wrap).
- **Clear the last recorded loop** no longer forgets other rows' recordings.
- Mode changes paint the new board's real colours in one burst instead of a wrong one and a correction; the Custom
  mode's *Automatic* colour lights loop switches and switches that only have a hold.
- The Custom mode's *Loop switches* stops at SW 1-5 (SW 6 is always the mode switch).

### Pacer Studio and tools

- One template, **Bitwig Pacer**; `tools/pacer-preset.mjs` (was `looper-preset.mjs`) writes
  `presets/bitwig-pacer-D1.syx`, and CI fails when the committed preset or the editor's fixture differ from it.
- `tools/pacer-monitor.mjs`: read-only monitor of everything the Pacer sends, for hardware testing.
- **Backups you can trust:** `pacer-send.mjs` refuses to write when its pre-write backup of the slot comes back short
  (it is saved as `-INCOMPLETE`), like `pacer-backup.mjs` already did; Pacer Studio no longer lets an incomplete
  session backup unlock the first write.
- `led-live-colour.mjs` only writes the loaded preset's RAM copy (its `--slot` wrote EEPROM without a backup) and
  validates its input; `led-colour-lab.mjs` needs `--confirm` for its SysEx writes.
- Pacer Studio: verify waits for the Pacer before reading back (it reported false differences), written global
  settings no longer stay "edited", LED colours use the Pacer manual's names (Magenta, Gold, Green, Dark green,
  Cyan), writes to *Current* warn that they are RAM only, and the LED Lab drives the switch's own CC (on, off, blink)
  instead of the disproved colour slots.

## 0.2.0 — unreleased

### PACER Looper (Bitwig extension)

- **Looper MIDI channel** is configurable (1–16, default 16); changing it restarts the extension. The preset
  generator takes `--channel`.
- **LED mode "Automatic"** (new default): the looper preset announces its LED variant in the preset-loaded message
  (CC 119 value 127 = two-colour, 2 = multi-colour), so the setting cannot mismatch the preset.
- Startup notification with version and MIDI channel; release workflow publishing the `.bwextension` for version tags.
- **Customisation:** separate loop track (1–6) and loop switch (0–6) counts, every non-loop switch assignable;
  tap / double-tap / hold on every switch and jack; per-project loop track position; expression pedal ranges and
  response curves; custom multi-colour loop colours; notification level; names for new rows; "Show looper status".
- **Nektar DAW mode** (opt-in, USB port 2): the Pacer's Track and Transport presets work without Nektar's script.
- **Performance:** hold-to-record mode, beat counter on SW A–D, quantized mutes (next beat / bar), fade out and stop,
  fade in the row, mute/unmute all, solo and input monitoring on the selected track, duplicate row, double / halve
  the selected loop, reset (panic).
- **Looping workflow:** one-button looper, clear last loop, count-in, "match the first loop of the row" loop length,
  longer hold time for clearing actions.
- **Expression pedals** can send MIDI into the "PACER" note input: mod wheel, breath, channel volume, expression,
  brightness, channel pressure, pitch bend up.
- The controller uses two MIDI port pairs since DAW mode; existing controller instances need port 2 assigned.

### Pacer Studio (editor)

- **Online:** hosted on GitHub Pages at <https://chillbeast.github.io/Pacer4Bitwig/>; installable as an offline app
  (manifest + generated service worker, works under the Pages base path).
- Bitwig Looper template: MIDI channel picker and a "matching Bitwig settings" panel; the preset-loaded CC 119 sends
  127 for two-colour and 2 for multi-colour.
- Verified reads with bounded automatic retries, a result summary and timeouts that name the missing request.
- Accessibility: dialog focus trap and focus return, live regions, visible focus rings, AA contrast tokens in both
  themes, reduced motion.
- Tablet layouts from 768 px (preset browser drawer, stacked panels); first-run guide and a Help & troubleshooting
  dialog.
- Global settings view (experimental writes), device identity / firmware, hardware follow, share links, printable
  cheat sheets, restore-from-backup wizard, new templates (CC toggle pedalboard, program change pedalboard, MMC
  transport), command palette.
- Version 0.2.0, shown in About and the command palette.

### Tools

- `looper-preset.mjs`: `--channel`, LED variant in the preset-loaded message, factory-shaped filler for unused
  footswitch steps.

## 0.1.0 — 2026-09-14

- PACER Looper extension: smart loop switches, undo, scene rows, launcher overdub, tap tempo, expression pedals,
  two-colour and experimental multi-colour LED feedback, beat-synced LEDs, exclusive arm, assignable switches,
  pass-through note input for the Pacer's other presets.
- Pacer Studio: hardware-first Web MIDI editor with preset browser, step and LED inspector, MIDI monitor, Bitwig
  Looper template and LED Lab.
- Tools: read-only backup, looper preset generator, guarded preset writer.
