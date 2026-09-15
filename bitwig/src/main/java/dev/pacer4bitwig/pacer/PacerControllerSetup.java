// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.command.core.ContinuousCommand;
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
import dev.pacer4bitwig.pacer.led.LedMode;
import dev.pacer4bitwig.pacer.led.SwitchLedWriter;
import dev.pacer4bitwig.pacer.midi.NoteInputFactory;
import dev.pacer4bitwig.pacer.preset.PresetKind;

import java.util.function.Supplier;


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
    /** Let the Pacer finish its own LED handling of a press before repainting. */
    private static final long          REPAINT_DELAY_MS         = 30;
    /** Tracks of a freshly opened project can arrive after startup: apply the loop track position once more. */
    private static final long          TRACK_START_RETRY_MS     = 1000;

    private final Runnable             requestFlush;
    private final Supplier<BeatClock>  clockFactory;
    private final NoteInputFactory     noteInputFactory;
    private final Supplier<FxTracks>   fxTracksFactory;
    private final IHwButton []         switches                 = new IHwButton [PacerMap.NUM_SWITCHES];
    private final SwitchLedWriter []   ledWriters               = new SwitchLedWriter [PacerMap.NUM_SWITCHES];
    private final IHwFader []          pedals                   = new IHwFader [PacerConfiguration.NUM_EXPRESSION];
    private LooperController           looper;
    private PacerController            controller;
    private DawModeController          dawMode;
    /** The looper channel the bindings were created with; changing the setting restarts the extension. */
    private int                        midiChannel              = PacerMap.DEFAULT_MIDI_CHANNEL;
    private volatile boolean           running;


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
        this.controller = new PacerController (this.host, this.configuration, this.looper, fx);
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
        final IMidiInput input = midiAccess.createInput (null);
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

        this.configuration.addSettingObserver (PacerConfiguration.LED_MODE, this::resetLeds);
        this.configuration.addSettingObserver (PacerConfiguration.LED_TEST, () -> this.looper.startLedTest ());
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
        this.configuration.addSettingObserver (PacerConfiguration.ACTIVE_PRESET, () -> {
            // The pedals of the two presets have their own targets
            this.bindPedal (0);
            this.bindPedal (1);
            if (!this.running)
                return;
            this.controller.presetChanged ();
            this.getSurface ().forceFlush ();
        });
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
            button.bind (new TapHoldCommand ( () -> this.controller.isTapOnPress (index), () -> this.controller.tap (index), () -> this.controller.hold (index), () -> this.controller.release (index), () -> this.scheduleRepaint (index), () -> this.controller.getExtraHoldMillis (index), this.host::scheduleTask).withDoubleTap ( () -> this.controller.doubleTap (index), () -> this.controller.isDoubleTapEnabled (index), () -> this.configuration.getDoubleTapWindow ().getMillis (), System::currentTimeMillis));
            button.bind (input, BindType.CC, this.midiChannel, PacerMap.switchCC (i));

            final SwitchLedWriter writer = new SwitchLedWriter (i, this.configuration::getEffectiveLedMode, (cc, value) -> output.sendCCEx (this.midiChannel, cc, value));
            surface.createLight (null, () -> this.controller.getLedCode (index), writer, code -> LedColour.fromCode (code).getColorEx (), button);

            this.switches[i] = button;
            this.ledWriters[i] = writer;
        }

        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
        {
            final int index = i;
            final IHwButton button = surface.createButton (ButtonID.get (ButtonID.FOOTSWITCH1, i), "FS " + (i + 1));
            button.bind (new TapHoldCommand ( () -> this.controller.isFootswitchTapOnPress (index), () -> this.controller.footswitchTap (index), () -> this.controller.footswitchHold (index), () -> this.controller.footswitchRelease (index), null, () -> this.controller.getFootswitchExtraHoldMillis (index), this.host::scheduleTask).withDoubleTap ( () -> this.controller.footswitchDoubleTap (index), () -> this.controller.isFootswitchDoubleTapEnabled (index), () -> this.configuration.getDoubleTapWindow ().getMillis (), System::currentTimeMillis));
            button.bind (input, BindType.CC, this.midiChannel, PacerMap.FOOTSWITCH_CC_BASE + i);
        }

        // Each Pacer4Bitwig preset announces itself (which preset, and its LED variant) whenever it is selected
        final IHwButton presetLoaded = surface.createButton (ButtonID.F1, "Preset loaded");
        presetLoaded.bind ( (event, velocity) -> {
            if (event != ButtonEvent.DOWN)
                return;
            this.controller.presetAnnounced (velocity);
            surface.forceFlush ();
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
            // Used whenever no parameter is bound directly: MIDI and FX targets, response curves and ranges
            pedal.bind ((ContinuousCommand) value -> this.controller.pedalMoved (index, value));
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
        this.host.scheduleTask ( () -> {
            if (this.running)
                this.looper.applyLoopTrackStart ();
        }, TRACK_START_RETRY_MS);
        this.dawMode.update ();
        this.getSurface ().forceFlush ();
        this.tick ();

        if (this.configuration.getNotificationLevel ().shows (true))
        {
            final String version = PacerControllerSetup.class.getPackage ().getImplementationVersion ();
            final String preset = this.configuration.getActivePreset () == PresetKind.FX ? " (FX preset)" : "";
            this.host.showNotification ("PACER Looper " + (version == null ? "dev" : version) + " ready on MIDI channel " + (this.midiChannel + 1) + preset);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void exit ()
    {
        this.running = false;
        this.dawMode.shutdown ();
        super.exit ();
    }


    private void tick ()
    {
        if (!this.running)
            return;
        this.controller.tick ();
        this.dawMode.flushLeds ();
        this.requestFlush.run ();
        this.host.scheduleTask (this::tick, TICK_MS);
    }


    private void bindPedal (final int index)
    {
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


    private void scheduleRepaint (final int switchIndex)
    {
        if (this.configuration.getEffectiveLedMode () != LedMode.MULTI_COLOUR)
            return;
        this.host.scheduleTask ( () -> this.switches[switchIndex].getLight ().forceFlush (), REPAINT_DELAY_MS);
    }


    private void resetLeds ()
    {
        if (!this.running)
            return;
        for (final SwitchLedWriter writer: this.ledWriters)
            writer.reset ();
        this.getSurface ().forceFlush ();
    }
}
