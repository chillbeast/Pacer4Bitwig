// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.command.core.ContinuousCommand;
import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.controller.AbstractControllerSetup;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.ISetupFactory;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwFader;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.ModelSetup;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.midi.IMidiAccess;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.utils.ButtonEvent;

import dev.pacer4bitwig.pacer.clock.BeatClock;
import dev.pacer4bitwig.pacer.command.TapHoldCommand;
import dev.pacer4bitwig.pacer.controller.MidiFilters;
import dev.pacer4bitwig.pacer.controller.PacerControlSurface;
import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.daw.DawModeController;
import dev.pacer4bitwig.pacer.fx.FxTracks;
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.SwitchLedWriter;
import dev.pacer4bitwig.pacer.live.GuardedGate;
import dev.pacer4bitwig.pacer.live.LiveBoard;
import dev.pacer4bitwig.pacer.live.PacerSysex;
import dev.pacer4bitwig.pacer.live.PresetCheck;
import dev.pacer4bitwig.pacer.live.PresetGuard;
import dev.pacer4bitwig.pacer.midi.NoteInputFactory;
import dev.pacer4bitwig.pacer.mode.Mode;
import dev.pacer4bitwig.pacer.mode.ModeMenu;
import dev.pacer4bitwig.util.Diagnostics;
import dev.pacer4bitwig.util.FailSoft;

import java.util.function.Supplier;
import java.util.regex.Pattern;


/**
 * Setup of the PACER Looper: creates the hardware proxies and wires them to the {@link PacerController} (port 1:
 * looper and FX presets) and the {@link DawModeController} (port 2).
 */
public class PacerControllerSetup extends AbstractControllerSetup<PacerControlSurface, PacerConfiguration>
{
    /** Name of the note input in Bitwig's track input chooser. */
    public static final String         NOTE_INPUT_NAME          = "PACER";

    /** Blink resolution. */
    private static final long          TICK_MS                  = 40;
    /** A press puts the switch's CC readout on the Pacer's display; put the mode name back after this. */
    private static final long          NAME_RESTORE_MS          = 400;
    /** After a press or an announcement, flush the LEDs every tick for this long even if nothing blinks. */
    private static final long          FLUSH_AFTER_EVENT_MS     = 1000;
    /** A name restore the preset check holds back is tried again on every tick, for this long. */
    private static final long          NAME_RESTORE_GIVE_UP_MS  = 3000;
    /** The row on the display when the scene has no name: "ROW 3", "ROW12", "R128" (as preset check keys). */
    private static final Pattern       ROW_NAME                 = Pattern.compile ("ROW [1-9]|ROW[1-9][0-9]|R[1-9][0-9][0-9] ");
    /** Tracks of a freshly opened project can arrive after startup: apply the loop track position again after these. */
    private static final long []       TRACK_START_RETRIES_MS   =
    {
        1000,
        3000
    };

    private final Runnable             requestFlush;
    private final Supplier<BeatClock>  clockFactory;
    private final NoteInputFactory     noteInputFactory;
    private final Supplier<FxTracks>   fxTracksFactory;
    private final IHwFader []          pedals                   = new IHwFader [PacerConfiguration.NUM_EXPRESSION];
    private LooperController           looper;
    private PacerController            controller;
    private DawModeController          dawMode;
    private LiveBoard                  board;
    /** Port 1, for the live colour and name writes. Only available once the surface exists. */
    private IMidiOutput                sysexOutput;
    /** When a switch last put its CC readout on the Pacer's display, 0 when the name is already back. */
    private volatile long              displayTakenAt;
    /** The looper channel the bindings were created with; changing the setting restarts the extension. */
    private int                        midiChannel              = PacerMap.DEFAULT_MIDI_CHANNEL;
    private volatile boolean           running;
    /** Is the Pacer still on the Bitwig preset? Live writes wait while it is not (setting "Check the Pacer..."). */
    private final PresetGuard          presetGuard;
    private GuardedGate                writeGate;
    /** Keeps one failing entry point from taking the rest down. */
    private final FailSoft             failSoft;
    private final Diagnostics          diagnostics;
    /** Flush the LEDs on every tick until then, blinking or not. */
    private volatile long              flushUntil;


