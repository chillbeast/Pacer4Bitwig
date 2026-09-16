# PACER FX preset

A second Pacer preset that turns the Pacer into a pedalboard for the instruments you play live through Bitwig
(vocals, guitar, bass, …) while the looper keeps running. The Pacer layout never changes: what a switch does for an
instrument is defined in Bitwig and saved with that instrument's track.

Status: **new in PACER Looper 0.3.0, not yet tested on hardware.** Run the checklist at the end and report what does
not work.

## How it works

- **One FX preset with instrument focus.** Changing instrument is one tap on the top row, while a Pacer preset change
  takes several presses.
- **Same CCs as the looper preset.** Only the preset-loaded value (CC 119) differs, so the extension knows which
  preset is active (setting *Active preset*). Switching presets never touches the loops: they keep playing.
- **The meaning lives in Bitwig.** Switches control the focused instrument's *track remote controls* (a page named
  "Pacer") or, without such a page, simply the devices of its chain in order. Changing an FX rack never needs a
  preset write.
- **The FX preset never changes Bitwig's selection.** It follows instruments with its own cursor track, so a pinned
  Push, the mouse and the looper's "selected track" actions are unaffected, and they never move the FX focus.

## Install

1. Put the FX preset on the Pacer, slot D2 by default: Pacer Studio's **Bitwig FX** template, or
   `cd tools && node looper-preset.mjs && node pacer-send.mjs ../presets/bitwig-fx-two-colour-D2.syx --confirm D2`
   (backs D2 up first). Use the multi-colour file together with the multi-colour looper preset.
2. PACER Looper 0.3.0 or later in Bitwig. Select D2 on the Pacer: Bitwig shows "FX preset: …".

## Bitwig setup

1. One track per live instrument (e.g. *Vocal*, *Guitar*, *Bass*) with the interface input, monitoring *On* and its
   effects: the "dedicated input track" setup of LOOPER.md section 8. Loop tracks record from these tracks.
2. Optional, per instrument: add a track remote controls page named **Pacer** and map
   - slots 1–6 to what SW 1–6 switch (an effect's mix, a chain's volume, …). The mapping's range is what the switch
     toggles between, so "delay mix 0–35 %" is set up in Bitwig, not on the Pacer.
   - slots 7–8 to what EXP 1 / EXP 2 control (wah frequency, delay feedback, reverb size, …).

   Save the track as a preset (*Guitar rig*, *Bass rig*, *Vocal chain*) to reuse it in other projects.
3. With the FX preset selected, select an instrument track in Bitwig and hold SW A, B or C to assign it to that
   switch.

## Layout (defaults)

| Control | Tap | Double-tap | Hold |
|---------|-----|------------|------|
| SW 1–6  | FX 1–6 on/off | – | FX 1–6 while held, back on release |
| SW A–C  | Focus instrument A–C | Mute/unmute that instrument | Assign the track selected in Bitwig |
| SW D    | Next snapshot of the focused instrument | Back to snapshot 1 | Store the current sound in this snapshot |
| FS 1    | One-button looper | – | Clear the last loop |
| FS 2    | Play/stop all loops | – | Clear the row |
| FS 3    | Focus the next instrument | – | – |
| FS 4    | Next snapshot | – | – |
| EXP 1   | Focused instrument: remote control 7 | | |
| EXP 2   | Focused instrument: remote control 8 | | |

- Every switch of the FX preset has its own tap / double-tap / hold settings (*FX preset: SW 1-6* and *SW A-D*), and
  the FX, instrument and snapshot actions can also be assigned on the looper preset and the jacks.
- The footswitch jacks and their settings are shared by both presets, so FS 1–2 keep looping from the FX preset.
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

- EXP 1/2 have their own target settings on the FX preset (*EXP 1 on the FX preset*; defaults: remote controls 7/8 of
  the focused instrument). Response curve and heel/toe range are shared with the looper preset.
- They only act on a "Pacer" page. After a focus change, the first pedal move sets the new instrument's control to the
  pedal position.

### LEDs

| Switch state | Two-colour | Multi-colour |
|--------------|------------|--------------|
| FX on | on | green |
| FX off, or nothing to control | off | off |
| Instrument focused | on | nearest Pacer colour to the track colour |
| Instrument assigned, not focused | short blip every second (on the downbeat while playing) | blip in the track colour |
| Instrument muted | blinking | red, blinking |
| Instrument slot empty, or its track is missing | off | off |
| Snapshot 1 / 2 / 3 / 4 | off / on / on / on | white / red / green / amber |
| Snapshot changed since recall or store | blinking | blinking |

## Hardware test checklist

Run with PACER Looper 0.3.0, the FX preset on D2 and at least two instrument tracks.

1. [ ] Select D2: Bitwig shows "FX preset: …" and *Active preset* switches to *FX preset*. D1 shows "Looper preset"
       and the looper works as before.
2. [ ] Select a track in Bitwig, hold SW A: "Instrument A is now …", SW A lights.
3. [ ] Assign a second instrument to SW B. Tapping SW A / SW B switches the focus ("FX: …") without changing Bitwig's
       selection or the Push's track.
4. [ ] Track without a "Pacer" page: SW 1 switches its first device on/off; the LED follows, also when you click the
       device's power button in Bitwig.
5. [ ] Add a "Pacer" page and map slot 1 to a delay mix with range 0–35 %: SW 1 now toggles between 0 and 35 %, and no
       device is switched any more.
6. [ ] Hold SW 2 for a second, then release: the effect is on only while held.
7. [ ] Double-tap SW B: its track mutes and the LED blinks; again unmutes.
8. [ ] Hold SW D: "snapshot 1 stored". Change an effect: SW D blinks. Tap SW D: "snapshot 2 is empty" - hold to store
       it. Tapping SW D now switches between both sounds; double-tap returns to snapshot 1.
9. [ ] Save, close and reopen the project: instruments, focus and snapshots are still there.
10. [ ] EXP 1 moves remote control 7 of the focused instrument's "Pacer" page.
11. [ ] FS 1 records loops while on the FX preset; FS 3 cycles through the instruments.
12. [ ] The extension selecting the "Pacer" page does not change the page shown in Bitwig or on the Push.

## Later

See docs/ROADMAP.md: a combined looper + FX preset, loop tracks recording the focused instrument, named snapshots.
