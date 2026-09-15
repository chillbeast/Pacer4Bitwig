// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.constants.LaunchQuantization;
import de.mossgrabers.framework.daw.constants.PostRecordingAction;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IScene;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISceneBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.parameter.IParameter;

import dev.pacer4bitwig.pacer.clock.BeatClock;
import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.led.LedClock;
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedMode;
import dev.pacer4bitwig.pacer.led.LedPattern;
import dev.pacer4bitwig.pacer.led.LedState;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.looper.CountIn;
import dev.pacer4bitwig.pacer.looper.ExpressionTarget;
import dev.pacer4bitwig.pacer.looper.LayerPlanner;
import dev.pacer4bitwig.pacer.looper.LoopAction;
import dev.pacer4bitwig.pacer.looper.LoopLeds;
import dev.pacer4bitwig.pacer.looper.LoopLength;
import dev.pacer4bitwig.pacer.looper.LoopLengthTracker;
import dev.pacer4bitwig.pacer.looper.LoopState;
import dev.pacer4bitwig.pacer.looper.TapTiming;
import dev.pacer4bitwig.pacer.midi.RawMidiSender;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * All looper behaviour: what switches, jacks and pedals do and what the LEDs show. The setup only wires hardware
 * to these methods; {@link #tick()} runs every few tens of milliseconds for everything that watches state.
 * <p>
 * Each loop switch controls the slot of the track with the same index in the current scene row (the track bank is
 * one scene high, so scrolling its scene bank moves the row for every loop).
 */
public class LooperController
{
    private static final long            NOTIFY_DELAY_MS       = 150;
    /** Bitwig reports a changed arm state asynchronously; do not trust "not armed" before this. */
    private static final long            ARM_SETTLE_MS         = 500;
    /** Give up a count-in if the transport has not started by then. */
    private static final long            COUNT_IN_START_MS     = 2000;
    private static final long            LED_TEST_STEP_MS      = 1000;
    private static final int             RECORD_HISTORY        = 64;
    private static final int             BEATS_PER_BAR_4_4     = 4;

    private final IHost                  host;
    private final IModel                 model;
    private final PacerConfiguration     configuration;
    private final BeatClock              clock;
    private RawMidiSender                midiSender            = RawMidiSender.NONE;

    /** Track bank positions the looper armed itself, with the time it did so. */
    private final Map<Integer, Long>     armedByLooper         = new HashMap<> ();
    private int                          lastArmedIndex        = -1;
    /** Recordings in order: {scene row, track bank position}. */
    private final Deque<int []>          recordHistory         = new ArrayDeque<> ();
    private final LoopLengthTracker      lengthTracker         = new LoopLengthTracker (PacerMap.MAX_LOOP_TRACKS);
    private int                          matchedBars;
    private PendingCountIn               pendingCountIn;
    private long                         ledTestStartedAt      = -1;


    /** A recording waiting for its count-in. */
    private static final class PendingCountIn
    {
        private final int     trackIndex;
        private final CountIn countIn;
        private final long    requestedAt;
        private final boolean turnedMetronomeOn;
        private double        recordAtBeats = Double.NaN;


        PendingCountIn (final int trackIndex, final CountIn countIn, final boolean turnedMetronomeOn)
        {
            this.trackIndex = trackIndex;
            this.countIn = countIn;
            this.turnedMetronomeOn = turnedMetronomeOn;
            this.requestedAt = System.currentTimeMillis ();
        }
    }


    /**
     * Constructor.
     *
     * @param host The host
     * @param model The model
     * @param configuration The configuration
     * @param clock The transport clock
     */
    public LooperController (final IHost host, final IModel model, final PacerConfiguration configuration, final BeatClock clock)
    {
        this.host = host;
        this.model = model;
        this.configuration = configuration;
        this.clock = clock;
    }


    /**
     * @param midiSender Sends pedal MIDI into Bitwig
     */
    public void setMidiSender (final RawMidiSender midiSender)
    {
        this.midiSender = midiSender;
    }


    // ---- Hardware entry points ----------------------------------------------------------------------------------

    /**
     * @param switchIndex 0-9
     * @return True if the switch fires its tap on press
     */
    public boolean isTapOnPress (final int switchIndex)
    {
        if (this.isLoopSwitch (switchIndex))
            return TapTiming.loopTapOnPress (this.configuration.isLoopOnPress (), this.configuration.getLoopHoldAction ());
        return TapTiming.actionTapOnPress (this.configuration.getSwitchTap (switchIndex), this.configuration.getSwitchHold (switchIndex));
    }


    /**
     * A switch was tapped.
     *
     * @param switchIndex 0-9
     */
    public void tap (final int switchIndex)
    {
        if (this.isLoopSwitch (switchIndex))
            this.loopTap (this.getTrackBank ().getItem (switchIndex));
        else
            this.perform (this.configuration.getSwitchTap (switchIndex));
    }


    /**
     * A switch was held.
     *
     * @param switchIndex 0-9
     */
    public void hold (final int switchIndex)
    {
        if (this.isLoopSwitch (switchIndex))
            this.loopHold (this.getTrackBank ().getItem (switchIndex));
        else
            this.perform (this.configuration.getSwitchHold (switchIndex));
    }


    /**
     * @param index 0-3
     * @return True if the footswitch jack fires its tap on press
     */
    public boolean isFootswitchTapOnPress (final int index)
    {
        return TapTiming.actionTapOnPress (this.configuration.getFootswitchTap (index), this.configuration.getFootswitchHold (index));
    }


    /**
     * A footswitch jack was tapped.
     *
     * @param index 0-3
     */
    public void footswitchTap (final int index)
    {
        this.perform (this.configuration.getFootswitchTap (index));
    }


    /**
     * A footswitch jack was held.
     *
     * @param index 0-3
     */
    public void footswitchHold (final int index)
    {
        this.perform (this.configuration.getFootswitchHold (index));
    }


    /**
     * An expression pedal moved while it is set to a MIDI target.
     *
     * @param index 0-1
     * @param value The pedal position, 0-127
     */
    public void pedalMoved (final int index, final int value)
    {
        final int [] message = this.configuration.getExpressionTarget (index).toMidi (value, this.configuration.getPedalMidiChannel ());
        if (message != null)
            this.midiSender.send (message[0], message[1], message[2]);
    }


    /**
     * Get the light code of a switch right now.
     *
     * @param switchIndex 0-9
     * @return The code, see {@link LedState#code(LedClock)}
     */
    public int getLedCode (final int switchIndex)
    {
        final long now = System.currentTimeMillis ();
        if (this.ledTestStartedAt >= 0)
        {
            final long step = (now - this.ledTestStartedAt) / LED_TEST_STEP_MS;
            if (step < LedColour.values ().length - 1)
                return LedColour.WHITE.ordinal () + (int) step;
            this.ledTestStartedAt = -1;
        }

        final LedClock ledClock = this.getLedClock (now);
        final LedState state;
        if (this.isLoopSwitch (switchIndex))
            state = this.loopLed (this.getTrackBank ().getItem (switchIndex));
        else
            state = this.actionLed (this.configuration.getSwitchTap (switchIndex), ledClock);
        return state.code (ledClock);
    }


    /**
     * @param target The expression pedal target
     * @return The parameter to bind, null for none or for MIDI targets
     */
    public IParameter getExpressionParameter (final ExpressionTarget target)
    {
        final ICursorTrack cursorTrack = this.model.getCursorTrack ();
        return switch (target)
        {
            case SELECTED_VOLUME -> cursorTrack.getVolumeParameter ();
            case SELECTED_PAN -> cursorTrack.getPanParameter ();
            case SELECTED_SEND_1 -> cursorTrack.getSendBank ().getItem (0);
            case SELECTED_SEND_2 -> cursorTrack.getSendBank ().getItem (1);
            case MASTER_VOLUME -> this.model.getMasterTrack ().getVolumeParameter ();
            case DEVICE_REMOTE_1 -> this.model.getCursorDevice ().getParameterBank ().getItem (0);
            case DEVICE_REMOTE_2 -> this.model.getCursorDevice ().getParameterBank ().getItem (1);
            case PROJECT_REMOTE_1 -> this.model.getProject ().getParameterBank ().getItem (0);
            case PROJECT_REMOTE_2 -> this.model.getProject ().getParameterBank ().getItem (1);
            default -> null;
        };
    }


    /**
     * Cycle every LED through all colours, one second each.
     */
    public void startLedTest ()
    {
        this.ledTestStartedAt = System.currentTimeMillis ();
        this.notify ("LED test: white, red, green, amber, blue, purple");
    }


    /**
     * Runs on every tick: count-ins, loop length matching, exclusive arm.
     */
    public void tick ()
    {
        this.updateCountIn ();
        this.updateLoopLengths ();
        this.enforceExclusiveArm ();
    }


    /**
     * Push the launch quantization setting into the project.
     */
    public void applyLaunchQuantization ()
    {
        final LaunchQuantization quantization = this.configuration.getLaunchQuantization ().getQuantization ();
        if (quantization != null)
            this.model.getTransport ().setDefaultLaunchQuantization (quantization);
    }


    /**
     * Push the loop length setting into the project.
     */
    public void applyLoopLength ()
    {
        final LoopLength length = this.configuration.getLoopLength ();
        final ITransport transport = this.model.getTransport ();
        switch (length)
        {
            case KEEP -> {
                // Leave the project alone
            }
            case FREE -> transport.setClipLauncherPostRecordingAction (PostRecordingAction.OFF);
            case MATCH_FIRST -> {
                if (this.matchedBars == 0)
                    transport.setClipLauncherPostRecordingAction (PostRecordingAction.OFF);
                else
                    this.setFixedLength (this.matchedBars, this.clock.getBeatsPerBar ());
            }
            default -> this.setFixedLength (length.getBars (), BEATS_PER_BAR_4_4);
        }
    }


    private void setFixedLength (final int bars, final double beatsPerBar)
    {
        final ITransport transport = this.model.getTransport ();
        transport.setClipLauncherPostRecordingTimeOffset (bars * beatsPerBar);
        transport.setClipLauncherPostRecordingAction (PostRecordingAction.PLAY_RECORDED);
    }


    // ---- Actions ------------------------------------------------------------------------------------------------

    /**
     * Run an assignable action.
     *
     * @param action The action
     */
    public void perform (final Action action)
    {
        final ITransport transport = this.model.getTransport ();
        switch (action)
        {
            case NONE -> {
                // Nothing
            }
            case RECORD_NEXT_LAYER -> this.recordNextLayer ();
            case CLEAR_LAST_LOOP -> this.clearLastLoop ();
            case LOOP_SELECTED -> this.withSelectedTrack (this::loopTap);
            case STOP_SELECTED -> this.withSelectedTrack (track -> track.stop (false));
            case MUTE_SELECTED -> this.withSelectedTrack (ITrack::toggleMute);
            case CLEAR_SELECTED -> this.withSelectedTrack (this::clearLoop);
            case SELECT_PREVIOUS_LOOP -> this.selectLoop (-1);
            case SELECT_NEXT_LOOP -> this.selectLoop (1);
            case PLAY_STOP_ALL -> {
                if (this.anyLoop (true))
                    this.stopAll ();
                else
                    this.playRow ();
            }
            case STOP_ALL -> this.stopAll ();
            case PLAY_ROW -> this.playRow ();
            case CLEAR_ROW -> this.clearRow ();
            case ROW_PREVIOUS -> this.scrollRows (false);
            case ROW_NEXT -> this.scrollRows (true);
            case TRACKS_LEFT -> this.scrollTracks (false);
            case TRACKS_RIGHT -> this.scrollTracks (true);
            case UNDO -> {
                this.model.getApplication ().undo ();
                this.notify ("Undo");
            }
            case REDO -> {
                this.model.getApplication ().redo ();
                this.notify ("Redo");
            }
            case LAUNCHER_OVERDUB -> {
                // The observed value still has the old state
                this.notify ("Launcher overdub " + (transport.isLauncherOverdub () ? "off" : "on"));
                transport.toggleLauncherOverdub ();
            }
            case METRONOME -> {
                this.notify ("Metronome " + (transport.isMetronomeOn () ? "off" : "on"));
                transport.toggleMetronome ();
            }
            case TAP_TEMPO -> transport.tapTempo ();
            case TRANSPORT_PLAY_STOP -> {
                if (transport.isPlaying ())
                    transport.stop ();
                else
                    transport.play ();
            }
            case LED_TEST -> this.startLedTest ();
        }
    }


    private LedState actionLed (final Action action, final LedClock ledClock)
    {
        final ITransport transport = this.model.getTransport ();
        final ITrackBank trackBank = this.getTrackBank ();
        final Optional<ITrack> selected = this.getSelectedTrack ();

        return switch (action)
        {
            case NONE -> LedState.DARK;
            case RECORD_NEXT_LAYER -> this.layerLed ();
            case CLEAR_LAST_LOOP -> LedState.when (this.anyLoop (false), LedColour.RED);
            case LOOP_SELECTED -> selected.map (this::loopLed).orElse (LedState.DARK);
            case STOP_SELECTED -> LedState.when (selected.isPresent () && selected.get ().isPlaying (), LedColour.WHITE);
            case MUTE_SELECTED -> LedState.when (selected.isPresent () && selected.get ().isMute (), LedColour.BLUE);
            case CLEAR_SELECTED -> LedState.when (selected.isPresent () && this.getLoopSlot (selected.get ()).hasContent (), LedColour.RED);
            case SELECT_PREVIOUS_LOOP, SELECT_NEXT_LOOP, LED_TEST -> LedState.solid (LedColour.WHITE);
            case PLAY_STOP_ALL -> {
                if (this.anyLoop (true))
                    yield LedState.solid (LedColour.GREEN);
                yield LedState.when (this.anyLoop (false), LedColour.AMBER);
            }
            case STOP_ALL -> LedState.when (this.anyLoop (true), LedColour.WHITE);
            case PLAY_ROW -> LedState.when (this.anyLoop (false), LedColour.GREEN);
            case CLEAR_ROW -> LedState.when (this.anyLoop (false), LedColour.RED);
            case ROW_PREVIOUS -> LedState.when (trackBank.getSceneBank ().canScrollBackwards (), LedColour.WHITE);
            case ROW_NEXT -> LedState.solid (trackBank.getSceneBank ().canScrollForwards () ? LedColour.WHITE : LedColour.BLUE);
            case TRACKS_LEFT -> LedState.when (trackBank.canScrollBackwards (), LedColour.WHITE);
            case TRACKS_RIGHT -> LedState.when (trackBank.canScrollForwards (), LedColour.WHITE);
            case UNDO -> LedState.when (this.model.getApplication ().canUndo (), LedColour.WHITE);
            case REDO -> LedState.when (this.model.getApplication ().canRedo (), LedColour.WHITE);
            case LAUNCHER_OVERDUB -> LedState.when (transport.isLauncherOverdub (), LedColour.RED);
            case METRONOME -> LedState.when (transport.isMetronomeOn (), LedColour.WHITE);
            case TAP_TEMPO -> {
                // A visual metronome: white on the downbeat, green on the other beats
                if (ledClock.synced ())
                    yield new LedState (ledClock.beatInBar () == 0 ? LedColour.WHITE : LedColour.GREEN, LedPattern.BEAT_FLASH);
                yield LedState.when (transport.isPlaying (), LedColour.GREEN);
            }
            case TRANSPORT_PLAY_STOP -> LedState.when (transport.isPlaying (), LedColour.GREEN);
        };
    }


    // ---- Loops --------------------------------------------------------------------------------------------------

    private void loopTap (final ITrack track)
    {
        if (!track.doesExist ())
        {
            this.notify ("No track for this loop switch");
            return;
        }

        if (this.pendingCountIn != null && this.pendingCountIn.trackIndex == track.getIndex ())
        {
            this.cancelCountIn ();
            return;
        }

        final ISlot slot = this.getLoopSlot (track);
        final LoopAction action = LoopAction.onTap (LoopState.of (slot), this.configuration.getPlayingTapAction ());
        if (this.configuration.isSelectOnPress ())
            track.select ();

        switch (action)
        {
            case RECORD -> this.startRecording (track);
            case PLAY -> this.playLoop (track);
            case STOP -> track.stop (false);
            case TOGGLE_MUTE -> track.toggleMute ();
            case OVERDUB -> {
                this.armForRecording (track);
                this.model.getTransport ().toggleLauncherOverdub ();
            }
            case NONE -> {
                // Configured to ignore
            }
        }
    }


    private void loopHold (final ITrack track)
    {
        if (!track.doesExist ())
            return;

        switch (this.configuration.getLoopHoldAction ())
        {
            case DELETE -> this.clearLoop (track);
            case STOP -> track.stop (false);
            case NOTHING -> {
                // Configured to ignore
            }
        }
    }


    private LedState loopLed (final ITrack track)
    {
        if (!track.doesExist ())
            return LedState.DARK;
        final LoopState state = this.getLoopState (track);
        final boolean overdubbing = state == LoopState.PLAYING && this.model.getTransport ().isLauncherOverdub () && track.isRecArm ();
        return LoopLeds.forLoop (state, overdubbing, track.isMute (), this.configuration.getLedMode ());
    }


    /** The slot state, with a pending count-in shown as a queued recording. */
    private LoopState getLoopState (final ITrack track)
    {
        if (this.pendingCountIn != null && this.pendingCountIn.trackIndex == track.getIndex ())
            return LoopState.RECORD_QUEUED;
        return LoopState.of (this.getLoopSlot (track));
    }


    /** Record now, or after the count-in if the transport is stopped. */
    private void startRecording (final ITrack track)
    {
        final ITransport transport = this.model.getTransport ();
        final CountIn countIn = this.configuration.getCountIn ();
        this.armForRecording (track);

        if (countIn == CountIn.OFF || transport.isPlaying ())
        {
            this.recordNow (track);
            return;
        }

        if (this.pendingCountIn != null)
            this.cancelCountIn ();
        final boolean turnMetronomeOn = !transport.isMetronomeOn ();
        if (turnMetronomeOn)
            transport.setMetronome (true);
        this.pendingCountIn = new PendingCountIn (track.getIndex (), countIn, turnMetronomeOn);
        transport.play ();
        this.notify ("Count-in: " + countIn.getLabel ());
    }


    private void recordNow (final ITrack track)
    {
        this.getLoopSlot (track).startRecording ();
        this.recordHistory.push (new int []
        {
            this.getRow (),
            track.getIndex ()
        });
        while (this.recordHistory.size () > RECORD_HISTORY)
            this.recordHistory.removeLast ();
    }


    private void playLoop (final ITrack track)
    {
        // Asking for a loop to play means wanting to hear it
        if (track.isMute ())
            track.setMute (false);
        final ISlot slot = this.getLoopSlot (track);
        slot.launch (true, false);
        slot.launch (false, false);
    }


    private void updateCountIn ()
    {
        final PendingCountIn pending = this.pendingCountIn;
        if (pending == null)
            return;

        if (!this.clock.isPlaying ())
        {
            if (System.currentTimeMillis () - pending.requestedAt > COUNT_IN_START_MS)
                this.cancelCountIn ();
            return;
        }

        final double position = this.clock.getPositionInBeats ();
        if (Double.isNaN (pending.recordAtBeats))
            pending.recordAtBeats = pending.countIn.recordAtBeats (position, this.clock.getBeatsPerBar ());
        if (position < pending.recordAtBeats)
            return;

        this.pendingCountIn = null;
        final ITrack track = this.getTrackBank ().getItem (pending.trackIndex);
        if (track.doesExist ())
            this.recordNow (track);
        if (pending.turnedMetronomeOn)
            this.model.getTransport ().setMetronome (false);
    }


    private void cancelCountIn ()
    {
        final PendingCountIn pending = this.pendingCountIn;
        if (pending == null)
            return;
        this.pendingCountIn = null;
        final ITransport transport = this.model.getTransport ();
        if (pending.turnedMetronomeOn)
            transport.setMetronome (false);
        if (!this.anyLoop (true))
            transport.stop ();
        this.notify ("Count-in cancelled");
    }


    /** Watch recordings close: the first loop of a row can set the length of the others. */
    private void updateLoopLengths ()
    {
        final ITrackBank trackBank = this.getTrackBank ();
        final boolean running = this.clock.isPlaying ();
        final double position = this.clock.getPositionInBeats ();
        final double beatsPerBar = this.clock.getBeatsPerBar ();
        final boolean matching = this.configuration.getLoopLength () == LoopLength.MATCH_FIRST;

        boolean rowEmpty = this.pendingCountIn == null;
        for (int i = 0; i < this.getLoopCount (); i++)
        {
            final ITrack track = trackBank.getItem (i);
            if (!track.doesExist ())
                continue;
            final LoopState state = LoopState.of (this.getLoopSlot (track));
            if (state != LoopState.EMPTY)
                rowEmpty = false;

            final int bars = this.lengthTracker.update (i, state, running, position, beatsPerBar);
            if (bars > 0 && matching && this.matchedBars == 0)
            {
                this.matchedBars = bars;
                this.applyLoopLength ();
                this.notify ("Loops in this row are now " + bars + (bars == 1 ? " bar" : " bars") + " long");
            }
        }

        // A fresh row starts free again
        if (rowEmpty && this.matchedBars != 0)
        {
            this.matchedBars = 0;
            if (matching)
                this.applyLoopLength ();
        }
    }


    private void recordNextLayer ()
    {
        if (this.pendingCountIn != null)
        {
            this.cancelCountIn ();
            return;
        }

        final LayerPlanner.Step step = LayerPlanner.next (this.getRowStates ());
        final ITrackBank trackBank = this.getTrackBank ();
        switch (step.kind ())
        {
            case RECORD -> {
                final ITrack track = trackBank.getItem (step.index ());
                if (this.configuration.isSelectOnPress ())
                    track.select ();
                this.startRecording (track);
            }
            case CLOSE -> this.playLoop (trackBank.getItem (step.index ()));
            case CANCEL -> trackBank.getItem (step.index ()).stop (false);
            case FULL -> this.notify ("Every loop track in this row has a loop");
        }
    }


    private LedState layerLed ()
    {
        final LoopState [] states = this.getRowStates ();
        final int focus = LayerPlanner.focus (states);
        if (focus >= 0)
            return this.loopLed (this.getTrackBank ().getItem (focus));
        final LedMode mode = this.configuration.getLedMode ();
        if (this.anyLoop (true))
            return LoopLeds.forLoop (LoopState.PLAYING, false, false, mode);
        if (this.anyLoop (false))
            return LoopLeds.forLoop (LoopState.STOPPED, false, false, mode);
        return LedState.DARK;
    }


    private void clearLastLoop ()
    {
        final ITrackBank trackBank = this.getTrackBank ();
        final int row = this.getRow ();
        while (!this.recordHistory.isEmpty ())
        {
            final int [] entry = this.recordHistory.pop ();
            if (entry[0] != row || entry[1] >= this.getLoopCount ())
                continue;
            final ITrack track = trackBank.getItem (entry[1]);
            if (track.doesExist () && LoopState.of (this.getLoopSlot (track)) != LoopState.EMPTY)
            {
                this.clearLoop (track);
                return;
            }
        }
        this.notify ("No recorded loop left to clear in this row");
    }


    private void armForRecording (final ITrack track)
    {
        final int index = track.getIndex ();
        this.lastArmedIndex = index;
        if (!this.configuration.isArmOnRecord () || track.isRecArm ())
            return;
        track.setRecArm (true);
        this.armedByLooper.put (Integer.valueOf (index), Long.valueOf (System.currentTimeMillis ()));
    }


    /** Disarm loop tracks the looper armed once they are done recording, so only one input monitor stays open. */
    private void enforceExclusiveArm ()
    {
        if (this.armedByLooper.isEmpty ())
            return;

        final long now = System.currentTimeMillis ();
        final ITrackBank trackBank = this.getTrackBank ();
        final Iterator<Map.Entry<Integer, Long>> it = this.armedByLooper.entrySet ().iterator ();
        while (it.hasNext ())
        {
            final Map.Entry<Integer, Long> entry = it.next ();
            final int index = entry.getKey ().intValue ();
            final ITrack track = trackBank.getItem (index);
            final boolean settled = now - entry.getValue ().longValue () > ARM_SETTLE_MS;

            if (!track.doesExist () || settled && !track.isRecArm ())
            {
                // Gone, or disarmed by the user
                it.remove ();
                continue;
            }
            if (!this.configuration.isExclusiveArm () || index == this.lastArmedIndex || !settled)
                continue;

            final LoopState state = LoopState.of (this.getLoopSlot (track));
            if (state == LoopState.RECORDING || state == LoopState.RECORD_QUEUED)
                continue;

            track.setRecArm (false);
            it.remove ();
        }
    }


    private void clearLoop (final ITrack track)
    {
        if (this.pendingCountIn != null && this.pendingCountIn.trackIndex == track.getIndex ())
            this.cancelCountIn ();
        track.stop (false);
        this.getLoopSlot (track).remove ();
        this.notify (track.getName () + ": loop cleared");
    }


    private void stopAll ()
    {
        if (this.pendingCountIn != null)
            this.cancelCountIn ();
        this.getTrackBank ().stop (false);
        this.notify ("Stop all loops");
    }


    private void playRow ()
    {
        final ISceneBank sceneBank = this.getTrackBank ().getSceneBank ();
        final IScene scene = sceneBank.getItem (0);
        scene.launch (true, false);
        scene.launch (false, false);
        this.notify ("Play row " + (sceneBank.getScrollPosition () + 1));
    }


    private void clearRow ()
    {
        if (this.pendingCountIn != null)
            this.cancelCountIn ();
        final ITrackBank trackBank = this.getTrackBank ();
        for (int i = 0; i < this.getLoopCount (); i++)
        {
            final ITrack track = trackBank.getItem (i);
            if (!track.doesExist ())
                continue;
            track.stop (false);
            this.getLoopSlot (track).remove ();
        }
        this.notify ("All loops in this row cleared");
    }


    private void scrollRows (final boolean forwards)
    {
        final ISceneBank sceneBank = this.getTrackBank ().getSceneBank ();
        if (!forwards)
        {
            if (sceneBank.canScrollBackwards ())
                sceneBank.scrollBackwards ();
        }
        else if (sceneBank.canScrollForwards ())
            sceneBank.scrollForwards ();
        else
        {
            // Past the last scene: add one so there is always a fresh row to loop into
            this.model.getProject ().createScene ();
            this.host.scheduleTask (sceneBank::scrollForwards, 100);
        }
        this.host.scheduleTask ( () -> this.notify ("Row " + (sceneBank.getScrollPosition () + 1)), NOTIFY_DELAY_MS);
    }


    private void scrollTracks (final boolean forwards)
    {
        final ITrackBank trackBank = this.getTrackBank ();
        if (forwards)
            trackBank.scrollForwards ();
        else
            trackBank.scrollBackwards ();
        // Bank positions now point at other tracks
        this.armedByLooper.clear ();
        this.lastArmedIndex = -1;
        this.recordHistory.clear ();
        this.lengthTracker.reset ();
        this.host.scheduleTask ( () -> this.notify ("Loop tracks start at " + trackBank.getItem (0).getName ()), NOTIFY_DELAY_MS);
    }


    private void selectLoop (final int delta)
    {
        final int loops = this.getLoopCount ();
        final Optional<ITrack> selected = this.getSelectedTrack ();
        final int current = selected.isPresent () && selected.get ().getIndex () < loops ? selected.get ().getIndex () : -1;
        final int next = current < 0 ? (delta > 0 ? 0 : loops - 1) : Math.floorMod (current + delta, loops);
        final ITrack track = this.getTrackBank ().getItem (next);
        if (!track.doesExist ())
            return;
        track.select ();
        this.notify ("Loop " + (next + 1) + ": " + track.getName ());
    }


    private void withSelectedTrack (final Consumer<ITrack> consumer)
    {
        final Optional<ITrack> selected = this.getSelectedTrack ();
        if (selected.isPresent ())
            consumer.accept (selected.get ());
        else
            this.notify ("Select one of the loop tracks first");
    }


    /**
     * Is any loop track of the current layout playing (or, with playing = false, holding a clip in the row)?
     */
    private boolean anyLoop (final boolean playing)
    {
        final ITrackBank trackBank = this.getTrackBank ();
        for (int i = 0; i < this.getLoopCount (); i++)
        {
            final ITrack track = trackBank.getItem (i);
            if (track.doesExist () && (playing ? track.isPlaying () : this.getLoopSlot (track).hasContent ()))
                return true;
        }
        return false;
    }


    /** The loop states of the row, null for missing tracks, with a pending count-in shown as queued. */
    private LoopState [] getRowStates ()
    {
        final ITrackBank trackBank = this.getTrackBank ();
        final LoopState [] states = new LoopState [this.getLoopCount ()];
        for (int i = 0; i < states.length; i++)
        {
            final ITrack track = trackBank.getItem (i);
            states[i] = track.doesExist () ? this.getLoopState (track) : null;
        }
        return states;
    }


    // ---- Helpers ------------------------------------------------------------------------------------------------

    private LedClock getLedClock (final long now)
    {
        if (!this.configuration.isBeatSyncedLeds () || !this.clock.isPlaying ())
            return LedClock.unsynced (now);
        return new LedClock (now, true, this.clock.getPositionInBeats (), this.clock.getBeatsPerBar ());
    }


    private boolean isLoopSwitch (final int switchIndex)
    {
        return this.configuration.getSwitchLayout ().isLoopSwitch (switchIndex);
    }


    private int getLoopCount ()
    {
        return this.configuration.getSwitchLayout ().getLoopCount ();
    }


    private int getRow ()
    {
        return this.getTrackBank ().getSceneBank ().getScrollPosition ();
    }


    private Optional<ITrack> getSelectedTrack ()
    {
        return this.getTrackBank ().getSelectedItem ();
    }


    private ITrackBank getTrackBank ()
    {
        return this.model.getTrackBank ();
    }


    private ISlot getLoopSlot (final ITrack track)
    {
        return track.getSlotBank ().getItem (0);
    }


    private void notify (final String message)
    {
        if (this.configuration.isNotifications ())
            this.host.showNotification (message);
    }
}