    /**
     * Constructor.
     *
     * @param host The DAW host
     * @param factory The factory
     * @param globalSettings The global settings
     * @param documentSettings The document (project) specific settings
     * @param requestFlush Asks Bitwig to call flush soon (drives the LED blinking)
     * @param clockFactory Creates the transport clock; called during init
     * @param noteInputFactory Creates the note input; called during init
     * @param fxTracksFactory Creates the FX preset's access to the project's tracks; called during init
     */
    public PacerControllerSetup (final IHost host, final ISetupFactory factory, final ISettingsUI globalSettings, final ISettingsUI documentSettings, final Runnable requestFlush, final Supplier<BeatClock> clockFactory, final NoteInputFactory noteInputFactory, final Supplier<FxTracks> fxTracksFactory)
    {
        super (factory, host, globalSettings, documentSettings);

        this.requestFlush = requestFlush;
        this.clockFactory = clockFactory;
        this.noteInputFactory = noteInputFactory;
        this.fxTracksFactory = fxTracksFactory;
        this.valueChanger = new TwosComplementValueChanger (128, 1);

        this.colorManager = new ColorManager ();
        this.colorManager.registerColorIndex (ColorManager.BUTTON_STATE_OFF, LedColour.OFF.ordinal ());
        this.colorManager.registerColorIndex (ColorManager.BUTTON_STATE_ON, LedColour.WHITE.ordinal ());
        this.colorManager.registerColorIndex (ColorManager.BUTTON_STATE_HI, LedColour.WHITE.ordinal ());
        for (final LedColour colour: LedColour.values ())
            this.colorManager.registerColor (colour.ordinal (), colour.getColorEx ());

        this.configuration = new PacerConfiguration (host, this.valueChanger, factory.getArpeggiatorModes ());
        final PacerConfiguration settings = this.configuration;
        // Names only the extension leaves on the display: an earlier session's, found at startup
        this.presetGuard = new PresetGuard (PacerMap.PRESET_NAME, key -> isOwnName (key, settings));
        this.diagnostics = new Diagnostics (host::println, settings::getDiagnosticsLevel);
        this.failSoft = new FailSoft ( (where, error, first) -> {
            host.error ("PACER Looper: error in " + where + " - carried on", error);
            if (first)
                host.showNotification ("PACER Looper caught an error (" + where + ") and carried on - details in the controller console");
        }, System::currentTimeMillis);
    }


    /** {@inheritDoc} */
    @Override
    protected void createScales ()
    {
        // Not used
    }


    /** {@inheritDoc} */
    @Override
    protected void createModel ()
    {
        final ModelSetup ms = new ModelSetup ();
        ms.setNumTracks (PacerMap.MAX_LOOP_TRACKS);
        ms.setNumScenes (1);
        ms.setNumSends (2);
        ms.setNumParams (8);
        ms.setHasFlatTrackList (true);
        ms.enableMainDrumDevice (false);
        this.model = this.factory.createModel (this.configuration, this.colorManager, this.valueChanger, this.scales, ms);
        // The launcher cursor clip (follows the selected slot) for doubling and halving loops
        this.model.ensureClip ();

        this.looper = new LooperController (this.host, this.model, this.configuration, this.clockFactory.get ());
        final FxController fx = new FxController (this.host, this.configuration, this.fxTracksFactory.get (), this::getSelectedTrackName);
        // The output only exists once the surface is created, so the board sends through this setup
        this.board = new LiveBoard (this::sendSysex);
        this.writeGate = new GuardedGate (this.presetGuard, () -> this.configuration.getPresetCheck () != PresetCheck.OFF, System::currentTimeMillis);
        this.board.setGate (this.writeGate);
        this.controller = new PacerController (this.host, this.configuration, this.looper, fx, this.board);
        this.controller.setModeListener (this::modeChanged);
        this.controller.setDiagnostics (this.diagnostics);
        this.looper.setEventSink (this.controller::showEvent);
        fx.setEventSink (this.controller::showEvent);
    }


