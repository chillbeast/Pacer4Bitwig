# Pacer4Bitwig

Nektar Pacer as a live-looper controller for Bitwig Studio, plus a new Pacer preset editor ("Pacer Studio").

## Docs

- `docs/PACER-MAP.md` — **the contract** between the Pacer presets and the Bitwig extension (CCs, channel, LED
  strategy, preset-loaded values). Change it first, then both sides.
- `docs/LOOPER.md` — user guide: Bitwig project setup, switch functions, settings, hardware test checklist.
- `docs/FX-PRESET.md` — FX preset guide (instrument focus, FX switches, snapshots) with its own hardware checklist.
- `docs/LIVE-COLOURS-AND-MODES.md` — **next wave**: what the Pacer's LEDs really do (verified), live edits through
  preset index 0, and the plan for state colours and modes. Read it before touching LED code.
- `docs/manual.html` — the styled manual built from the Markdown docs; keep it in step with them. Written in Artifact
  format (starts at `<title>`, no doctype/html/head/body): published as the claude.ai Artifact
  https://claude.ai/artifact/Wxrg7UxPZpbCL7VREDpyZk (checklist results in its db, `checklists/results`) and, wrapped by
  `pages.yml`, at https://chillbeast.github.io/Pacer4Bitwig/manual/ (checklists fall back to localStorage there).

## Layout

```
bitwig/     Maven project -> Pacer4Bitwig.bwextension (groupId dev.pacer4bitwig), on the DrivenByMoss framework
editor/     Pacer Studio: Vite + React + TypeScript web app (Web MIDI + SysEx)
tools/      Node scripts talking to the Pacer directly (@julusian/midi)
backups/    .syx dumps of the user's Pacer - keep them, they are the undo for any preset write
reference/  read-only copy of github.com/francoisgeorgy/pacer-editor (gitignored)
```

DrivenByMoss is not on Maven Central: build a local clone into `~/.m2` (see CONTRIBUTING.md). `bitwig/pom.xml` pins
`de.mossgrabers:DrivenByMoss:26.6.5`; CI pins the matching upstream commit in `.github/workflows/ci.yml`.

## Build & run

```bash
cd bitwig && mvn -q install      # tests + copies Pacer4Bitwig.bwextension into Documents/Bitwig Studio/Extensions
cd bitwig && mvn -q package      # tests + jar only, Bitwig keeps running the installed version
cd editor && npm install && npm run dev    # http://localhost:5173 (Chrome/Edge, allow MIDI + SysEx)
cd tools && node pacer-backup.mjs          # read-only full backup into backups/
cd tools && node looper-preset.mjs         # regenerate presets/ (looper D1 + FX D2, both LED variants)
cd tools && node pacer-monitor.mjs         # read-only: print everything the Pacer sends (both ports)
```

Bitwig: Settings > Controllers > Add > Nektar > **PACER Looper**, ports `PACER` / `PACER`. Bitwig hot-reloads the
extension when the file changes (so `install` restarts it under a user who is testing).

## Hardware facts (verified on a real Pacer, Windows 11)

- USB MIDI ports: `PACER` (port 1, presets) and `MIDIIN2 (PACER)` / `MIDIOUT2 (PACER)` (port 2, Nektar DAW mode).
  With Windows MIDI Services the ports are multi-client (Bitwig, the editor and tools can share them).
- Nektar's own `PACER.control.js` claims the `PACER` port too, so it must be disabled while PACER Looper is active.
  What it does on port 2 is documented in docs/ROADMAP.md.
- Full backup = 4762 SysEx messages / 139544 bytes. The Pacer never answers GET for preset D6.
- LED data only comes back when requesting a whole preset (obj 0x7F), not a single control.
- Preset names are always 5 characters, space-padded.

## Rules

- **Never send SysEx SET (cmd 0x01) to the Pacer without the user's explicit OK**, and take a backup first
  (`tools/pacer-backup.mjs`). GET requests and plain CCs (LED tests) are fine.
- Never edit DrivenByMoss sources. Copy a class into `dev.pacer4bitwig.*` if something upstream is private.
- Bitwig code: keep decision logic pure and unit-tested (`looper/`, `led/`, `fx/`, `preset/`), keep
  `PacerControllerSetup` thin. Upstream style: 4-space indent, space before `(`, `final` everywhere,
  `/** {@inheritDoc} */` on overrides.
