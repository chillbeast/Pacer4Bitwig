# Pacer ⇄ Bitwig looper map (shared contract)

The single source of truth for how the **Pacer preset** (written by the editor) and the **Bitwig extension**
(`bitwig/`) talk to each other. Change both sides together, and update this file first.

## Transport

- USB MIDI **port 1** of the Pacer (Windows: in `PACER`, out `PACER`) carries this contract. Port 2
  (`MIDIIN2/MIDIOUT2 (PACER)`) is the Pacer's DAW port; the extension only uses it when *Nektar DAW mode* is
  switched on, with Nektar's protocol (docs/ROADMAP.md), never for the looper.
- Everything on **MIDI channel 16** by default (status `0xBF`; SysEx step channel byte `16`, since `0` means
  "global"). The channel is configurable: extension setting *Looper MIDI channel*, `tools/pacer-preset.mjs
  --channel N`, and the channel picker of Pacer Studio's Bitwig Pacer template — all three must agree. The CC
  numbers never change.
- The preset-loaded message (**CC 119 = 127**) tells the extension one of its presets was selected. Selecting a
  preset on the Pacer discards every live edit, so the extension answers by writing the whole board again. Older
  presets sent 2, 17 or 18 (the looper/FX split and their LED variants); those values still count as ours, so a
  Pacer that has not been rewritten keeps working.
- **One preset**, in slot **D1** (preset index `0x13`) by default, name `PACER` (space-padded to five characters like
  every preset name). There is nothing else to install: what each switch does, what colour it is and what the display
  says are all written live and change with the extension's active mode. Never use D6 (`0x18`): the Pacer does not
  answer GET requests for it (known firmware quirk, see `reference/pacer-editor/dumps/README.md`).

## Controls

| Control | SysEx obj | Action CC (step 1) | Role in the Looper mode |
|---------|-----------|--------------------|--------------------------|
| SW 1    | `0x0D`    | 102                | Loop track 1             |
| SW 2    | `0x0E`    | 103                | Loop track 2             |
| SW 3    | `0x0F`    | 104                | Loop track 3             |
| SW 4    | `0x10`    | 105                | Loop track 4             |
| SW 5    | `0x11`    | 106                | Loop track 5             |
| SW 6    | `0x12`    | 107                | **The mode switch, in every mode**: tap toggles, hold opens the mode menu |
| SW A    | `0x14`    | 108                | Undo · hold: redo        |
| SW B    | `0x15`    | 109                | Play/stop all loops · hold: clear row |
| SW C    | `0x16`    | 110                | Launcher overdub · hold: metronome |
| SW D    | `0x17`    | 111                | Tap tempo · hold: transport play/stop |
| FS 1    | `0x18`    | 112                | One-button looper · hold: clear last loop |
| FS 2    | `0x19`    | 113                | Play/stop all loops · hold: clear row |
| FS 3    | `0x1A`    | 114                | (unassigned)             |
| FS 4    | `0x1B`    | 115                | (unassigned)             |
| EXP 1   | `0x36`    | 116 (0–127)        | Selected track volume    |
| EXP 2   | `0x37`    | 117 (0–127)        | Master volume            |
| Preset loaded | `0x7E` setting 1 | 119 (value 127) | Extension writes the whole board again |

- **The CCs never change**, not even between modes: a mode is a lookup table inside the extension, not a rewrite of
  the Pacer. The "role" column is the Looper mode; the other modes use the same CCs for their own jobs
  (docs/LIVE-COLOURS-AND-MODES.md). Only the footswitch jacks are assignable in the Bitwig settings.
- Channel 16 is reserved for the looper. The extension passes channels 1–15 to Bitwig as the note input
  "PACER", so other presets must avoid channel 16. Expression pedals set to a MIDI target inject their messages into
  that same note input.
- Switch steps use message type **CC Trigger** (`0x40`): data1 = CC, data2 = **127 (down)**, data3 = **0 (up)**.
  Both edges are needed: the extension measures long-presses itself.
