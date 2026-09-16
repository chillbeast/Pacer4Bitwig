# The FX mode

A pedalboard for the instruments you play live through Bitwig (vocals, guitar, bass, …) while the looper keeps
running. What a switch does for an instrument is defined in Bitwig and saved with that instrument's track.

Status: **not yet tested on hardware.** Run the checklist at the end and report what does not work.

## How it works

- **One tap to change instrument.** The four instrument slots sit on the bottom row.
- **It is a mode, not a preset.** Hold SW 6 and tap SW 2 to get here; tap SW 6 to go back where you were. The Pacer
  stays on the same preset the whole time and the loops keep playing — the extension simply repaints the board and
  re-reads what each switch means (docs/LIVE-COLOURS-AND-MODES.md).
- **The meaning lives in Bitwig.** Switches control the focused instrument's *track remote controls* (a page named
  "Pacer") or, without such a page, simply the devices of its chain in order. Changing an FX rack never needs a
  preset write.
- **The FX mode never changes Bitwig's selection.** It follows instruments with its own cursor track, so a pinned
  Push, the mouse and the looper's "selected track" actions are unaffected, and they never move the FX focus.

## Install

Nothing to install beyond the one Pacer preset and the extension (docs/LOOPER.md sections 2–3). Hold SW 6 and tap
SW 2; the display reads `FX`.

## Bitwig setup

1. One track per live instrument (e.g. *Vocal*, *Guitar*, *Bass*) with the interface input, monitoring *On* and its
   effects: the "dedicated input track" setup of LOOPER.md section 8. Loop tracks record from these tracks.
2. Optional, per instrument: add a track remote controls page named **Pacer** and map
   - slots 1–4 to what SW A–D switch (an effect's mix, a chain's volume, …). The mapping's range is what the switch
     toggles between, so "delay mix 0–35 %" is set up in Bitwig, not on the Pacer. Slots 5 and 6 are reachable by
     putting *FX 5* / *FX 6* on a footswitch jack.
   - slots 7–8 to what EXP 1 / EXP 2 control (wah frequency, delay feedback, reverb size, …).

   Save the track as a preset (*Guitar rig*, *Bass rig*, *Vocal chain*) to reuse it in other projects.
3. In the FX mode, select an instrument track in Bitwig and hold SW 1, 2, 3 or 4 to assign it to that switch.

## Layout (defaults)

| Control | Tap | Double-tap | Hold |
|---------|-----|------------|------|
| SW 1–4  | Focus instrument A–D | Mute/unmute that instrument | Assign the track selected in Bitwig |
| SW 5    | Next snapshot of the focused instrument | Back to snapshot 1 | Store the current sound in this snapshot |
| SW 6    | **The mode switch** — previous mode | – | Mode menu |
| SW A–D  | FX 1–4 on/off | – | FX 1–4 while held, back on release |
| FS 1    | One-button looper | – | Clear the last loop |
| FS 2    | Play/stop all loops | – | Clear the row |
| FS 3    | Focus the next instrument | – | – |
| FS 4    | Next snapshot | – | – |
| EXP 1   | Focused instrument: remote control 7 | | |
| EXP 2   | Focused instrument: remote control 8 | | |

- The modes are fixed for now, so this layout is not a setting. The FX, instrument and snapshot actions *can* be put
  on the footswitch jacks, which keep their own settings in every mode — so FS 1–2 keep looping from here.
- Four instruments fit because SW 6 is the mode switch and snapshots moved to SW 5; the old layout had three
  instruments and six FX switches.
- *Momentary: tap again on release* works as the hold of any switch whose tap toggles something.

## Behaviour

### Instruments

- Four slots (A–D). SW D is the snapshot switch by default; set *FX SW D tap* to *Focus instrument D* for a fourth
  instrument.
- A slot remembers the track **name**, saved per project, and so does the focused slot. Assignments are made from
  the Pacer: Bitwig 6 shows a controller's project settings nowhere in its own panels. After renaming a track,
  assign it again. Instruments are found among the first 64 tracks of the project (tracks inside groups count).
