# Changelog

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

- Global settings view (experimental writes), device identity / firmware, hardware follow, share links, printable
  cheat sheets, restore-from-backup wizard, new templates (CC toggle pedalboard, program change pedalboard, MMC
  transport), command palette, manual GitHub Pages workflow.

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