    /** {@inheritDoc} */
    @Override
    protected void createSurface ()
    {
        // Settings have delivered their stored values during init, so the channel is known here
        this.midiChannel = this.configuration.getLooperMidiChannel ();

        final IMidiAccess midiAccess = this.factory.createMidiAccess ();

        // Port 1: hardware bindings only - the note input comes from the Bitwig layer so pedals can inject MIDI into it
        final IMidiOutput output = midiAccess.createOutput ();
        this.sysexOutput = output;
        final IMidiInput input = midiAccess.createInput (null);
        // Answers to the preset check (read-only GETs of the loaded preset's name)
        input.setSysexCallback (data -> this.failSoft.run ("SysEx in", () -> this.sysexReceived (data)));
        // The looper channel is reserved; everything else the Pacer presets send reaches Bitwig tracks
        this.controller.setMidiSender (this.noteInputFactory.create (NOTE_INPUT_NAME, MidiFilters.allChannelsExcept (this.midiChannel)));
        this.surfaces.add (new PacerControlSurface (this.host, this.colorManager, this.configuration, output, input));

        // Port 2: the Pacer's DAW port
        this.dawMode = new DawModeController (this.host, this.model, midiAccess.createInput (1, null), midiAccess.createOutput (1), this.configuration::isDawMode);
    }


    /** {@inheritDoc} */
    @Override
    protected void createObservers ()
    {
        super.createObservers ();

        // The framework calls every observer once at the end of init; the test must only run when it is clicked
        this.configuration.addSettingObserver (PacerConfiguration.LED_TEST, () -> {
            if (this.running)
            {
                this.looper.startLedTest ();
                // Nothing changes in Bitwig, so nothing would flush the LEDs for the first step
                this.eventHappened ();
            }
        });
        this.configuration.addSettingObserver (PacerConfiguration.CUSTOM_MODE, () -> {
            // Laying out the custom layout while standing in the mode it changes should show up straight away
            if (this.running)
            {
                this.failSoft.run ("custom layout", this.controller::customLayoutChanged);
                this.eventHappened ();
                this.getSurface ().forceFlush ();
            }
        });
        this.configuration.addSettingObserver (PacerConfiguration.LAUNCH_QUANTIZATION, () -> {
            if (this.running)
                this.looper.applyLaunchQuantization ();
        });
        this.configuration.addSettingObserver (PacerConfiguration.LOOP_LENGTH, () -> {
            if (this.running)
                this.looper.applyLoopLength ();
        });
        this.configuration.addSettingObserver (PacerConfiguration.EXPRESSION_1, () -> this.bindPedal (0));
        this.configuration.addSettingObserver (PacerConfiguration.EXPRESSION_2, () -> this.bindPedal (1));
        this.configuration.addSettingObserver (PacerConfiguration.DAW_MODE, () -> {
            if (this.running)
                this.dawMode.update ();
        });
        this.configuration.addSettingObserver (PacerConfiguration.LOOP_TRACK_START, () -> {
            if (this.running)
                this.looper.applyLoopTrackStart ();
        });
        this.configuration.addSettingObserver (PacerConfiguration.LOOPER_CHANNEL, () -> {
            if (!this.running || this.configuration.getLooperMidiChannel () == this.midiChannel)
                return;
            // MIDI bindings and note input filters are fixed at init
            this.host.showNotification ("PACER Looper restarts to use MIDI channel " + (this.configuration.getLooperMidiChannel () + 1));
            this.host.restart ();
        });
    }


