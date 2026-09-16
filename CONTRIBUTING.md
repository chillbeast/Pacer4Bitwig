# Contributing to Pacer4Bitwig

Thanks for helping! Pacer owners with different setups are the best testers this project can get.

## Ways to help

- **Hardware reports.** Run the checklist in [docs/LOOPER.md](docs/LOOPER.md#8-hardware-test-checklist) and the
  LED Lab in Pacer Studio, then open an issue with your results (firmware version, OS, Bitwig version).
- **Presets and templates.** Share Pacer presets (`.syx`) or editor templates for other DAWs, loopers and pedals.
- **Code.** Pick an item from [docs/ROADMAP.md](docs/ROADMAP.md) or fix a bug.

## Repository layout

| Folder | Stack | License |
|--------|-------|---------|
| `bitwig/` | Java 21, Maven, DrivenByMoss framework | LGPL-3.0-or-later |
| `editor/` | TypeScript, React, Vite, Web MIDI | GPL-3.0-or-later |
| `tools/` | Node 24 | GPL-3.0-or-later |
| `docs/` | Markdown | same as the code they describe |

## Development setup

```bash
# DrivenByMoss is not on Maven Central: build it once into your local Maven repository
git clone https://github.com/git-moss/DrivenByMoss.git
cd DrivenByMoss && mvn -q install -DskipTests -Dbitwig.extension.directory=target/ext

# Bitwig extension (tests run as part of the build; install copies it into Bitwig's Extensions folder)
cd bitwig && mvn -q install

# Editor
cd editor && npm install && npm run dev      # http://localhost:5173 in Chrome or Edge
npm test && npm run build

# Tools
cd tools && npm install && node pacer-preset.mjs
```

Match the `drivenbymoss.version` in `bitwig/pom.xml` to the DrivenByMoss version you installed.

## Ground rules

1. **The MIDI contract comes first.** Anything that changes CCs, channels or LED behaviour starts with an edit to
   [docs/PACER-MAP.md](docs/PACER-MAP.md), then updates the extension (`PacerMap`), the preset generator
   (`tools/pacer-preset.mjs`) and the editor template together.
2. **Never write to someone's Pacer without asking.** Tools and the editor must confirm the target slot and offer a
   backup before any SysEx SET. GET requests and plain CCs are fine.
3. **Keep logic testable.** In the extension, decisions live in pure classes (`looper/`, `led/`) with JUnit tests;
   `LooperController` applies them and `PacerControllerSetup` only wires hardware. In the editor, the protocol code in
   `src/pacer/` has no UI dependencies and is covered by vitest against real dumps.
4. **Hardware facts need evidence.** When you learn something about the Pacer's SysEx or LEDs, note how you verified
   it (dump, LED Lab, firmware version) in the docs.
5. Java code follows the upstream DrivenByMoss style (4-space indent, space before `(`, `final` everywhere).
   TypeScript follows the editor's lint/format config.

## Pull requests

- One topic per PR, with a short description of what you tested and on which hardware.
- CI must be green: extension build + tests, editor type-check, tests and build, preset generator self-check.
- Update `docs/LOOPER.md` for user-visible extension changes and the editor README for editor features.

## Trademarks

"Nektar", "Pacer" and related names are trademarks of Nektar Technology, Inc. This project is not affiliated with or
endorsed by Nektar. "Bitwig" is a trademark of Bitwig GmbH.
