# Live looping in Bitwig with the Nektar Pacer

Bitwig Studio has no dedicated looper device — **the clip launcher is the looper**. Every loop is a clip in a launcher
slot. Recording starts and stops on the launch quantization, so loops are always in time with each other and with
the project tempo. The **PACER Looper** extension turns the Pacer's switches into looper-pedal controls for that
workflow, with LED feedback that follows the beat.

## 1. Prepare a Bitwig project

1. Create 4 tracks at the top of the project (6 with the "SW 1-6 loops" layout), e.g. *Loop 1 … Loop 4*.
   - **Audio loops:** audio tracks, input = your interface input, monitoring = *Auto*. The looper arms a track when
     it records and disarms it again once that loop is closed (*exclusive arm*), so your input is only monitored
     once, through the track you are recording on.
   - **MIDI loops:** instrument tracks work the same way and additionally support launcher overdub (layering notes
     into a playing clip).
2. Set the tempo (or tap it with **SW D** — its LED then flashes the beat).
3. In the extension settings set **Launch quantization = 1 bar**. This is what makes loops land on the grid.
4. Keep the audio buffer low (64–256 samples); Bitwig compensates recording latency automatically.

## 2. Install the extension

```bash
cd bitwig
mvn -q install
```

This copies `Pacer4Bitwig.bwextension` into `Documents/Bitwig Studio/Extensions`. (Every CI build on GitHub also offers
the `.bwextension` as a download.) In Bitwig: **Settings > Controllers > Add controller > Nektar > PACER Looper** with

| | Input | Output |
|---|---|---|
| Port 1 | `PACER` | `PACER` |
| Port 2 | `MIDIIN2 (PACER)` | `MIDIOUT2 (PACER)` |

Port 2 is only used by the optional *Nektar DAW mode* (section 5). If you added PACER Looper before it had two ports,
open its entry in *Settings > Controllers* and pick the port-2 devices (or remove and re-add the controller).

Nektar's own *PACER* script uses the same ports, so disable it while PACER Looper is active — PACER Looper can take
over its job (see *Nektar DAW mode*).

## 3. Load the looper preset onto the Pacer

```bash
cd tools
node looper-preset.mjs
node pacer-send.mjs ../presets/bitwig-looper-two-colour-D1.syx --confirm D1
```

…or use the **Bitwig Looper** template in Pacer Studio (`editor/`). `pacer-send.mjs` backs the slot up to `backups/`
before it writes. Start with the **two-colour** variant; switch to multi-colour only after the LED test passes (see
section 6).

On the Pacer, select preset D1. The preset sends CC 119 when loaded, which makes the extension repaint every LED.

## 4. Your other presets

