# Changelog

## 0.3.0 — unreleased

### FX preset (new)

A second Pacer preset (D2, `FX`) that turns the Pacer into a pedalboard for the instruments played live through
Bitwig, while the looper keeps running. See [docs/FX-PRESET.md](docs/FX-PRESET.md).

- **Instruments:** up to four tracks (found by name, saved per project). Hold SW A–C to assign the track selected in
  Bitwig, tap to focus, double-tap to mute. The FX preset follows instruments with its own cursor, so Bitwig's
  selection (and a pinned Push) never moves it and it never moves them.
- **FX switches SW 1–6:** a track remote controls page named "Pacer" when the track has one, otherwise the first six
  devices of the chain. Tap latches, hold is momentary.
- **Snapshots on SW D:** 2–4 per instrument; tap for the next, double-tap for the first, hold to store. The LED blinks
  when the sound changed since.
- **Pedals** on the FX preset have their own targets (defaults: remote controls 7 and 8 of the focused instrument).
- FS 3 / FS 4 default to "focus the next instrument" / "next snapshot"; FS 1–2 keep looping on both presets.

### PACER Looper (Bitwig extension)

- The preset-loaded CC 119 now announces the preset as well as the LED variant (17 / 18 = FX preset); the setting
  *Active preset* follows it and can be switched by hand.
- New hold action **Momentary: tap again on release** for any switch or jack, and FX / instrument / snapshot actions
  assignable on both presets.
- Pedal targets *Focused instrument: remote control 1–8*.
- **Loop tracks start at track** moved from the project to the normal settings and applies to every project. Bitwig 6
  shows a controller's project settings in no panel, so a stale per-project value could neither be seen nor corrected
  - it beat the setting shown in the panel until the controller was switched off and on. Moving the window from the
  Pacer (hold SW A / SW B) writes the setting, and the position is stored as asked for rather than read back after a
  delay, which could scroll the window straight back.

### Pacer Studio and tools

- "Bitwig FX" template (shared with the looper template) and FX roles in the cheat sheet.
- `tools/looper-preset.mjs --preset looper|fx|all` also writes `presets/bitwig-fx-{two,multi}-colour-D2.syx`.
- `tools/pacer-monitor.mjs`: read-only monitor of everything the Pacer sends, for hardware testing.

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