- Assigning uses *Hold time for clearing actions*, so it cannot happen by accident mid-song.
- Focusing shows a notification ("FX: Guitar"). Setting *Focusing an instrument selects its track in Bitwig*, default
  off.
- Double-tap mutes the instrument's track, for unplugging and swapping guitars without pops. Its LED blinks while
  muted.

### FX switches

- **The focused track has a remote controls page named "Pacer"** (any case, setting *Remote controls page name*):
  SW N toggles remote control N between the ends of its mapping; it counts as on from the middle of the range. An
  unmapped slot does nothing.
- **No "Pacer" page:** SW N switches the Nth device of the track's chain on/off. Only top-level devices count (a
  container is one device); devices after the sixth are out of reach.
- **Tap latches, hold is momentary, on the same switch:** the effect flips on press (no delay); if the switch was held
  longer than the hold time, it flips back on release. Good for delay and reverb throws on vocals.

### Snapshots

- Per instrument and saved per project. *Snapshots per instrument* = 2 (an A/B pair) by default, up to 4.
- A snapshot stores the state of SW 1–6: device on/off, or the exact remote control value (set 35 % with a knob, then
  store). Remote controls 7–8 are not stored because the pedals own them.
- Tap moves to the next snapshot and recalls it if it has been stored; an empty snapshot changes nothing until you
  store into it.
- The snapshot LED blinks when the sound has changed since the snapshot was recalled or stored.

### Pedals

- EXP 1/2 have their own target settings in the FX mode (*EXP 1 in the FX mode*; defaults: remote controls 7/8 of
  the focused instrument), and they are re-bound whenever the mode changes. Response curve and heel/toe range are
  shared with the other modes.
- They only act on a "Pacer" page. After a focus change, the first pedal move sets the new instrument's control to the
  pedal position.

### LEDs

| Switch state | LED |
|--------------|-----|
| FX on | green |
| FX off, or nothing to control | the mode colour, dimmed |
| Instrument focused | nearest Pacer colour to the track colour |
| Instrument assigned, not focused | a blip in the track colour (on the downbeat while playing) |
| Instrument muted | red, blinking |
| Instrument slot empty, or its track is missing | the mode colour, dimmed |
| Snapshot 1 / 2 / 3 / 4 | white / red / green / amber |
| Snapshot changed since recall or store | blinking |

## Hardware test checklist

Run with the one Pacer preset on D1 and at least two instrument tracks.

1. [ ] Hold SW 6 and tap SW 2: the display reads `FX` and the board repaints. Tap SW 6: back to the looper, which
       works as before and never stopped playing.
2. [ ] Select a track in Bitwig, hold SW 1: "Instrument A is now …", SW 1 lights.
3. [ ] Assign a second instrument to SW 2. Tapping SW 1 / SW 2 switches the focus ("FX: …") without changing Bitwig's
       selection or the Push's track.
4. [ ] Track without a "Pacer" page: SW A switches its first device on/off; the LED follows, also when you click the
       device's power button in Bitwig.
5. [ ] Add a "Pacer" page and map slot 1 to a delay mix with range 0–35 %: SW A now toggles between 0 and 35 %, and no
       device is switched any more.
6. [ ] Hold SW B for a second, then release: the effect is on only while held.
7. [ ] Double-tap SW 2: its track mutes and the LED blinks; again unmutes.
8. [ ] Hold SW 5: "snapshot 1 stored". Change an effect: SW 5 blinks. Tap SW 5: "snapshot 2 is empty" - hold to store
       it. Tapping SW 5 now switches between both sounds; double-tap returns to snapshot 1.
9. [ ] Save, close and reopen the project: instruments, focus and snapshots are still there.
10. [ ] EXP 1 moves remote control 7 of the focused instrument's "Pacer" page.
11. [ ] FS 1 records loops while in the FX mode; FS 3 cycles through the instruments.
12. [ ] The extension selecting the "Pacer" page does not change the page shown in Bitwig or on the Push.

## Later

See docs/ROADMAP.md: loop tracks recording the focused instrument, named snapshots. A combined board is no longer a
preset question - it is just another mode.
