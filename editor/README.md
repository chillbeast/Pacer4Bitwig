# Pacer Studio

A hardware-first editor for the **Nektar Pacer** MIDI footswitch controller. It replaces the original
[pacer-editor](https://github.com/francoisgeorgy/pacer-editor) by François Georgy with a new interface and
architecture while keeping its hard-won SysEx protocol knowledge.

- Top-down rendering of the Pacer with live LED colours, labels and message summaries
- Preset browser (Current + A1–D6) with copy/paste, duplicate, swap and drag-to-copy
- Inspector for control modes, six steps per control, LEDs, expression pedals, preset name and on-load MIDI
- Read one preset or a full backup, send only the changed messages, verify by reading back
- **Global settings** view (configs 1–4, current state read-only) with raw values where the meaning is unverified
- **Device identity**: firmware version from the Universal Identity reply in the connection pill and About panel
- **Hardware follow**: press a switch on the Pacer and the editor selects it (toggle "Follow" in the top bar)
- **Share links**: a preset (plus editor labels) packed into the URL hash; opening the link offers an import
- **Printable cheat sheet** of a preset (PACER Looper roles for the Bitwig Looper layout)
- **Restore from backup** wizard with per-preset and per-control differences
- Templates: Bitwig Looper ([`docs/PACER-MAP.md`](../docs/PACER-MAP.md)), CC toggle pedalboard, program change
  pedalboard, MMC transport
- LED Lab to test LED behaviour with plain CCs; MIDI monitor that decodes Pacer SysEx, identity, MMC and channel messages
- Works offline: import/export `.syx` (single preset or full backup) and `.json`
- Undo/redo, command palette (**Ctrl+K**), shortcut overview (**?**), dark (default) and light themes

## Requirements

- Node.js 20.19+ (developed with Node 24 / npm 11)
- A browser with Web MIDI **and SysEx permission** for hardware access: Chrome, Edge or Opera on desktop.
  Everything else (editing, files, templates, share links, printing) works in any modern browser.

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

The build uses relative asset paths by default. For a sub-path deployment set `VITE_BASE`, e.g.
`VITE_BASE=/Pacer4Bitwig/ npm run build`. The GitHub Pages workflow (`.github/workflows/pages.yml`) does exactly that
and only runs when started manually (*Actions → Deploy Pacer Studio to GitHub Pages → Run workflow*; Pages must be set
to "GitHub Actions" in the repository settings).

## Using it with the Pacer

1. Plug in the Pacer and click the connection pill → **Allow MIDI access** (the browser asks for SysEx permission).
   The Pacer's **port 1** (`PACER`) is selected automatically; `MIDIIN2/MIDIOUT2 (PACER)` is Nektar's DAW port and is not used.
   After connecting, the app sends one Universal Identity Request (`F0 7E 7F 06 01 F7`) to show the firmware version.
2. **Read all** (or read the selected preset). D6 cannot be read on its own (firmware quirk); *Read all* includes it and
   also loads the global settings.
3. Edit. Presets with unsent edits show an amber dot; **Send changes** shows the number of pending SysEx messages
   (presets and global settings).
4. Sending always opens a confirmation that names the target slot(s), and before the first write of a session asks you
   to download a full `.syx` backup (or explicitly skip it). *Verify by reading back* compares the device with the editor.

Nothing is sent to the Pacer without a user action (Connect, Read, Write, an LED Lab button). The app does not request
MIDI access until you connect for the first time.

### Global settings

The **Global** view edits the four global configurations (target `0x05`, idx 1–4): MIDI channel, MIDI jack source,
patch up/down, dim LED brightness, footswitch jack modes, relay modes, encoder message and expression pedal values.
Element names come from Nektar's SysEx notes; value meanings are mostly guesses from dumps and are marked **unverified**.
Undocumented elements (e.g. `0x63`) are shown raw and preserved byte for byte. The current state (idx 0: active preset,
current preset/config, program/bank per channel) is read-only — writing it is known to misbehave. Writing global
settings has not been tested on hardware.

### Hardware follow

With **Follow** on, an incoming message that matches step 1 of exactly one control in the displayed preset selects that
control (CC, note, program change, pitch bend, aftertouch, NRPN, MMC). Exact matches win over ranges (e.g. program
switches over FS program-step ranges); ambiguous messages are ignored. Steps on the global channel use the global
MIDI channel when the global settings are loaded, otherwise any channel.

### Share links

*Share* (stage toolbar, preset menu or command palette) copies a URL like `…/#preset=z…`. The payload is a compact
binary form of the preset plus editor labels, deflated and base64url-encoded (~600 characters). Opening the link
validates it strictly (magic, version, value ranges, checksum, labels) and offers to import it into a chosen slot of the
editor — it never writes to the Pacer.

## Architecture

```
src/
  pacer/       framework-free protocol module (no React, no DOM)
    constants.ts   ids, message types (incl. encoder types), field meanings, colours
    model.ts       Preset / Control / Step / Led / MidiSetting + helpers
    sysex.ts       framing, checksum-carrying builder, element reader
    parse.ts       parseDump(bytes) → presets (+ raw globals)
    encode.ts      encodePreset, diffMessages, diffByControl
    global.ts      global settings: parse/encode round trip, write parts (idx 1–4 only), field descriptions
    requests.ts    GET builders (preset, control, all presets, full backup)
    follow.ts      incoming MIDI → control whose step 1 sent it
    share.ts       share-link packing, compression, strict validation
    describe.ts    human summaries for steps, diffs and the MIDI monitor
    json.ts        validated JSON import/export
  midi/        Web MIDI service (port auto-selection, hot-plug, SysEx reassembly, request/collect, paced writes),
               Universal Identity request/reply parsing
  store/       zustand stores: editor (slots + globals + undo/redo), device, ui, monitor
  templates/   Bitwig Looper (docs/PACER-MAP.md, roles from docs/LOOPER.md) and generic pedalboards
  app/         operations that combine MIDI, stores and files (read/write/import/export, identity, follow, share)
  ui/          React components (hardware rendering, browser, inspector, global view, dialogs, LED Lab, palette, cheat sheet)
  styles/      CSS custom-property design tokens and component styles (incl. print styles)
test/          vitest suites + fixtures (factory dumps, user patch, looper generator output)
```

Key protocol facts (verified against factory dumps and a real device dump):

- `F0 00 01 77 7F <cmd> <tgt> <idx> <obj> <elm…> <cs> F7`, checksum `(128 - sum % 128) % 128` over cmd…last data byte.
- One preset = 189 messages: name, 10 stompswitches × (mode + 6 steps + 6 LEDs), 4 footswitch jacks × (mode + 6 steps),
  2 expression pedals × (mode + 6 steps), 16 on-load MIDI settings (5 elements each, no "active" byte).
- A full backup is 4762 messages: presets 0 (current) + A1…D6, then 37 global messages:
  configs 1–4 × obj `0x01` settings (elm `01 09 30 62 63`), `0x23` footswitch modes (elm 1–4), `0x25` relay modes
  (elm 1–4), `0x26` encoder (channel, type, data 1–3), `0x27` expression pedals (elm 1–2); then 17 current-state
  messages, all obj `0x01`: `1E 1A 31` (current preset, active preset, current config) and one message per channel
  with program (`0x32+n`) and two bank bytes (`0x42+n`, `0x52+n`).
- Global messages use the same `elm, 01, value, 00` element shape as presets (last element without the trailing `00`).
- `GET` global settings: `F0 00 01 77 7F 02 05 00 79 F7`.
- LED configuration is only returned when requesting a whole preset.

## License

Pacer Studio is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0 or later** (see [LICENSE](LICENSE)). It is based on the protocol work of
pacer-editor © François Georgy, also GPL-3.0-or-later.

## Trademarks

“Nektar Technology”, the logo and all other Nektar Technology product, technology or service names and logos are
trademarks or registered trademarks of Nektar Technology, Inc.

This application is not endorsed by, directly affiliated with, maintained, or sponsored by Nektar Technology.
