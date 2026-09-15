// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.daw;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;

import java.util.Arrays;
import java.util.function.BooleanSupplier;


/**
 * Serves the Pacer's built-in Track and Transport DAW presets on USB port 2, like Nektar's own script does, so that
 * script can stay disabled while PACER Looper owns port 1. Opt-in via the settings.
 */
public class DawModeController
{
    /** MIDI channel 16. */
    private static final int     CHANNEL              = 15;
    /** Held functions start repeating after this (same as Nektar's script). */
    private static final long    REPEAT_DELAY_MS      = 1000;
    private static final long    REPEAT_INTERVAL_MS   = 100;
    /** Slot colours while a function is off / on: dim white / white. */
    private static final int     COLOUR_OFF           = 0x18;
    private static final int     COLOUR_ON            = 0x17;

    private final IHost          host;
    private final IModel         model;
    private final IMidiOutput    output;
    private final BooleanSupplier enabled;

    private final int []         sentLeds             = new int [128];
    private final boolean []     held                 = new boolean [128];
    private final int []         holdGenerations      = new int [128];
    private boolean              connected;


    /**
     * Constructor.
     *
     * @param host The host
     * @param model The model
     * @param input The MIDI input of USB port 2
     * @param output The MIDI output of USB port 2
     * @param enabled The DAW mode setting
     */
    public DawModeController (final IHost host, final IModel model, final IMidiInput input, final IMidiOutput output, final BooleanSupplier enabled)
    {
        this.host = host;
        this.model = model;
        this.output = output;
        this.enabled = enabled;

        Arrays.fill (this.sentLeds, -1);
        input.setMidiCallback (this::handleMidi);
        input.setSysexCallback (this::handleSysex);
    }


    /**
     * Connect or disconnect DAW mode on the Pacer to match the setting.
     */
    public void update ()
    {
        final boolean on = this.enabled.getAsBoolean ();
        if (on == this.connected)
            return;
        this.connected = on;
        this.output.sendSysex (on ? DawModeSysex.ENABLE : DawModeSysex.DISABLE);
        Arrays.fill (this.sentLeds, -1);
        Arrays.fill (this.held, false);
    }


    /**
     * Tell the Pacer the DAW is gone.
     */
    public void shutdown ()
    {
        if (!this.connected)
            return;
        this.connected = false;
        this.output.sendSysex (DawModeSysex.DISABLE);
    }


    /**
     * Send the LED states that changed. Called on every tick.
     */
    public void flushLeds ()
    {
        if (!this.connected)
            return;

        final ITransport transport = this.model.getTransport ();
        final ICursorTrack track = this.model.getCursorTrack ();
        this.setLed (DawFunction.PLAY, transport.isPlaying ());
        this.setLed (DawFunction.STOP, !transport.isPlaying ());
        this.setLed (DawFunction.RECORD, transport.isRecording ());
        this.setLed (DawFunction.LOOP, transport.isLoop ());
        this.setLed (DawFunction.METRONOME, transport.isMetronomeOn ());
        this.setLed (DawFunction.PRE_ROLL, transport.getPrerollMeasures () > 0);
        this.setLed (DawFunction.OVERDUB, transport.isArrangerOverdub ());
        this.setLed (DawFunction.MUTE, track.isMute ());
        this.setLed (DawFunction.SOLO, track.isSolo ());
        this.setLed (DawFunction.ARM, track.isRecArm ());
        for (final DawFunction function: DawFunction.values ())
            if (function.getKind () == DawFunction.Kind.HELD)
                this.setLed (function, this.held[function.getCC ()]);
    }


    private void setLed (final DawFunction function, final boolean on)
    {
        final int value = on ? 127 : 0;
        if (this.sentLeds[function.getCC ()] == value)
            return;
        this.sentLeds[function.getCC ()] = value;
        this.output.sendCCEx (CHANNEL, function.getCC (), value);
    }


    private void handleMidi (final int status, final int data1, final int data2)
    {
        if (!this.connected || status != (0xB0 | CHANNEL))
            return;
        final DawFunction function = DawFunction.fromCC (data1);
        if (function == null)
            return;

        switch (function.getKind ())
        {
            case PRESS -> {
                if (data2 > 0)
                    this.perform (function);
            }
            case HELD -> {
                final boolean down = data2 > 0;
                this.held[data1] = down;
                final int generation = ++this.holdGenerations[data1];
                if (down)
                {
                    this.perform (function);
                    this.host.scheduleTask ( () -> this.repeat (function, generation), REPEAT_DELAY_MS);
                }
            }
            case ABSOLUTE -> {
                if (function == DawFunction.TRACK_VOLUME)
                    this.model.getCursorTrack ().setVolume (data2);
                else
                    this.model.getMasterTrack ().setVolume (data2);
            }
            case RELATIVE -> {
                if (function == DawFunction.TRACK_VOLUME_RELATIVE)
                    this.model.getCursorTrack ().changeVolume (data2);
                else
                    this.model.getMasterTrack ().changeVolume (data2);
            }
        }
    }


    private void repeat (final DawFunction function, final int generation)
    {
        final int cc = function.getCC ();
        if (!this.connected || !this.held[cc] || this.holdGenerations[cc] != generation)
            return;
        this.perform (function);
        this.host.scheduleTask ( () -> this.repeat (function, generation), REPEAT_INTERVAL_MS);
    }


    private void handleSysex (final String data)
    {
        if (!this.connected || !DawModeSysex.isFunctionReport (data))
            return;

        // A DAW preset became active: colour its slots, then repaint every state
        final int [] off = new int [DawModeSysex.NUM_SLOTS];
        final int [] on = new int [DawModeSysex.NUM_SLOTS];
        Arrays.fill (off, COLOUR_OFF);
        Arrays.fill (on, COLOUR_ON);
        this.output.sendSysex (DawModeSysex.slotColours (off, on));
        Arrays.fill (this.sentLeds, -1);
    }


    private void perform (final DawFunction function)
    {
        final ITransport transport = this.model.getTransport ();
        final ICursorTrack track = this.model.getCursorTrack ();
        switch (function)
        {
            case PLAY -> {
                if (transport.isPlaying ())
                    transport.stop ();
                else
                    transport.play ();
            }
            case STOP -> transport.stop ();
            case RECORD -> transport.startRecording ();
            case LOOP -> transport.toggleLoop ();
            case REWIND -> transport.changePosition (false, false);
            case FAST_FORWARD -> transport.changePosition (true, false);
            case METRONOME -> transport.toggleMetronome ();
            case PRE_ROLL -> transport.setPrerollMeasures (transport.getPrerollMeasures () == 0 ? 1 : 0);
            case OVERDUB -> transport.toggleOverdub ();
            case GOTO_LOOP_START -> {
                if (transport.isPlaying ())
                    transport.stop ();
                transport.setPosition (transport.getLoopStart ());
            }
            case MUTE -> track.toggleMute ();
            case SOLO -> track.toggleSolo ();
            case ARM -> track.toggleRecArm ();
            case UNDO -> this.model.getApplication ().undo ();
            case TRACK_PREVIOUS -> track.selectPrevious ();
            case TRACK_NEXT -> track.selectNext ();
            case PATCH_PREVIOUS -> this.model.getCursorDevice ().selectPrevious ();
            case PATCH_NEXT -> this.model.getCursorDevice ().selectNext ();
            case LOOP_LEFT, LOOP_RIGHT -> this.host.println ("DAW mode: moving the loop region is not supported yet");
            default -> {
                // Volumes are handled in handleMidi
            }
        }
    }
}
