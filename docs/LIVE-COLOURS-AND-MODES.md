# Live colours and modes

**Status: built, not yet tested on hardware.** The live writer, the mode table and the SW 6 mode switch are in the
extension (`pacer/live/`, `pacer/mode/`); what is left is the hardware checklist, retiring the multi-colour variant
and cutting the preset generator down to one preset. The first section is hardware fact, verified on a real Pacer on
2026-09-15/16 with the user watching.

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
  so a whole-board change is 10-21 messages. Speed is not a problem: 1000 messages in 304 ms (~3300/s).
- **Every SET flashes `LOAD SYS` on the display.** A burst of 21 back-to-back messages reads as one brief flash;
  writes dribbled out a few per second hold `LOAD SYS` there continuously. Colour changes must be coalesced into
  bursts, never sent per tick.
- **A switch press replaces the display with its CC readout**, so a name is not a standing label - the host has to
  re-write it when the CC arrives.
- **Nektar's live colour message** (target `0x06`, used by DAW mode) is ignored outside the Pacer's own DAW presets,
  with and without the "DAW connected" message.
- **CC 119 arrives on every preset selection** (seen in the MIDI monitor log), so preset announcements are reliable.

Experiment scripts, all read-only or RAM-only: `tools/led-colour-lab.mjs` (Nektar's colour message),
`tools/led-colour-chart.mjs` (writes a colour chart to a slot), `tools/led-live-colour.mjs` (one live colour write),
`tools/led-layout-test.mjs` (the LED-number test).

## The plan

### 1. Live colour engine

A writer next to `led/SwitchLedWriter` that sends the colour pair of a switch when the wanted colour changes, while
on/off still goes through the CC echo (`127` = on colour, `0` = off colour). Points to settle:

- Where it sits: `LedState` grows a real colour, `SwitchLedWriter` sends the SysEx when the colour changes and the CC
  when the pattern changes. The light cache in DrivenByMoss only carries an int, so the colour has to be encoded into
  that int (colour * 8 + pattern?) or kept beside it.
- ~~How many SysEx messages per second are safe.~~ Measured: ~3300/s with no drops. The limit is not throughput but
  the `LOAD SYS` display message - sustained writes keep it on screen, so the engine must only write on real state
  changes and send them as one burst.
- What the extension leaves behind on exit: the Pacer keeps the last live colours until a preset is selected. Either
  live with it, or write the preset's own colours back in `exit ()`.

### 2. Colours for state

Proposed (all configurable later):

| Loop switch | Colour |
|-------------|--------|
| empty | dark green, dimmed |
| recording | red |
| playing | green |
| stopped, has a loop | gold |
| muted | blue |
| waiting (record/play/stop) | the colour it is heading for, blinking |

Other switches: undo/redo white, transport gold, overdub red, metronome white, row navigation lavender. On the FX
preset: FX switches green when on, instruments in the nearest colour to their track colour, snapshots by index.

### 3. Modes instead of presets (built 2026-09-16)

Implemented as `pacer/mode/`: `Mode` holds the three boards, `ModeState` is the SW 6 gesture, `SwitchRole` decides
where a press goes, `ModePainter` turns a mode into colour and name writes, and `live/LiveBoard` sends them while
dropping anything that would not change. All of it is unit-tested.

**One preset, and the extension paints everything on top of it.** The stored preset is only a fallback and a
bootstrap: ten switches as CC Trigger on CCs 102-111 channel 16, LED MIDI ctrl on, and the preset-loaded CC 119 so
the extension knows when the Pacer arrives on it. Everything else is live.

**Switch CCs never change at runtime.** A mode is an extension-side lookup table, not a rewrite of the Pacer - the
extension already decides what an incoming CC does. So a mode change writes only *colours and the name*: 10 colour
messages + 1 name message, sent as one burst = one brief `LOAD SYS`. Live step rewriting stays a tool for building
the stored preset, not a runtime mechanism.

#### SW 6 is the mode switch

Chosen for its position: it sits under the display and encoder, physically apart from the 1-5 cluster, so it reads
as a system switch rather than a performance slot.

- **tap** - toggle between the last two modes
- **hold** - the mode menu, and the nine other switches become its slots
- **release without a tap** - stay where you were

| Held on SW 6 | Slot | Why |
|--------------|------|-----|
| 1-5 | Looper, FX, Mixer, Song, Custom | direct access, no cycling |
| A / B | loop tracks left / right | needed from every mode, so pay for it once |
| C / D | row down / up | the panel already prints `▼` and `▲` on those two switches |

#### What a mode holds

- a 5-character name for the display
- per performance switch (1-5, A-D): tap / double-tap / hold action, colour, and **LED position**
- both expression pedal targets - the biggest win with no spare footswitches, since two pedals that re-point per
  mode multiply the surface without new hardware
- the four footswitch jacks (unassigned by default; the author has none yet)

That is 9 switches x 3 gestures = 27 bindings per mode, on top of the existing 53 actions, which all already exist
and are assignable. The mode system is plumbing, not new behaviour.

#### Fixed modes first

Built in, not user-editable, until the ergonomics are proven. Designing a settings system for modes nobody has
specified means guessing twice, and 5 modes x 27 bindings would be 135 entries in Bitwig's panel. Settings come
later, where they turn out to matter. Starting set: **LOOP**, **FX**, **MIX**, and two free slots.

Open questions:

- ~~What are modes 4 and 5?~~ Song, and a custom board laid out in the settings.
- Should a mode change the loop track window or the focused instrument too? Default no - it makes modes stateful
  and harder to reason about. Add it if it is missed.
- Is the name worth re-writing on every press to keep it on the display, or should the display only show the mode
  at the moment it changes? Re-writing costs one message per press and flashes `LOAD SYS` each time.

### 4. Retire multi-colour

Remove `LedMode.MULTI_COLOUR`, the colour-slot CCs from the contract, the multi-colour preset variants from the
generator and Pacer Studio, and the multi-colour rows from the docs. Keep one preset (the current two-colour looper
layout, whose switches already have LED MIDI control on) and let the extension paint it.

## Where things stand (2026-09-16)

- **The whole mechanism is proven on hardware.** Colours, names, switch assignments, LED host control, jacks and
  pedals are all live-writable with no EEPROM cost; see the rules for a live writer in docs/PACER-MAP.md. Nothing
  is built yet.
- Extension 0.3.0 installed; **PACER Looper is switched off in Bitwig's controller settings** after the LED tests.
- Pacer: D1 = looper preset, D2 = FX preset, D3 = its factory contents (restored). Backups in `backups/`; the live
  tests ran against D3's RAM copy and a preset select clears them.
- Hardware checklists in docs/LOOPER.md and docs/FX-PRESET.md are still unrun apart from the pedals, the preset
  announcement and the LED findings above.
- Bitwig's audio engine hung once during a project switch (nothing to do with the extension, see its log).