- Licenses: `bitwig/` builds on DrivenByMoss (LGPLv3, keep attribution). `editor/` is derived from pacer-editor and
  is GPL-3.0-or-later.

## Bitwig extension structure

- `PacerControllerExtensionDefinition` (package `bitwig`) is the only place touching the raw Bitwig API; it hands the
  setup `host::requestFlush`, a `TransportBeatClock` factory, the note input factory and a `BitwigFxTracks` factory
  (all created during init).
- `PacerControllerSetup`: hardware proxies only (10 switches with lights, 4 FS jacks, 2 pedals, preset-loaded CC),
  wired to `PacerController`.
- `PacerController`: the hardware entry point. What a switch does depends on the active preset (`PresetKind`,
  decoded from the preset-loaded CC by `PresetAnnouncement` and stored in the global setting *Active preset*): loop
  switches go to `LooperController`, every other switch runs its `Action` - FX actions (`Action.isFx`) in
  `FxController`, the rest in `LooperController`. `Action.MOMENTARY` as a hold runs the tap again on release. Each
  preset has its own switch actions and pedal targets; the footswitch jacks are shared.
- `LooperController`: loop switches (`loopSwitchTap/DoubleTap/Hold/Release`), looper actions (`perform` +
  `actionLed`), parameter pedal targets, LED test, beat counter, exclusive arm (`enforceExclusiveArm`, 40 ms tick).
- `FxController` (FX preset, docs/FX-PRESET.md): instruments are tracks found by *name* (document settings
  *Instrument A-D*, the focused instrument, snapshots as hidden strings encoded by `fx/SnapshotBank`). All Bitwig
  access goes through `fx/FxTracks`, implemented by `bitwig/BitwigFxTracks`: a flat 64-track bank plus a cursor track
  created with `followSelection = false` and pointed with `selectChannel`, with a 6-device bank and the track's remote
  controls. `tick` re-points the cursor, selects the "Pacer" remote page by name and re-resolves the instruments'
  track positions every 500 ms (`resolve`) - LED flushes must never scan 64 track names, they read the cache. `fx/FxTarget.resolve`: a track
  with a "Pacer" page uses only that page, others their devices.
- Pure, unit-tested: `looper/` (LoopState, LoopAction, LoopLeds, TapTiming, enums for settings), `led/` (LedClock,
  LedPattern, LedState, LedColour.nearest, SwitchLedWriter), `fx/` (FxTarget, FxLookup, SnapshotBank), `preset/`,
  `controller/PacerMap` + `MidiFilters`.
- Adding an assignable action: constant in `looper/Action` (mark it `destructive` if it deletes or overwrites, pass
  `fx = true` for FX actions), case in `LooperController.perform` and `.actionLed` or in `FxController`. Settings store
  enum *labels*, so renaming a label resets users' choice to the default. Keep setting labels unique (the FX preset's
  switch settings are "FX SW 1 tap" etc.).
- `LooperController.tick ()` (every 40 ms) drives count-ins, "match the first loop" (`LoopLengthTracker`), fades
  (`VolumeFade`) and exclusive arm. Anything that has to watch Bitwig state over time goes there (or in
  `FxController.tick`).
- `TapHoldCommand` owns tap-vs-hold timing, including the extra delay for destructive holds (it tracks press
  generations so a re-press never inherits an old hold).
- Fades write `getVolumeParameter ().setNormalizedValue (...)` per tick and remember the original volumes
  (`IValueChanger.toNormalizedValue (track.getVolume ())`); a fade-out waits for the loops to really stop before
  restoring them.
- Double/halve use the launcher cursor clip: `model.ensureClip ()` in `createModel` creates it (init phase); select
  the slot, then act on `model.getCursorClip ()` ~150 ms later once the cursor has followed.
- Loop tracks (1-6, `getLoopTrackCount`) and loop switches (0-6, `LoopSwitchCount`) are separate settings: a switch
  is a loop switch if its index is below both (looper preset only). Every other switch uses its tap / double-tap /
  hold `Action`s.
