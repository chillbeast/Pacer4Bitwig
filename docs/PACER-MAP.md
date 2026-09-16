# Pacer ⇄ Bitwig looper map (shared contract)

The single source of truth for how the **Pacer preset** (written by the editor) and the **Bitwig extension**
(`bitwig/`) talk to each other. Change both sides together, and update this file first.

## Transport

- USB MIDI **port 1** of the Pacer (Windows: in `PACER`, out `PACER`) carries this contract. Port 2
  (`MIDIIN2/MIDIOUT2 (PACER)`) is the Pacer's DAW port; the extension only uses it when *Nektar DAW mode* is
  switched on, with Nektar's protocol (docs/ROADMAP.md), never for the looper.
- Everything on **MIDI channel 16** by default (status `0xBF`; SysEx step channel byte `16`, since `0` means
  "global"). The channel is configurable: extension setting *Looper MIDI channel*, `tools/looper-preset.mjs
  --channel N`, and the channel picker of Pacer Studio's Bitwig Looper template — all three must agree. The CC
  numbers never change.
- The preset-loaded message (CC 119) announces which preset was selected and its LED variant. With the extension's
  LED mode on *Automatic* the LEDs follow the preset.

  | CC 119 value | Preset | LEDs |
  |--------------|--------|------|
  | 127 | Looper | two-colour |
  | 2   | Looper | multi-colour |
  | 17  | FX (docs/FX-PRESET.md) | two-colour |
  | 18  | FX | multi-colour |

  New values follow *kind × 16 + variant* (kind 0 looper, 1 FX, 2 reserved for a combined preset; variant 1
  two-colour, 2 multi-colour). 127 stays the looper's two-colour value so presets already on a Pacer keep working;
  unknown kinds count as the looper. The extension's setting *Active preset* follows this message.
- Preset slots by default: **D1** (preset index `0x13`) = looper, name `LOOPS`; **D2** (`0x14`) = FX, name `FX`
  (space-padded to five characters like every preset name). The FX preset is the looper preset with its own name,
  CC 119 value and two-colour on colours (SW 1–6 green `0x0D`, SW A–D white `0x17`). Never use D6 (`0x18`): the
  Pacer does not answer GET requests for it (known firmware quirk, see `reference/pacer-editor/dumps/README.md`).

## Controls

| Control | SysEx obj | Action CC (step 1) | Colour-slot CCs (steps 2–6) | Looper role (default layout) |
|---------|-----------|--------------------|-----------------------------|------------------------------|
| SW 1    | `0x0D`    | 102                | 20–24                       | Loop track 1                 |
| SW 2    | `0x0E`    | 103                | 25–29                       | Loop track 2                 |
| SW 3    | `0x0F`    | 104                | 30–34                       | Loop track 3                 |
| SW 4    | `0x10`    | 105                | 35–39                       | Loop track 4                 |
| SW 5    | `0x11`    | 106                | 40–44                       | Undo · hold: Redo            |
| SW 6    | `0x12`    | 107                | 45–49                       | Play/stop all loops · hold: clear row |
| SW A    | `0x14`    | 108                | 50–54                       | Previous scene row · hold: tracks ←  |
| SW B    | `0x15`    | 109                | 55–59                       | Next scene row · hold: tracks →      |
| SW C    | `0x16`    | 110                | 60–64                       | Launcher overdub · hold: metronome   |
| SW D    | `0x17`    | 111                | 65–69                       | Tap tempo · hold: transport play/stop |
| FS 1    | `0x18`    | 112                | –                           | One-button looper · hold: clear last loop |
| FS 2    | `0x19`    | 113                | –                           | Play/stop all loops · hold: clear row |
| FS 3    | `0x1A`    | 114                | –                           | (unassigned)                  |
| FS 4    | `0x1B`    | 115                | –                           | (unassigned)                  |
| EXP 1   | `0x36`    | 116 (0–127)        | –                           | Selected track volume         |
| EXP 2   | `0x37`    | 117 (0–127)        | –                           | Master volume                 |
| Preset loaded | `0x7E` setting 1 | 119 (value: see above) | –                    | Extension switches preset, re-sends every LED |

- The "looper role" column lists the extension's defaults; SW 5–6, SW A–D, FS 1–4 and EXP 1–2 are reassignable in
  the Bitwig settings. The CCs never change. The FX preset uses the same CCs with its own roles (docs/FX-PRESET.md).
- Channel 16 is reserved for the looper. The extension passes channels 1–15 to Bitwig as the note input
  "PACER", so other presets must avoid channel 16. Expression pedals set to a MIDI target inject their messages into
  that same note input.
- Switch steps use message type **CC Trigger** (`0x40`): data1 = CC, data2 = **127 (down)**, data3 = **0 (up)**.
  Both edges are needed: the extension measures long-presses itself.
