# Live colours and modes (plan)

**Status: planned, nothing built yet.** The first section is hardware fact, verified on a real Pacer on 2026-09-15/16
with the user watching. Everything after it is design to argue about.

## What the hardware actually does

- **One light bar per stomp switch.** Its colour is the **On colour / Off colour pair of step 1**. The colour slots on
  steps 2-6 do nothing, and LED numbers 1-3 address the Pacer's labelled indicators (Preset, Solo, transport), not the
  switch bar. The multi-colour preset variant is therefore dead - see docs/PACER-MAP.md.
- **Twelve colours**, each with a full (A) and dimmed (b) variant in consecutive bytes: magenta `0x01`, red `0x03`,
  orange `0x05`, gold `0x07`, yellow `0x09`, green `0x0B`, dark green `0x0D`, cyan `0x0F`, blue `0x11`, lavender
  `0x13`, purple `0x15`, white `0x17`.
- **Writing to preset index 0 edits the loaded preset live, in RAM.** Selecting any preset restores the stored one, so
  it costs no EEPROM wear.
  - colour: `F0 00 01 77 7F 01 01 00 <switch obj> 41 01 <on> 00 42 01 <off> <cs> F7`
  - function: `F0 00 01 77 7F 01 01 00 <switch obj> 03 01 <cc> <cs> F7` (element 3 = step 1 data 1)
  - Both confirmed: SW 1 cycled through all twelve colours, SW 5 sent CC 60 instead of CC 106.
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
- How many SysEx messages per second are safe. Colours change on state changes, not per flush, so this should be a
  handful per second at worst - but it wants measuring.
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

### 3. Modes instead of presets

With live writes, a "mode" is a set of switch assignments plus colours that the extension applies; the Pacer stays on
one preset and its stored memory is never touched. Sketch:

- A mode = the tap / double-tap / hold actions of the ten switches, plus a colour per switch, plus a name.
- Switching modes: a dedicated switch (cycle), a hold, a footswitch jack, or the Pacer's preset selection as today.
- The Pacer's display could show the mode: writing the preset **name** live is the same mechanism (object `0x01`,
  element `0x01`, length + characters) - **untested**, try it first.
- Built-in modes to start with: Looper, FX, and a combined one. User-defined modes later (they are just settings).

Open questions to brainstorm:

- Which modes are worth having, and what does each switch do in them?
- How should switching feel - momentary (hold to peek at another mode) or latched?
- Should a mode change the loop track window or the FX instrument too?
- Per-mode colours: fixed by us, or pickable per switch in the settings?
- Does the Pacer's display update when the name is written live?

### 4. Retire multi-colour

Remove `LedMode.MULTI_COLOUR`, the colour-slot CCs from the contract, the multi-colour preset variants from the
generator and Pacer Studio, and the multi-colour rows from the docs. Keep one preset (the current two-colour looper
layout, whose switches already have LED MIDI control on) and let the extension paint it.

## Where things stand (2026-09-16)

- Extension 0.3.0 installed; **PACER Looper is switched off in Bitwig's controller settings** after the LED tests.
- Pacer: D1 = looper preset, D2 = FX preset, D3 = its factory contents (restored). Backups in `backups/`.
- Hardware checklists in docs/LOOPER.md and docs/FX-PRESET.md are still unrun apart from the pedals, the preset
  announcement and the LED findings above.
- Bitwig's audio engine hung once during a project switch (nothing to do with the extension, see its log).