- Expression pedals use message type **CC** (`0x00`): data1 = CC, data2 = min 0, data3 = max 127.
- Control mode (`elm 0x60`) = `0x00` "all steps in one shot".
- Only **step 1** of each control is active; steps 2-6 are inert, exactly as the factory leaves unused steps.

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
- `tools/pacer-preset.mjs` generates `presets/bitwig-pacer-D1.syx`, which lines up element for element with a real
  D1 dump (0 missing, 0 length mismatches) and is self-checked at 189 messages. Pacer Studio builds the same preset
  independently and a test compares the two byte for byte. D1 held the factory preset `G-MST` — it is in the backup.

## Live edits: writing to preset index 0

**Verified on hardware 2026-09-16.** A SET to preset index `0x00` ("current", the slot a full backup reports as
`CUR`) edits the *loaded* preset immediately, and only in RAM: selecting any preset restores the stored one, so this
costs no EEPROM wear.

- **Colour:** `01 01 00 <switch obj> 41 01 <on colour> 00 42 01 <off colour>` recolours a switch on the spot.
  Confirmed by cycling SW 1 through all twelve colours.
- **Function:** `01 01 00 <switch obj> 03 01 <cc>` changes what step 1 of a switch sends (element 3 = data 1).
  Confirmed: SW 5 sent CC 60 instead of CC 106 straight away.
- **A whole step in one message:** `01 01 00 <obj> 01 01 <ch> 00 02 01 <type> 00 03 01 <d1> 00 04 01 <d2> 00
  05 01 <d3> 00 06 01 <active>` - channel, message type, all three data bytes and the active flag together.
- **LED MIDI ctrl:** element `0x40` is live-writable, so the extension can put a switch's LED under host control
  even on a preset that was not built for it. With it on the **Pacer stops painting the LED on press** - the host
  owns it completely.
- **Name:** `01 01 00 01 01 <len> <chars>` writes the preset name and **the display follows it live**. 1-5
  characters all work, and a shorter name does not leave the old characters behind. Do **not** append the usual
  `0x00` element terminator after the characters - it corrupts the name.
- **Jacks and pedals** (objects `0x18`-`0x1B`, `0x36`-`0x37`) take live edits the same way.

So the extension can repaint, rename and reassign the Pacer while it plays. State colours, and "modes" that change
the whole board without selecting another preset, both rest on this.

### Rules for a live writer (verified 2026-09-16)

- **One object per message.** Bytes after the first object's elements are parsed as *more elements of that object*,
  not as a second object: a two-object message recoloured the first switch and silently wrote junk into its step 3.
  A full mode change is therefore 10-21 separate messages.
- **Speed is free.** 1000 colour messages went out in 304 ms (~3300/s), the last value landed, and the device still
  answered a full 189-message GET afterwards. Throughput is not a constraint.
- **The display is the constraint.** Every SET shows `LOAD SYS`. A burst of 21 back-to-back messages reads as one
  brief `LOAD SYS`; writes dribbled out a few per second hold it there for as long as they keep coming. Coalesce
  colour changes into occasional bursts, never one per tick. Nothing in the global settings dump looks like it
  switches the message off.
- **A switch press replaces the display with its CC readout** (`CC102 127/000`), so a name is not a standing label.
  The host can re-write the name when the CC arrives and win - one extra message per press.
- **Reading back:** a GET of preset index `0` mirrors RAM, so it verifies live edits; a GET of the stored slot reads
  EEPROM and will not show them. Allow ~250 ms after a write, or the GET still returns the old value.
- **No EEPROM wear:** after a session of live writes the stored slot read back unchanged.

### The colour table (Pacer user guide, page 12)

Twelve colours, each with a full (A) and a dimmed (b) variant in consecutive bytes:

| # | Colour | Full | Dim | | # | Colour | Full | Dim |
|---|--------|------|-----|-|---|--------|------|-----|
| 1 | Magenta | `0x01` | `0x02` | | 7 | Dark green | `0x0D` | `0x0E` |
| 2 | Red | `0x03` | `0x04` | | 8 | Cyan | `0x0F` | `0x10` |
| 3 | Orange | `0x05` | `0x06` | | 9 | Blue | `0x11` | `0x12` |
| 4 | Gold | `0x07` | `0x08` | | 10 | Lavender | `0x13` | `0x14` |
| 5 | Yellow | `0x09` | `0x0A` | | 11 | Purple | `0x15` | `0x16` |
| 6 | Green | `0x0B` | `0x0C` | | 12 | White | `0x17` | `0x18` |