All other presets keep doing whatever they are programmed to do. The looper only reacts to **MIDI channel 16** on
port 1; everything the Pacer sends on channels 1–15 goes to Bitwig through the note input **"PACER"** — pick it (or
*All ins*) as a track's MIDI input to play instruments, or use it for MIDI mapping. Avoid channel 16 in other presets
(CCs 20–69 and 102–119 on channel 16 are the looper's).

The Pacer's built-in **Track** and **Transport** DAW presets work when *Nektar DAW mode* is on (section 5).

## 5. What the switches do

Defaults — everything except the loop switches can be reassigned in the settings (see *Actions* below).

| Control | Tap | Hold |
|---------|-----|------|
| **SW 1–4** | Smart loop on track 1–4 (see below) | Delete that loop |
| **SW 5** | Undo | Redo |
| **SW 6** | Stop all loops, or play the whole row if nothing plays | Clear every loop in the row |
| **SW A** | Previous scene row | Move the loop track window left |
| **SW B** | Next scene row (adds a scene after the last one) | Move the loop track window right |
| **SW C** | Launcher overdub on/off | Metronome on/off |
| **SW D** | Tap tempo | Transport play/stop |
| **FS 1** (jack) | **One-button looper**: record the next layer | Clear the last recorded loop |
| **FS 2** (jack) | Stop all loops / play the row | Clear every loop in the row |
| **FS 3, FS 4** | – | – |
| **EXP 1** | Volume of the selected track | – |
| **EXP 2** | Master volume | – |

### Smart loop

One switch per loop, like a looper pedal:

| The slot is… | A tap… |
|--------------|--------|
| empty | arms the track and records (starts at the next quantization point, or after the count-in) |
| recording | closes the loop: stops recording and plays it |
| playing | stops it (setting: or mutes/unmutes it, toggles overdub, or does nothing) |
| stopped | plays it (and unmutes the track if it was muted) |
| waiting to record/play, counting in | cancels |
| waiting to stop | keeps it playing |

Loop switches fire **on press**, so timing is tight. Holding a loop switch therefore also runs the tap first; with the
default hold action (delete) that is harmless — whatever the tap started is deleted. Switches without a hold action
always fire on press.

**Hold to record** (*Loop switch mode = Hold to record, release to close*): on an empty slot, press and hold to
record, release to close the loop — like a punch-in. If you let go before the recording has started (it waits for the
quantization point), the loop closes one quantization step after it starts. Holding never deletes a recording this
way; on slots that already hold a loop the switch works as usual (tap and hold).

### Building a performance

- **One-button looper** — the classic single-pedal looper, spread over the loop tracks: each tap closes the loop that
  is recording, or starts recording on the first empty loop track of the row. Hold clears the most recent loop.
- **Mute instead of stop** (*Tap on a playing loop = Mute/unmute*): the clip keeps running silently, so it comes back
  exactly in time. **Mute/unmute all loops** drops everything out for a break; **Solo/unsolo selected loop** isolates
  one layer.
- **Mute timing** (*Immediately / On the next beat / On the next bar*): mutes from the looper wait for the next beat or
  downbeat, so a drop-out lands on the one even if your foot is early. A waiting mute blinks fast; tapping again
  before it lands cancels it.
- **Count-in** (*Count-in from a stopped transport = 1 or 2 bars*): recording from a stopped transport starts the
  transport with the metronome on, counts in, and starts recording on the next bar. The metronome switches off again
  as recording starts if it was off before. Tap the same switch during the count-in to cancel.
- **Beat counter:** while counting in or waiting to record (amber) and while recording (red), the top row SW A–D
  shows the beat of the bar — A = 1, B = 2, C = 3, D = 4 (setting *Count beats on SW A-D*).
- **Loop length = Match the first loop of the row:** the first loop of a row is recorded free; its length (in bars)
  becomes the fixed length of every later loop in that row, which then close by themselves. When the row is empty
  again the next first loop is free again.
- **Double / halve selected loop:** *double* duplicates the clip's content (a 2-bar loop becomes 4 bars of the same
  material, ready for variations); *halve* shortens the loop region to its first half.