    /** {@inheritDoc} */
    @Override
    protected void registerTriggerCommands ()
    {
        final PacerControlSurface surface = this.getSurface ();
        final IMidiInput input = surface.getMidiInput ();
        final IMidiOutput output = surface.getMidiOutput ();

        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            final int index = i;
            final IHwButton button = surface.createButton (ButtonID.get (ButtonID.ROW1_1, i), PacerMap.SWITCH_NAMES[i]);
            final String name = PacerMap.SWITCH_NAMES[i];
            // SW 6's tap changes the mode, which a double-tap must not do first: it waits out the double-tap window
            final TapHoldCommand command = new TapHoldCommand ( () -> this.controller.isTapOnPress (index), () -> this.controller.tap (index), () -> this.controller.hold (index), () -> this.controller.release (index), () -> this.afterSwitchEvent (index), () -> this.controller.getExtraHoldMillis (index), (task, delay) -> this.host.scheduleTask (this.failSoft.wrap (name, task), delay)).withPress ( () -> this.controller.press (index)).withDoubleTap ( () -> this.controller.doubleTap (index), () -> this.controller.isDoubleTapEnabled (index), () -> this.configuration.getDoubleTapWindow ().getMillis (), System::currentTimeMillis).withDelayedTap ( () -> Mode.isModeSwitch (index));
            button.bind (this.failSoft (name, command));
            button.bind (input, BindType.CC, this.midiChannel, PacerMap.switchCC (i));

            final SwitchLedWriter writer = new SwitchLedWriter (i, (cc, value) -> output.sendCCEx (this.midiChannel, cc, value));
            surface.createLight (null, () -> this.failSoft.getAsInt ("LEDs", () -> this.controller.getLedCode (index), 0), writer, code -> LedColour.fromCode (code).getColorEx (), button);
        }

        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
        {
            final int index = i;
            final String name = "FS " + (i + 1);
            final IHwButton button = surface.createButton (ButtonID.get (ButtonID.FOOTSWITCH1, i), name);
            final TapHoldCommand command = new TapHoldCommand ( () -> this.controller.isFootswitchTapOnPress (index), () -> this.controller.footswitchTap (index), () -> this.controller.footswitchHold (index), () -> this.controller.footswitchRelease (index), this::eventHappened, () -> this.controller.getFootswitchExtraHoldMillis (index), (task, delay) -> this.host.scheduleTask (this.failSoft.wrap (name, task), delay)).withPress ( () -> this.controller.footswitchPress (index)).withDoubleTap ( () -> this.controller.footswitchDoubleTap (index), () -> this.controller.isFootswitchDoubleTapEnabled (index), () -> this.configuration.getDoubleTapWindow ().getMillis (), System::currentTimeMillis);
            button.bind (this.failSoft (name, command));
            button.bind (input, BindType.CC, this.midiChannel, PacerMap.FOOTSWITCH_CC_BASE + i);
        }

