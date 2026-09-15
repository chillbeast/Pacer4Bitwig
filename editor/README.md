# Pacer Studio

A hardware-first editor for the **Nektar Pacer** MIDI footswitch controller. It replaces the original
[pacer-editor](https://github.com/francoisgeorgy/pacer-editor) by François Georgy with a new interface and
architecture while keeping its hard-won SysEx protocol knowledge.

- Top-down rendering of the Pacer with live LED colours, labels and message summaries
- Preset browser (Current + A1–D6) with copy/paste, duplicate, swap and drag-to-copy
- Inspector for control modes, six steps per control, LEDs, expression pedals, preset name and on-load MIDI
- Read one preset or a full backup, send only the changed messages, verify by reading back
- "Bitwig Looper" template implementing [`docs/PACER-MAP.md`](../docs/PACER-MAP.md)
- LED Lab to test LED behaviour with plain CCs
- MIDI monitor that decodes Pacer SysEx and channel messages
- Works offline: import/export `.syx` (single preset or full backup) and `.json`
- Undo/redo, dark (default) and light themes, keyboard navigation

## Requirements

- Node.js 20.19+ (developed with Node 24 / npm 11)
- A browser with Web MIDI **and SysEx permission** for hardware access: Chrome, Edge or Opera on desktop.
  Everything else (editing, files, templates) works in any modern browser.

## Run

```sh
cd editor
npm install
npm run dev        # http://localhost:5173
```

Other scripts:

```sh
npm test           # unit tests (vitest)
npm run build      # type-check (tsc, strict) + production build into dist/
npm run preview    # serve the production build on http://localhost:4173
```

## Using it with the Pacer

1. Plug in the Pacer and click the connection pill → **Allow MIDI access** (the browser asks for SysEx permission).
   The Pacer's **port 1** (`PACER`) is selected automatically; `MIDIIN2/MIDIOUT2 (PACER)` is Nektar's DAW port and is not used.
   On Windows only one application can open a MIDI port — close Bitwig (or disable the Pacer there) while editing.
2. **Read all** (or read the selected preset). D6 cannot be read on its own (firmware quirk); *Read all* includes it.
3. Edit. Presets with unsent edits show an amber dot; **Send changes** shows the number of pending SysEx messages.
4. Sending always opens a confirmation that names the target slot(s), and before the first write of a session asks you
   to download a full `.syx` backup (or explicitly skip it). *Verify by reading back* compares the device with the editor.

Nothing is ever sent to the Pacer without a click (Read, Write, or an LED Lab button). The app does not even request
MIDI access until you connect for the first time.

## Architecture

```
src/
  pacer/       framework-free protocol module (no React, no DOM)
    constants.ts   ids, message types, field meanings, colours
    model.ts       Preset / Control / Step / Led / MidiSetting + helpers
    sysex.ts       framing, checksum-carrying builder, element reader
    parse.ts       parseDump(bytes) → presets (+ raw globals)
    encode.ts      encodePreset(preset, index), diffMessages(original, edited, index)
    requests.ts    GET builders (preset, control, all presets, full backup)
    describe.ts    human summaries for steps and MIDI monitor decoding
    json.ts        validated JSON import/export
  midi/        Web MIDI service: port auto-selection, hot-plug, SysEx reassembly,
               request/collect with progress + timeout, paced write queue, error states
  store/       zustand stores: editor (slots + undo/redo), device, ui, monitor
  templates/   Bitwig Looper template (docs/PACER-MAP.md)
  app/         operations that combine MIDI, stores and files (read/write/import/export)
  ui/          React components (hardware rendering, browser, inspector, dialogs, LED Lab)
  styles/      CSS custom-property design tokens and component styles
test/          vitest suites + fixtures (factory dumps, user patch, looper generator output)
```

Key protocol facts (verified against factory dumps and a real device dump):

- `F0 00 01 77 7F <cmd> <tgt> <idx> <obj> <elm…> <cs> F7`, checksum `(128 - sum % 128) % 128` over cmd…last data byte.
- One preset = 189 messages: name, 10 stompswitches × (mode + 6 steps + 6 LEDs), 4 footswitch jacks × (mode + 6 steps),
  2 expression pedals × (mode + 6 steps), 16 on-load MIDI settings (5 elements each, no "active" byte).
- A full backup is 4762 messages: presets 0 (current) + A1…D6, then 37 global messages.
- LED configuration is only returned when requesting a whole preset.

## License

Pacer Studio is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0 or later** (see [LICENSE](LICENSE)). It is based on the protocol work of
pacer-editor © François Georgy, also GPL-3.0-or-later.

## Trademarks

“Nektar Technology”, the logo and all other Nektar Technology product, technology or service names and logos are
trademarks or registered trademarks of Nektar Technology, Inc.

This application is not endorsed by, directly affiliated with, maintained, or sponsored by Nektar Technology.