- Double-tap never delays the tap (`TapHoldCommand.withDoubleTap`): a second tap inside the window runs the double-tap
  action instead of a second tap. Holds reset the double-tap window.
- The loop track position is a *document* setting (`documentSettings.getRangeSetting`, saved per project);
  `LooperController.applyLoopTrackStart` scrolls the bank to it, and moving the window from the Pacer writes it back.
- Notifications go through `notify` (confirmations, level "All" only) or `notifyImportant` (navigation, warnings,
  count-ins, loop lengths, resets, focus changes). "Show looper status" always shows.
- The looper MIDI channel is a setting read during init (DrivenByMoss' `EnumSettingImpl.addValueObserver` fires the
  stored value immediately); bindings, LED writers and note input filters use the channel captured in
  `createSurface`, and changing the setting later calls `host.restart ()`.
- LED mode `AUTO` resolves to the variant announced by the preset-loaded CC value (127 / 2 looper, 17 / 18 FX);
  always use `PacerConfiguration.getEffectiveLedMode ()` when driving LEDs.
- `daw/DawModeController` (port 2, opt-in setting): raw `setMidiCallback` / `setSysexCallback` on
  `midiAccess.createInput (1, null)`, LED feedback as CC 127/0 on channel 16 via `createOutput (1)`. `DawModeSysex`
  reproduces Nektar's messages byte for byte, including the odd 0x1F "checksum" of the slot-colour message. The
  controller definition has 2 in / 2 out ports since wave 4, so existing controller instances need port 2 assigned.
- Hold-to-record: `TapHoldCommand` has a release callback; `LooperController.loopSwitchTap/Hold/Release` track
  whether the current press started a recording (`holdRecording`), and a release before recording started closes it
  as soon as it starts (`closeWhenRecording`, checked in `tick`).
- Quantized mutes (`pendingMutes`) apply in `tick` at `MuteTiming.nextBoundary`; anything that moves bank positions
  (track scroll) applies them first.
- Pedals: a linear parameter target is bound directly (Bitwig binding, take-over etc.); MIDI and FX targets and
  response curves unbind the parameter so the pedal's `ContinuousCommand` runs (`PacerController.pedalMoved`).
  Changing the active preset re-binds both pedals.

## Framework notes (DrivenByMoss)

- `ModelSetup.setNumScenes (1)`: each track's slot bank is exactly the current scene row; scrolling
  `trackBank.getSceneBank ()` moves the row for every loop track.
- Lights: `surface.createLight (null, IntSupplier, IntConsumer, IntFunction<ColorEx>, button)`. The consumer only runs
  when the supplied int changes or on `forceFlush`. Blinking = the supplier reads the wall clock, and a
  `host.scheduleTask` tick calls `ControllerHost.requestFlush ()` every 40 ms.
- The note input "PACER" passes channels 1-15 (`MidiFilters.allChannelsExcept`); channel 16 only reaches the
  hardware bindings. DrivenByMoss' `NoteInputImpl` cannot inject MIDI, so the setup calls `createInput (null)` (no
  note input) and the extension definition creates the Bitwig `NoteInput` itself (`NoteInputFactory`, init phase),
  handing back `sendRawMidiEvent` for the pedal MIDI targets.
- A pedal with no parameter bound (`bind ((IParameter) null)`) runs its `ContinuousCommand` with 0-127.
- `IHwFader.bind (IParameter)` removes the previous binding, so pedals can be re-targeted at runtime.
- `ITrack.getIndex ()` is the bank position; after scrolling the track bank the same position is another track.
- The live play position is not in the framework: `TransportBeatClock` uses `Transport.playPosition ()` and
  extrapolates with the tempo between updates.
- `ISlot.launch (pressed, alt)` maps to `launch ()` / `launchRelease ()`; call both for a click.
- `ITrackBank.stop (alt)` stops the whole scene bank (all tracks of the page).
- Setting observers may fire before `startup ()`: anything touching the transport checks the `running` flag and is
  re-applied in `startup ()`.
- The framework model has one cursor track (follows the selection). Anything that must not follow the selection needs
  its own raw `createCursorTrack (id, name, sends, scenes, false)` from the extension definition. `ISetting.setVisible
  (false)` hides internal document settings.
