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
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedMode;
import dev.pacer4bitwig.pacer.led.SwitchLedWriter;
import dev.pacer4bitwig.pacer.midi.NoteInputFactory;

import java.util.function.Supplier;


/**
 * Setup of the PACER Looper: creates the hardware proxies and wires them to the {@link LooperController} (port 1)
 * and the {@link DawModeController} (port 2).
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
    private final IHwButton []         switches                 = new IHwButton [PacerMap.NUM_SWITCHES];
    private final SwitchLedWriter []   ledWriters               = new SwitchLedWriter [PacerMap.NUM_SWITCHES];
    private final IHwFader []          pedals                   = new IHwFader [PacerConfiguration.NUM_EXPRESSION];
    private LooperController           looper;
    private DawModeController          dawMode;
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
     */
    public PacerControllerSetup (final IHost host, final ISetupFactory factory, final ISettingsUI globalSettings, final ISettingsUI documentSettings, final Runnable requestFlush, final Supplier<BeatClock> clockFactory, final NoteInputFactory noteInputFactory)
    {
        super (factory, host, globalSettings, documentSettings);

        this.requestFlush = requestFlush;
        this.clockFactory = clockFactory;
        this.noteInputFactory = noteInputFactory;
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
    }


    /** {@inheritDoc} */
    @Override
    protected void createSurface ()
    {
        final IMidiAccess midiAccess = this.factory.createMidiAccess ();

        // Port 1: hardware bindings only - the note input comes from the Bitwig layer so pedals can inject MIDI into it
        final IMidiOutput output = midiAccess.createOutput ();
        final IMidiInput input = midiAccess.createInput (null);
        // Channel 16 belongs to the looper; everything the other Pacer presets send reaches Bitwig tracks
        this.looper.setMidiSender (this.noteInputFactory.create (NOTE_INPUT_NAME, MidiFilters.allChannelsExcept (PacerMap.MIDI_CHANNEL)));
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
        this.configuration.addSettingObserver (PacerConfiguration.DAW_MODE, () -> {
            if (this.running)
                this.dawMode.update ();
        });
        this.configuration.addSettingObserver (PacerConfiguration.LOOP_TRACK_START, () -> {
            if (this.running)
                this.looper.applyLoopTrackStart ();
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
            button.bind (new TapHoldCommand ( () -> this.looper.isTapOnPress (index), () -> this.looper.tap (index), () -> this.looper.hold (index), () -> this.looper.release (index), () -> this.scheduleRepaint (index), () -> this.looper.getExtraHoldMillis (index), this.host::scheduleTask).withDoubleTap ( () -> this.looper.doubleTap (index), () -> this.looper.isDoubleTapEnabled (index), () -> this.configuration.getDoubleTapWindow ().getMillis (), System::currentTimeMillis));
            button.bind (input, BindType.CC, PacerMap.MIDI_CHANNEL, PacerMap.switchCC (i));

            final SwitchLedWriter writer = new SwitchLedWriter (i, this.configuration::getLedMode, (cc, value) -> output.sendCCEx (PacerMap.MIDI_CHANNEL, cc, value));
            surface.createLight (null, () -> this.looper.getLedCode (index), writer, code -> LedColour.fromCode (code).getColorEx (), button);

            this.switches[i] = button;
            this.ledWriters[i] = writer;
        }

        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
        {
            final int index = i;
            final IHwButton button = surface.createButton (ButtonID.get (ButtonID.FOOTSWITCH1, i), "FS " + (i + 1));
            button.bind (new TapHoldCommand ( () -> this.looper.isFootswitchTapOnPress (index), () -> this.looper.footswitchTap (index), () -> this.looper.footswitchHold (index), null, null, () -> this.looper.getFootswitchExtraHoldMillis (index), this.host::scheduleTask).withDoubleTap ( () -> this.looper.footswitchDoubleTap (index), () -> this.looper.isFootswitchDoubleTapEnabled (index), () -> this.configuration.getDoubleTapWindow ().getMillis (), System::currentTimeMillis));
            button.bind (input, BindType.CC, PacerMap.MIDI_CHANNEL, PacerMap.FOOTSWITCH_CC_BASE + i);
        }

        final IHwButton presetLoaded = surface.createButton (ButtonID.F1, "Preset loaded");
        presetLoaded.bind ( (event, velocity) -> {
            if (event == ButtonEvent.DOWN)
                surface.forceFlush ();
        });
        presetLoaded.bind (input, BindType.CC, PacerMap.MIDI_CHANNEL, PacerMap.PRESET_LOADED_CC);
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
            pedal.bind (surface.getMidiInput (), BindType.CC, PacerMap.MIDI_CHANNEL, i == 0 ? PacerMap.EXP1_CC : PacerMap.EXP2_CC);
            // Used whenever no parameter is bound directly: MIDI targets, response curves and ranges
            pedal.bind ((ContinuousCommand) value -> this.looper.pedalMoved (index, value));
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
        this.looper.tick ();
        this.dawMode.flushLeds ();
        this.requestFlush.run ();
        this.host.scheduleTask (this::tick, TICK_MS);
    }


    private void bindPedal (final int index)
    {
        final IHwFader pedal = this.pedals[index];
        if (pedal != null)
            // Null (no parameter) routes the pedal to its command
            pedal.bind (this.looper.getPedalBinding (index));
    }


    private void scheduleRepaint (final int switchIndex)
    {
        if (this.configuration.getLedMode () != LedMode.MULTI_COLOUR)
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
