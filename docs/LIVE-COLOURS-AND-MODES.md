# Live colours and modes

**Status: built (modes, live colours, one preset), not yet run through the hardware checklist.** The live writer,
the five modes, the SW 6 mode switch and the state colours are in the extension (`pacer/live/`, `pacer/mode/`); the
multi-colour variant is retired and the generator makes one preset. What is left is the hardware checklist. The
first section is hardware fact, verified on a real Pacer on 2026-09-15/16 with the user watching.

## What the hardware actually does

- **One lit LED per stomp switch**, but its *position* is ours to choose. LED Number 1 is the colour strip, 2 the
  transport-icon row, 3 the word row (`Solo`, `Mute`, `Rec Arm`, `Click`) - and A-D have no row 3. All take the full
  palette and the value is live-writable. Giving several steps different LED numbers does **not** light several LEDs:
  one wins and the rest stay dark. Presets that light two rows at once use the DAW function message type, which is
  firmware behaviour we cannot reach. Full detail in docs/PACER-MAP.md; the multi-colour preset variant stays dead.
- **The word row labels the switch for free** where a mode's function matches the printed word - the strongest use of
  the LED-number choice.
- **Twelve colours**, each with a full (A) and dimmed (b) variant in consecutive bytes: magenta `0x01`, red `0x03`,
  orange `0x05`, gold `0x07`, yellow `0x09`, green `0x0B`, dark green `0x0D`, cyan `0x0F`, blue `0x11`, lavender
  `0x13`, purple `0x15`, white `0x17`.
- **Writing to preset index 0 edits the loaded preset live, in RAM.** Selecting any preset restores the stored one, so
  it costs no EEPROM wear.
  - colour: `F0 00 01 77 7F 01 01 00 <switch obj> 41 01 <on> 00 42 01 <off> <cs> F7`
  - function: `F0 00 01 77 7F 01 01 00 <switch obj> 03 01 <cc> <cs> F7` (element 3 = step 1 data 1)
  - name: `F0 00 01 77 7F 01 01 00 01 01 <len> <chars> <cs> F7` - **and the display follows it live.**
  - a whole step (channel, type, data 1-3, active) goes in one message, and element `0x40` puts the LED under host
    control even on a preset that was not built for it - the Pacer then stops painting the LED on press.
  - All confirmed on hardware: SW 1 cycled through all twelve colours, SW 5 sent CC 60 instead of CC 106, the
    display read `MODE1` and then `LUP`, and the stored slot read back unchanged afterwards.
- **One object per message.** Bytes after the first object's elements are parsed as more elements of *that* object,
  so a whole-board change is at most 11 messages: ten switches (each with its LED row) plus the name. Speed is not a
  problem: 1000 messages in 304 ms (~3300/s).
- **Every SET flashes `LOAD SYS` on the display.** A burst of 21 back-to-back messages reads as one brief flash;
  writes dribbled out a few per second hold `LOAD SYS` there continuously. Colour changes must be coalesced into
  bursts, never sent per tick.
- **A switch press replaces the display with its CC readout**, so a name is not a standing label - the host has to
  re-write it when the CC arrives.
- **Nektar's live colour message** (target `0x06`, used by DAW mode) is ignored outside the Pacer's own DAW presets,
  with and without the "DAW connected" message.
- **CC 119 arrives on every preset selection** (seen in the MIDI monitor log), so preset announcements are reliable.