- **Scene rows as song sections:** each row (scene) is a set of loops. Record a verse in row 1, tap **SW B** to move
  to row 2 and record the chorus, then launch rows with **SW 6** (or Bitwig's scene launchers). **Duplicate row**
  copies the current row with all its loops into a new row right below and moves there — play the copy and replace
  single loops to build a variation.
- **Fade out and stop all loops:** ramps every loop track down over the *Fade length* (following the tempo), stops
  the loops and then restores their volumes, so the next launch plays at the normal level. **Fade in the row**
  launches the row from silence and ramps up. Tap either again during a fade to cancel it and restore the volumes.
  (If arranger automation writing is armed, Bitwig records the volume ramps as automation.)
- **Input monitoring on/off (selected track):** play through a loop track's effects without recording.
- **Reset the looper:** the panic button — stops all loops, cancels count-ins, fades and waiting mutes, unmutes,
  unsolos and disarms the loop tracks and switches launcher overdub off.
- **Hold time for clearing actions** (*Long* / *Very long*): deleting holds — a loop switch's delete, *Clear the last
  recorded loop*, *Clear selected loop*, *Clear all loops in the row* — only run if the switch stays down for 1.5 or
  2.5 seconds. Other holds keep the normal half second.

### Actions

Available for SW 5–6 (4-loop layout), SW A–D and FS 1–4, as tap and as hold:

| Group | Actions |
|-------|---------|
| Loops | one-button looper · clear the last recorded loop · smart loop / stop / mute / solo / clear the selected track · input monitoring on/off · double / halve the selected loop · select previous / next loop track |
| Row | play row / stop all · stop all · play row · clear row · mute/unmute all loops · fade out and stop · fade in the row · reset the looper |
| Navigation | previous / next row · duplicate row · move loop tracks left / right |
| Transport & misc | undo · redo · launcher overdub · metronome · tap tempo · transport play/stop · LED test |

### Expression pedals

| Kind | Targets |
|------|---------|
| Bitwig parameters | selected track volume, pan, send 1, send 2 · master volume · selected device remote controls 1–2 · project remote controls 1–2 |
| MIDI into the "PACER" note input | mod wheel (CC 1) · breath (CC 2) · channel volume (CC 7) · expression (CC 11) · brightness / MPE timbre (CC 74) · channel pressure · pitch bend up (heel = centre, toe = full up) |

MIDI targets reach every instrument on a track whose input is "PACER" or *All ins*, on the channel set in
*MIDI channel for pedal messages* (default 1).

**Response** per pedal: *Linear*, *Inverted* (toe = minimum), *Slow start* (fine control near the heel — good for
volume swells) or *Fast start* (fine control near the toe).

### Nektar DAW mode (Track and Transport presets)

Switch on *Settings > Nektar DAW mode (USB port 2) > Serve the Track and Transport presets* and assign port 2 (section
2). PACER Looper then does what Nektar's script did for the Pacer's built-in DAW presets:

| Pacer DAW function | In Bitwig | LED |
|--------------------|-----------|-----|
| Play · Stop · Record | transport play/stop · stop · arranger record | playing · stopped · recording |
| Loop · Metronome · Pre-roll · Overdub | arranger loop · metronome · pre-roll off/1 bar · arranger overdub | on/off |
| Rewind · Fast forward | move the play position (repeats while held) | while held |
| Go to loop start | stop, jump to the loop start | – |
| Mute · Solo · Arm | selected track | state |
| Undo | undo | – |
| Track − / + | select previous / next track (repeats while held) | while held |
| Patch − / + | select previous / next device (Nektar's script browsed presets instead) | while held |
| Track / master volume (absolute or relative) | selected track / master volume | – |

Moving the loop region left/right is not supported yet. The Pacer's DAW mode is independent of the looper preset: use
the Pacer's Track/Transport buttons to switch between them.

## 6. LEDs

While the transport runs, blinking follows the beat (setting *Blink in time with the transport*); when it is stopped,
LEDs blink on the wall clock.

**Two-colour mode** (default):

| Loop | LED |
|------|-----|
| empty | off |
| has a loop, stopped — or muted | short flash on every downbeat (once a second when stopped) |
| playing | on, with a short gap on every downbeat |
| waiting (record/play/stop), counting in, mute waiting for its beat | fast blink (eighth notes) |
| recording, or overdubbing | blinks on every beat |

**Multi-colour mode** (experimental): empty = off, stopped = amber, playing = green (gap on the downbeat),
muted = blue, recording/overdubbing = red, waiting = blinking in the target colour. Requires the multi-colour
preset variant.

Assignable switches light up when their tap action has something to do: undo/redo possible, loops playing (play/stop
all: green playing, amber loaded), previous/next row available (next row shows blue when it would add a scene),
overdub on (red), metronome on, transport running (green), any loop muted (blue), selected loop soloed (amber),
input monitoring on (green), reset available (purple). The one-button looper shows the loop it is busy with; fade
actions blink while a fade runs. **Tap tempo** flashes every beat — white on the downbeat, green on the others in
multi-colour mode. The beat counter temporarily takes over SW A–D (section 5).

**LED test:** *Settings > Pacer LEDs > Test the LEDs* (or the *Test the LEDs* action) lights every switch white,
red, green, amber, blue, purple for a second each. With the two-colour preset every step just looks "on"; with the
multi-colour preset you should see six different colours — that confirms multi-colour mode works on your Pacer.

## 7. Settings (Bitwig: Settings > Controllers > PACER Looper)

| Category | Setting | Options |
|----------|---------|---------|
| Looper | Switch layout | SW 1-4 loops, SW 5-6 assignable · SW 1-6 loops |
| | Loop switch mode | Tap to record, tap again to close · Hold to record, release to close |
| | Loop switch fires on press | On (tight timing) · Off (on release; hold no longer runs the tap) |
| | Tap on a playing loop | Stop · Mute/unmute · Toggle launcher overdub (note clips) · Nothing |
| | Hold a loop switch | Delete the clip · Stop · Nothing |
| | Hold time for clearing actions | Normal (0.5 s) · Long (1.5 s) · Very long (2.5 s) |
| | Arm the track when recording | On · Off |
| | Exclusive arm (disarm finished loops) | On · Off |
| | Select the track on press | On · Off (EXP targets follow the selected track) |
| | Count-in from a stopped transport | Off · 1 bar · 2 bars |
| | Mute timing | Immediately · On the next beat · On the next bar |
| | Fade length | 1 · 2 · 4 · 8 bars |
| Switches | SW 5, SW 6, SW A–D tap / hold | any action |
| Jacks | FS 1–4 tap / hold | any action |
| | EXP 1, EXP 2 | any pedal target |
| | EXP 1, EXP 2 response | Linear · Inverted · Slow start · Fast start |
| | MIDI channel for pedal messages | 1–16 |
| Pacer LEDs | LED mode | Two-colour (safe) · Multi-colour (experimental) |
| | Blink in time with the transport | On · Off |
| | Count beats on SW A-D (count-in and recording) | On · Off |
| | Test the LEDs | button |
| Clip launcher | Launch quantization | Keep project setting · None · 1/16 … 8 bars |
| | Loop length | Keep project setting · Free (press again to close) · Match the first loop of the row · 1 / 2 / 4 / 8 bars (assumes 4/4) |
| Nektar DAW mode | Serve the Track and Transport presets | Off · On |
| Feedback | Show pop-up notifications | On · Off |

## 8. Hardware test checklist

Run these once with the Pacer on preset D1 and the controller added in Bitwig.

1. [ ] Every switch does something in Bitwig (SW 1–6, A–D).
2. [ ] SW 1 on an empty slot: LED blinks fast, then blinks on the beat while recording; a second tap → on (with a gap
       on each downbeat) while playing.
3. [ ] Record loop 1, then loop 2: after loop 1 is closed its track disarms by itself; only loop 2 stays armed.
4. [ ] Hold SW 1 (~0.5 s): the clip disappears and the LED goes dark.
5. [ ] SW 6 stops everything; tapping again replays the row.
6. [ ] SW A / SW B move the highlighted scene row in Bitwig; SW B past the last row adds a scene.
7. [ ] SW D flashes on every beat while the transport runs; while recording, SW A–D count the beats.
8. [ ] EXP 1 moves the selected track's volume; set EXP 2 to *mod wheel* and it moves the mod wheel of an instrument
       on a track listening to "PACER"; *Inverted* response flips it.
9. [ ] Switch to another Pacer preset and back to D1: all LEDs repaint.
10. [ ] Another preset sending notes on channel 1 plays an instrument track whose input is "PACER".
11. [ ] FS 1 (one-button looper): tap, tap, tap, tap records loop 1 then loop 2; hold clears loop 2.
12. [ ] Count-in 1 bar from a stopped transport: recording starts on bar 2.
13. [ ] Loop length *Match the first loop*: a 2-bar first loop makes the next loop close by itself after 2 bars.
14. [ ] *Fade out and stop*: loops fade over the fade length, stop, and play at full volume on the next launch.
15. [ ] *Duplicate row*: a new row with the same loops appears below and becomes the current row.
16. [ ] *Double selected loop* on a 1-bar loop makes it 2 bars long.
17. [ ] *Hold time for clearing actions = Long*: holding SW 1 for one second does not delete; two seconds does.
18. [ ] *Loop switch mode = Hold to record*: hold SW 2 for two bars and release — a 2-bar loop plays.
19. [ ] *Mute timing = On the next bar*: tapping mute mid-bar blinks, then mutes exactly on the downbeat.
20. [ ] *Reset the looper* stops, unmutes and disarms everything.
21. [ ] *Nektar DAW mode* on, port 2 assigned: the Pacer's Transport preset starts/stops Bitwig and its LEDs follow.
22. [ ] *Test the LEDs* with the multi-colour preset shows six colours.
23. [ ] Pressing a switch in multi-colour mode does not leave a wrong colour behind (the extension repaints 30 ms
        after each press).

If 22 or 23 fail, stay in two-colour mode and note what you saw in the LED Lab checklist.