        // Each Pacer4Bitwig preset announces itself (which preset, and its LED variant) whenever it is selected
        final IHwButton presetLoaded = surface.createButton (ButtonID.F1, "Preset loaded");
        presetLoaded.bind ( (event, velocity) -> {
            if (event != ButtonEvent.DOWN)
                return;
            this.failSoft.run ("preset loaded", () -> {
                // Selected on the Pacer this moment, so it is loaded: the preset check need not ask
                this.presetGuard.presetAnnounced (System.currentTimeMillis ());
                this.controller.presetAnnounced (velocity);
                this.eventHappened ();
                surface.forceFlush ();
            });
        });
        presetLoaded.bind (input, BindType.CC, this.midiChannel, PacerMap.PRESET_LOADED_CC);
    }


    /** {@inheritDoc} */
    @Override
    protected void registerContinuousCommands ()
    {
        final PacerControlSurface surface = this.getSurface ();
        for (int i = 0; i < PacerConfiguration.NUM_EXPRESSION; i++)
        {
            final int index = i;
            final IHwFader pedal = surface.createFader (ContinuousID.get (ContinuousID.FADER1, i), "EXP " + (i + 1), true);
            pedal.bind (surface.getMidiInput (), BindType.CC, this.midiChannel, i == 0 ? PacerMap.EXP1_CC : PacerMap.EXP2_CC);
            // Used whenever no parameter is bound directly: MIDI, FX and loop targets, response curves and ranges, pick-up
            final String name = "EXP " + (i + 1);
            pedal.bind ((ContinuousCommand) value -> this.failSoft.run (name, () -> {
                this.controlUsed ();
                this.controller.pedalMoved (index, value);
            }));
            this.pedals[i] = pedal;
            this.bindPedal (i);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void layoutControls ()
    {
        final PacerControlSurface surface = this.getSurface ();

        // Bottom row SW 1-6, top row SW A-D centred above it
        for (int i = 0; i < 6; i++)
            surface.getButton (ButtonID.get (ButtonID.ROW1_1, i)).setBounds (8 + i * 31, 62, 26, 26);
        for (int i = 0; i < 4; i++)
            surface.getButton (ButtonID.get (ButtonID.ROW1_1, 6 + i)).setBounds (39 + i * 31, 28, 26, 26);

        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
            surface.getButton (ButtonID.get (ButtonID.FOOTSWITCH1, i)).setBounds (8 + i * 18, 4, 16, 16);
        surface.getButton (ButtonID.F1).setBounds (84, 4, 30, 16);
        surface.getContinuous (ContinuousID.FADER1).setBounds (160, 4, 14, 20);
        surface.getContinuous (ContinuousID.FADER2).setBounds (178, 4, 14, 20);
    }


    /** {@inheritDoc} */
    @Override
    public void startup ()
    {
        this.running = true;
        this.looper.applyLaunchQuantization ();
        this.looper.applyLoopLength ();
        this.looper.applyLoopTrackStart ();
        for (final long delay: TRACK_START_RETRIES_MS)
            this.host.scheduleTask ( () -> {
                if (this.running)
                    this.looper.applyLoopTrackStart ();
            }, delay);
        this.dawMode.update ();
        // The Pacer may be showing whatever its stored preset says, so write the whole board
        this.controller.applyStartupMode ();
        this.getSurface ().forceFlush ();
        this.tick ();

        if (this.configuration.getNotificationLevel ().shows (true))
        {
            final String version = PacerControllerSetup.class.getPackage ().getImplementationVersion ();
            this.host.showNotification ("PACER Looper " + (version == null ? "dev" : version) + " ready on MIDI channel " + (this.midiChannel + 1) + ", mode " + this.controller.getMode ().getLabel ());
        }
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        this.running = false;
        // A board nobody is driving should not look live - unless the Pacer is on someone else's preset
        if (this.configuration.getPresetCheck () == PresetCheck.OFF || !this.presetGuard.isElsewhere ())
            this.failSoft.run ("exit", this.controller::blackout);
        this.dawMode.shutdown ();
        super.exit ();
    }


    private void tick ()
    {
        if (!this.running)
            return;
        try
        {
            // Each part on its own: one that keeps failing must not stop the colours, the preset check or the others
            this.failSoft.run ("looper tick", this.controller::tickLooper);
            this.failSoft.run ("FX tick", this.controller::tickFx);
            this.failSoft.run ("paint", this.controller::paint);
            this.failSoft.run ("preset check", this::checkPreset);
            this.failSoft.run ("name restore", this::restoreName);
            this.failSoft.run ("DAW mode", this.dawMode::flushLeds);
            // Blinking needs a flush on every tick. A steady board does not: Bitwig flushes whenever its state
            // changes, and a press - which can change what the LEDs show without Bitwig knowing - buys a second.
            if (this.controller.isAnimating () || System.currentTimeMillis () < this.flushUntil)
                this.requestFlush.run ();
        }
        finally
        {
            // Whatever happened above, the tick goes on: it drives count-ins, fades and every colour
            this.host.scheduleTask (this::tick, TICK_MS);
        }
    }


    /** The preset check: give up on a question nobody answered, and ask when the guard wants to know. */
    private void checkPreset ()
    {
        final PresetCheck check = this.configuration.getPresetCheck ();
        if (check == PresetCheck.OFF)
            return;
        final long now = System.currentTimeMillis ();
        if (this.presetGuard.checkTimeout (now))
            this.diagnostics.log (Diagnostics.Level.ACTIONS, () -> "preset check: no answer, " + this.presetGuard.getState ());
        if (this.presetGuard.shouldProbe (now, this.writeGate.takeWaiting (), check == PresetCheck.REGULAR))
        {
            this.presetGuard.probeSent (now);
            this.board.request (PacerSysex.requestName ());
        }
    }


    private void sysexReceived (final String data)
    {
        this.diagnostics.log (Diagnostics.Level.ALL, () -> "SysEx in: " + data);
        final String name = PacerSysex.parseName (data);
        if (name == null || this.configuration.getPresetCheck () == PresetCheck.OFF)
            return;
        final PresetGuard.State before = this.presetGuard.getState ();
        final PresetGuard.Verdict verdict = this.presetGuard.answered (name, System.currentTimeMillis ());
        if (this.presetGuard.getState () != before)
            this.diagnostics.log (Diagnostics.Level.ACTIONS, () -> "preset check: \"" + name + "\", " + before + " -> " + this.presetGuard.getState ());
        switch (verdict)
        {
            case REPAINT -> {
                this.controller.repaintAll ();
                this.eventHappened ();
                this.getSurface ().forceFlush ();
            }
            case FOREIGN -> {
                if (this.configuration.getNotificationLevel ().shows (true))
                    this.host.showNotification ("PACER Looper: the Pacer is on another preset (\"" + name.trim () + "\") - it is left alone until you select the Bitwig preset again");
            }
            case NONE -> {
                // Nothing changed
            }
        }
    }


    private void bindPedal (final int index)
    {
        this.controller.pedalRetargeted (index);
        final IHwFader pedal = this.pedals[index];
        if (pedal != null)
            // Null (no parameter) routes the pedal to its command
            pedal.bind (this.controller.getPedalBinding (index));
    }


    private String getSelectedTrackName ()
    {
        final ICursorTrack cursorTrack = this.model.getCursorTrack ();
        return cursorTrack.doesExist () ? cursorTrack.getName () : "";
    }


    private void sendSysex (final String hex)
    {
        this.diagnostics.log (Diagnostics.Level.ALL, () -> "SysEx out: " + hex);
        if (this.sysexOutput != null)
            this.sysexOutput.sendSysex (hex);
    }


    /**
     * A switch or jack command that reports what it throws instead of passing it on to Bitwig. The press itself is
     * proof that the Bitwig preset is loaded.
     */
    private TriggerCommand failSoft (final String name, final TriggerCommand command)
    {
        return (event, velocity) -> this.failSoft.run (name, () -> {
            this.controlUsed ();
            command.execute (event, velocity);
        });
    }


    /** A control of the Bitwig preset sent something: tell the preset check, and paint if it thought otherwise. */
    private void controlUsed ()
    {
        if (this.configuration.getPresetCheck () == PresetCheck.OFF || !this.presetGuard.controlUsed (System.currentTimeMillis ()))
            return;
        this.diagnostics.log (Diagnostics.Level.ACTIONS, () -> "preset check: a control of the Bitwig preset was used - painting it again");
        this.controller.repaintAll ();
        this.eventHappened ();
    }


    /**
     * @param key A name as the preset check compares it
     * @param settings The settings, for the custom mode's name
     * @return True if only the extension puts this name on the display
     */
    private static boolean isOwnName (final String key, final PacerConfiguration settings)
    {
        if (ROW_NAME.matcher (key).matches ())
            return true;
        if (key.equals (PresetGuard.key ("OFF")) || key.equals (PresetGuard.key (ModeMenu.NAME)) || key.equals (PresetGuard.key (settings.getCustomBoard ().getDisplayName ())))
            return true;
        for (final Mode mode: Mode.values ())
            if (key.equals (PresetGuard.key (mode.getDisplayName ())))
                return true;
        return false;
    }


    /** Something happened that may change the LEDs without Bitwig knowing: keep flushing for a moment. */
    private void eventHappened ()
    {
        this.flushUntil = System.currentTimeMillis () + FLUSH_AFTER_EVENT_MS;
    }


    private void modeChanged ()
    {
        this.eventHappened ();
        // Modes have their own pedal targets
        this.bindPedal (0);
        this.bindPedal (1);
        if (this.running)
            this.getSurface ().forceFlush ();
    }


    /**
     * Pressing a switch replaces the Pacer's display with that switch's CC readout. Note when that happened; the
     * tick puts the name back. Doing it here with a scheduled task per event would add work to the controller
     * thread on every press, which is exactly what must not happen while a foot is on a switch.
     */
    private void afterSwitchEvent (final int switchIndex)
    {
        this.displayTakenAt = System.currentTimeMillis ();
        this.eventHappened ();
    }


    private void restoreName ()
    {
        final long takenAt = this.displayTakenAt;
        final long now = System.currentTimeMillis ();
        if (takenAt == 0 || now - takenAt < NAME_RESTORE_MS)
            return;
        // Held back while the preset check asks, it is tried again on the next ticks
        if (!this.configuration.isKeepModeName () || this.controller.isMenuOpen () || this.board.repeatName () || now - takenAt > NAME_RESTORE_GIVE_UP_MS)
            this.displayTakenAt = 0;
    }

}