Experiment scripts: `tools/led-colour-lab.mjs` (Nektar's colour message, needs `--confirm`),
`tools/led-colour-chart.mjs` (builds a colour chart for a slot), `tools/led-live-colour.mjs` (one live colour write,
RAM only), `tools/live-name.mjs` (a live name write), `tools/led-layout-test.mjs` (the LED-number test). The two
that build a slot's `.syx` leave the writing to `pacer-send.mjs`, which backs the slot up first.

## The plan

### 1. Live colour engine

A writer next to `led/SwitchLedWriter` that sends the colour pair of a switch when the wanted colour changes, while
on/off still goes through the CC echo (`127` = on colour, `0` = off colour). Points to settle:

- ~~Where it sits.~~ `live/LiveBoard` is the only place that writes the SysEx: it remembers what the Pacer shows and
  sends a switch's colour pair only when it changes. `SwitchLedWriter` keeps sending the CC echo, bright or dim.
- ~~How many SysEx messages per second are safe.~~ Measured: ~3300/s with no drops. The limit is not throughput but
  the `LOAD SYS` display message - sustained writes keep it on screen, so the engine must only write on real state
  changes and send them as one burst.
- ~~What the extension leaves behind on exit.~~ Switching the extension off darkens every switch and writes `OFF` to
  the display, so a board nobody drives does not look live. Selecting a preset brings its stored colours back.

### 2. Colours for state

As built; the four loop colours are settings (*Loop colour: stopped / playing / recording / muted*):

| Loop switch | Colour |
|-------------|--------|
| empty | dark green, dimmed |
| recording | red |
| playing | green |
| stopped, has a loop | gold |
| muted | blue |
| waiting (record/play/stop) | the colour it is heading for, blinking |

Other switches: undo/redo white, transport gold, overdub red, metronome white, row navigation lavender. In the FX
mode: FX switches green when on, instruments in the nearest colour to their track colour, snapshots by index.

### 3. Modes instead of presets (built 2026-09-16)

Implemented as `pacer/mode/`: `Mode` holds the five boards (LOOP, FX, MIX, SONG, CUSTOM - the Custom board is built
from the settings), `ModeState` is the SW 6 gesture, `SwitchRole` decides where a press goes, `ModePainter` turns a
mode into colour and name writes, and `live/LiveBoard` sends them while dropping anything that would not change. All
of it is unit-tested.

**One preset, and the extension paints everything on top of it.** The stored preset is only a fallback and a
bootstrap: ten switches as CC Trigger on CCs 102-111 channel 16, LED MIDI ctrl **off** (so the board still lights
without Bitwig; the extension turns it on live), and the preset-loaded CC 119 so the extension knows when the Pacer
arrives on it. Everything else is live.

**Switch CCs never change at runtime.** A mode is an extension-side lookup table, not a rewrite of the Pacer - the
extension already decides what an incoming CC does. So a mode change writes only *colours and the name*: 10 colour
messages + 1 name message, sent as one burst = one brief `LOAD SYS`. Live step rewriting stays a tool for building
the stored preset, not a runtime mechanism.

#### SW 6 is the mode switch

Chosen for its position: it sits under the display and encoder, physically apart from the 1-5 cluster, so it reads
as a system switch rather than a performance slot.

- **tap** - toggle between the last two modes (or, with the menu open, close it and change nothing)
- **hold** - the mode menu, and the nine other switches become its slots
- **release** - the menu stays open, since a foot cannot hold one switch and press another. A mode slot goes there
  and closes the menu; the navigation slots leave it open so they can be pressed again; a tap of SW 6 closes it.

| In the menu | Slot | Why |
|--------------|------|-----|
| 1-5 | Looper, FX, Mixer, Song, Custom | direct access, no cycling |
| A / B | loop tracks left / right | needed from every mode, so pay for it once |
| C / D | row down / up | the panel already prints `▼` and `▲` on those two switches |

#### What a mode holds

- a 5-character name for the display
- per performance switch (1-5, A-D): tap / double-tap / hold action, colour, and **LED position**
- both expression pedal targets - the biggest win with no spare footswitches, since two pedals that re-point per
  mode multiply the surface without new hardware

The four footswitch jacks are not part of a mode: they are global, the same in every mode, and have defaults (FS 1
one-button looper, hold: clear the last recorded loop; FS 2 play row / stop all, hold: clear the row; FS 3 focus the
next instrument; FS 4 next snapshot). Any action fits on them, including the mode actions.

That is 9 switches x 3 gestures = 27 bindings per mode, on top of the existing 53 actions, which all already exist
and are assignable. The mode system is plumbing, not new behaviour.

#### Fixed modes first

Built in, not user-editable, until the ergonomics are proven. Designing a settings system for modes nobody has
specified means guessing twice, and 5 modes x 27 bindings would be 135 entries in Bitwig's panel. The set: **LOOP**,
**FX**, **MIX**, **SONG** and **CUST**.

What did turn out to matter was changing one switch of a built-in mode (tap tempo where overdub is). So there is one
**custom layout** (*Settings > Custom layout*, `mode/CustomBoard`): either the fifth mode, `CUST`, or laid over a
built-in mode, which it then changes in place - every switch setting starts at *As in the mode*, so one setting
changes one switch, and the mode keeps its slot, name and pedals. 45 settings rather than 135, at the price of one
changed mode at a time. A switch whose tap is *Loop track n* is a loop switch wherever it sits, so a custom layout can
put loops on the top row or on a jack.

The **shift layer** doubles every board without a second menu (`mode/ShiftLayer`, `mode/ShiftedBoard`): SW 6's
double-tap (a setting; the tap then waits out the double-tap window) or a shift action on a jack or switch raises it,
and every switch with something on its shift layer does that - the Looper's holds loop tracks 5-8. Raising or
dropping it is a board change like a mode change: one burst of at most ten colour messages, plus the `SHIFT` event
word. SW 6 turns gold as the reminder; its colour follows the shift state, never the clock (the "for one press" blink
is a pattern).

Open questions:

- ~~What are modes 4 and 5?~~ Song, and a custom board laid out in the settings.
- Should a mode change the loop track window or the focused instrument too? Default no - it makes modes stateful
  and harder to reason about. Add it if it is missed.
- ~~Is the name worth re-writing on every press to keep it on the display?~~ It is a setting: *Put the mode name
  back on the display after a press* (Modes, on by default). Each re-write costs one message and a `LOAD SYS` flash;
  switch it off to leave the CC readout up.

### 4. Retire multi-colour (done)

`LedMode` is gone, and with it the colour-slot CCs in the contract, the multi-colour preset variants in the generator
and Pacer Studio, and the multi-colour rows in the docs. There is one preset (`tools/pacer-preset.mjs` →
`presets/bitwig-pacer-D1.syx`, or Pacer Studio's "Bitwig Pacer" template); it ships with LED MIDI ctrl off and the
extension paints it.

## Where things stand

- **Built:** the five modes, the SW 6 mode switch and its menu, live state colours and the one preset. The mechanism
  underneath is proven on hardware: colours, names, switch assignments, LED host control, jacks and pedals are all
  live-writable with no EEPROM cost; see the rules for a live writer in docs/PACER-MAP.md.
- **Not yet run through the hardware checklists** (docs/LOOPER.md section 10, docs/FX-PRESET.md): only the pedals,
  the preset announcement and the LED findings above are verified.
- The extension used to paint preset index 0 whatever preset was loaded. The preset check now reads the loaded
  preset's name back before painting and leaves another preset alone (`live/PresetGuard`); it is not verified on
  hardware yet - see "The preset check" in docs/ROADMAP.md.
- Event words (`REC 2`, `UNDO`) cost two name writes each: only for things the player did, never per beat.
- At the live tests (2026-09-16): extension 0.3.0 installed, **PACER Looper switched off in Bitwig's controller
  settings** afterwards; Pacer D1 = the old looper preset, D2 = the old FX preset (now retired: it still announces
  itself, and selecting it switches to the FX mode), D3 = its factory contents (restored). Backups in `backups/`; the
  live tests ran against D3's RAM copy and a preset select clears them.
- Bitwig's audio engine hung once during a project switch (nothing to do with the extension, see its log).