- Expression pedals use message type **CC** (`0x00`): data1 = CC, data2 = min 0, data3 = max 127.
- Control mode (`elm 0x60`) = `0x00` "all steps in one shot".
- Colour-slot CC formula: `20 + switchIndex * 5 + (step - 2)`, switchIndex = 0..9 in table order (SW1..SW6, A..D).

## Preset structure (verified against a full dump of the real device)

- A preset is exactly **189 SysEx messages**: name (1) + 10 switches × 13 (control mode, 6 steps, 6 LED configs)
  + 4 footswitch jacks × 7 (control mode, 6 steps — no LEDs) + 2 expression pedals × 7 (same) + 16 preset-MIDI
  settings. A full backup is 25 × 189 (current + A1..D6) + 37 global messages = 4762.
- Step: `elm (n-1)*6+1..+6` = channel, type, data1, data2, data3, active; each `elm 01 value 00`, the active byte
  without the trailing `00`.
- LED: `elm 0x40+(n-1)*4..+3` = MIDI ctrl, on colour, off colour, LED num.
- Preset-MIDI settings (obj `0x7E`): **5 elements only** (no active byte — the old editor gets this wrong).
- Name: `obj 0x01, elm 0x01, length, chars` (5 chars).
- Unused step as the factory writes it: channel 0, type `0x61` (off), data `0 / 127 / 0`, active 0.
- `tools/looper-preset.mjs` generates `presets/bitwig-looper-{two,multi}-colour-D1.syx`; both line up element for
  element with the real D1 dump (0 missing, 0 length mismatches). D1 held the factory preset `G-MST` — it is in the
  backup.

## LEDs

The Pacer has no "set LED to colour N" message. What it does have (manual, LED settings): with a step's
**LED MIDI Ctrl = 1**, *"LED on and off colors are triggered by the assigned MIDI message received via the USB MIDI
port"* — i.e. the extension echoes the step's own CC back: **127 → on colour, 0 → off colour**.

Two strategies, selectable in the extension settings (`LED mode`) and in the editor's looper template:

### `two-colour` (safe, documented behaviour)

Only step 1 carries LED config: LED MIDI Ctrl 1, on colour per role (loop switches `0x03` Red, others `0x17` White),
off colour `0x00` Off, LED num `0` default. The extension expresses state with blink patterns:

| Loop state          | LED                               |
|---------------------|-----------------------------------|
| empty               | off                               |
| stopped, has clip   | short blip every second           |
| playing             | solid on                          |
| record/play queued  | fast blink (≈ 4 Hz)               |
| recording           | medium blink (≈ 2 Hz)             |

### `multi-colour` — tested on hardware 2026-09-16: **it does not work**

Each stomp switch has exactly one light bar, and its colour comes from **step 1's On colour / Off colour**. Neither
the colour slots on steps 2-6 nor the LED numbers 1-3 change it (LED numbers address the Pacer's labelled indicators,
not the switch bar), and the live colour message Nektar's DAW mode uses (target `0x06`) is ignored outside the DAW
presets. What follows is the original hypothesis, kept for the record.

The manual numbers the colours **1A-12A** (full) and **1b-12b** (dimmed), which are consecutive byte values: 1A =
`0x01`, 1b = `0x02`, 2A = `0x03` … 12A = `0x17`, 12b = `0x18`. A switch can therefore show one bright colour and one
dim colour: the factory "COLOR" preset uses `0x03` / `0x04` for SW 1 and `0x05` / `0x06` for SW 2. That pair, set on
step 1, is the only colour choice a preset really has.

Hypothesis: every step has its own LED config and listens to its own message, so steps 2–6 can act as colour
slots on the same LED. Steps 2–6 are **CC Trigger** on their colour-slot CC (127/0), active = 1, LED MIDI Ctrl 1,
off colour `0x00`, on colours:

| Step | Colour slot | On colour        |
|------|-------------|------------------|
| 1    | white       | `0x17` White     |
| 2    | red         | `0x03` Red       |
| 3    | green       | `0x0D` Green     |
| 4    | amber       | `0x07` Amber     |
| 5    | blue        | `0x11` Blue      |
| 6    | purple      | `0x15` Purple    |

To show colour *k* the extension sends 0 to the previously lit slot CC, then 127 to slot *k*. Because "all steps in
one shot" also *sends* the colour-slot CCs when the switch is pressed, the extension ignores CCs 20–69.

Things to confirm on hardware (LED Lab): (1) the last received message wins when several steps share one LED,
(2) steps respond while not being the active step, (3) the Pacer does not repaint the LED on press/release in a way
that fights the echo.

| Loop state          | Colour                 |
|---------------------|------------------------|
| empty               | off                    |
| stopped, has clip   | amber                  |
| playing             | green                  |
| record queued       | red, blinking          |
| recording           | red                    |
| play queued         | green, blinking        |
| overdub on (SW C)   | red                    |
| undo/redo possible  | white                  |
