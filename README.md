# Pacer4Bitwig

Turn a **Nektar Pacer** into a live-looper pedalboard for **Bitwig Studio**, and edit the Pacer with a modern editor.

| Part | What it is |
|------|------------|
| [`bitwig/`](bitwig) | **PACER Looper** Bitwig extension (Java, DrivenByMoss framework): smart record/play/stop per loop track, undo, scene rows, overdub, tap tempo, expression pedals, LED feedback |
| [`editor/`](editor) | **Pacer Studio**: a Web MIDI preset editor with a hardware-first UI, templates and an LED lab |
| [`tools/`](tools) | Node scripts: read-only backup, looper preset generator, guarded preset writer |
| [`docs/LOOPER.md`](docs/LOOPER.md) | User guide: Bitwig setup, switch functions, LEDs, settings, test checklist |
| [`docs/PACER-MAP.md`](docs/PACER-MAP.md) | The MIDI contract between the Pacer preset and the extension |

## Quick start

```bash
# 1. Back up the Pacer (read-only)
cd tools && npm install && node pacer-backup.mjs

# 2. Build and install the Bitwig extension
cd ../bitwig && mvn -q install
#    Bitwig: Settings > Controllers > Add > Nektar > PACER Looper (ports: PACER / PACER)

# 3. Put the looper preset on the Pacer (overwrites D1; backs D1 up first)
cd ../tools && node looper-preset.mjs && node pacer-send.mjs ../presets/bitwig-looper-two-colour-D1.syx --confirm D1
#    ...or use the Bitwig Looper template in Pacer Studio:
cd ../editor && npm install && npm run dev
```

Requirements: Java 21 + Maven, Node 24, Bitwig Studio 6, DrivenByMoss 26.6.5 installed to the local Maven repository
(see `CLAUDE.md`).

## Licenses and credits

- `bitwig/` is LGPL-3.0-or-later ([bitwig/LICENSE](bitwig/LICENSE)) and bundles
  [DrivenByMoss](https://github.com/git-moss/DrivenByMoss) by Jürgen Moßgraber (LGPL-3.0).
- `editor/` is derived from [pacer-editor](https://github.com/francoisgeorgy/pacer-editor) by François Georgy;
  `editor/` and `tools/` are GPL-3.0-or-later ([LICENSE](LICENSE)).
- Contributions welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/ROADMAP.md](docs/ROADMAP.md).
- "Nektar", "Pacer" and related names are trademarks of Nektar Technology, Inc. This project is not affiliated with
  or endorsed by Nektar.