## LEDs

The Pacer has no "set LED to colour N" message. What it does have (manual, LED settings): with a step's
**LED MIDI Ctrl = 1**, *"LED on and off colors are triggered by the assigned MIDI message received via the USB MIDI
port"* — i.e. the extension echoes the step's own CC back: **127 → on colour, 0 → off colour**. Which colours those
are is now settable live (see above).

### Three LED positions per switch (verified 2026-09-16)

The manual's "LED Number" (element `0x43` of a step's LED block; 0 default, 1 bottom, 2 middle, 3 top) chooses
*which physical LED* the switch drives:

| LED number | SW 1-6 | SW A-D |
|------------|--------|--------|
| 1 (bottom) | the colour strip on the switch | the colour strip |
| 2 (middle) | the transport-icon row above it | its single label row |
| 3 (top)    | the word row (`Solo`, `Mute`, `Rec Arm`, `Click`, `Patch`) | nothing - there is no third row |

All of them take the full twelve-colour palette, and the value is live-writable, so a switch's indicator can be
moved between rows per mode.

The legends are **printed per switch**, so they only label anything when the matching function is put on the
matching switch - a real constraint on how modes lay out, and a free win when it lines up:

| | SW 1 | SW 2 | SW 3 | SW 4 | SW 5 | SW 6 |
|---|---|---|---|---|---|---|
| row 3 (word) | `Solo` | `Mute` | `Rec Arm` | `Click` | `Patch ▼` | `Patch ▲` |
| row 2 (icon) | loop | `◀◀` | `▶▶` | `■` | `▶` | `●` |

SW A-D print `Track`, the transport icons, `▼` and `▲` on their single row 2.

**But only one LED per switch lights at a time.** Each of the six steps has its own LED block, and giving steps
1-3 different LED numbers does *not* light three LEDs: the winner takes the LED and the others stay dark (with
only step 2's block enabled the transport icon lit; adding step 1's block pointing at the word row moved the light
there). Across all 25 presets of a full dump, no preset enables more than one LED block on a switch and every
enabled block uses LED number 0, so the factory firmware never does this either.

Presets that light several rows at once do it through the **DAW function** message type (`0x7E`): "when DAW
Transport or Track functions are assigned, the top and middle row icons are illuminated" (manual p.7). That is
firmware behaviour tied to Nektar's DAW protocol, not something the LED configuration can reach.

Two strategies, selectable in the extension settings (`LED mode`) and in the editor's looper template:

### How state reaches the LEDs

Two channels, and keeping them apart is what makes this cheap:

- **Colour** is a live SysEx write (see above). It only goes out when a switch's colour actually changes, because a
  steady trickle of SysEx leaves the Pacer showing `LOAD SYS` instead of the mode name.
- **Bright or dim** is the CC echo, which costs nothing. The extension sends the switch's own CC: **127** for the on
  colour, **0** for the off colour, at whatever rate the blink patterns need.

A switch rests at its mode's colour, dimmed, and lights in its state's colour, so the board reads even when nothing
is playing. Blink patterns still carry the states that are *waiting*:

| Loop state          | LED                                        |
|---------------------|--------------------------------------------|
| empty               | the mode colour, dimmed                    |
| stopped, has clip   | solid, the "stopped" colour                |
| playing             | solid with a dip on the beat               |
| overdubbing         | solid with a dip, the "recording" colour   |
| record/play/stop queued | fast blink (≈ 4 Hz) in the colour it is heading for |
| muted               | solid, the "muted" colour                  |

The four loop colours are settings (*Loop colour: stopped / playing / recording / muted*).

**The multi-colour strategy is gone.** It assumed steps 2-6 could act as colour slots on one LED; hardware testing
on 2026-09-16 disproved it (see "Three LED positions per switch"), and live colour writes make it unnecessary. The
colour-slot CCs 20-69 are no longer part of this contract.
