// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.clip.INoteClip;
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
import dev.pacer4bitwig.pacer.looper.HoldAction;
import dev.pacer4bitwig.pacer.looper.LayerPlanner;
import dev.pacer4bitwig.pacer.looper.LoopAction;
import dev.pacer4bitwig.pacer.looper.LoopColours;
import dev.pacer4bitwig.pacer.looper.LoopDoubleTap;
import dev.pacer4bitwig.pacer.looper.LoopLeds;
import dev.pacer4bitwig.pacer.looper.LoopLength;
import dev.pacer4bitwig.pacer.looper.LoopLengthTracker;
import dev.pacer4bitwig.pacer.looper.LoopState;
import dev.pacer4bitwig.pacer.looper.LoopSwitchMode;
import dev.pacer4bitwig.pacer.looper.LooperText;
import dev.pacer4bitwig.pacer.looper.MuteTiming;
import dev.pacer4bitwig.pacer.looper.TapTiming;
import dev.pacer4bitwig.pacer.looper.VolumeFade;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * Looper behaviour: loop switches, looper actions and their LEDs, parameter targets of the pedals. The
 * {@link PacerController} decides what each switch does on the active preset; {@link #tick()} runs every few tens of
 * milliseconds for everything that watches state.
 * <p>
 * The looper manages the first "loop tracks" of the track bank (1-6). Each loop switch controls the slot of the
 * track with the same number in the current scene row (the track bank is one scene high, so scrolling its scene
 * bank moves the row for every loop).
 */
public class LooperController
{
    private static final long             NOTIFY_DELAY_MS       = 150;
    /** Bitwig reports a changed arm state asynchronously; do not trust "not armed" before this. */
    private static final long             ARM_SETTLE_MS         = 500;
    /** Give up a count-in if the transport has not started by then. */
    private static final long             COUNT_IN_START_MS     = 2000;
    /** Give the launcher cursor clip time to follow a newly selected slot. */
    private static final long             CURSOR_CLIP_FOLLOW_MS = 150;
    /** Restore volumes even if the loops never report stopped. */
    private static final long             FADE_STOP_TIMEOUT_MS  = 20000;
    /** A released hold-to-record that never started recording is forgotten after this. */
    private static final long             CLOSE_WAIT_MS         = 1000;
    private static final long             LED_TEST_STEP_MS      = 1000;
    private static final int              RECORD_HISTORY        = 64;
    private static final int              BEATS_PER_BAR_4_4     = 4;
    private static final int              TOP_ROW_SIZE          = 4;

    private final IHost                   host;
    private final IModel                  model;
    private final PacerConfiguration      configuration;
    private final BeatClock               clock;

    /** Track bank positions the looper armed itself, with the time it did so. */
    private final Map<Integer, Long>      armedByLooper         = new HashMap<> ();
    private int                           lastArmedIndex        = -1;
    /** Recordings in order: {scene row, track bank position}. */
    private final Deque<int []>           recordHistory         = new ArrayDeque<> ();
    private final LoopLengthTracker       lengthTracker         = new LoopLengthTracker (PacerMap.MAX_LOOP_TRACKS);
    private int                           matchedBars;
    private PendingCountIn                pendingCountIn;
    private VolumeFade                    fade;
    private long                          fadeStopRequestedAt   = -1;
    private long                          ledTestStartedAt      = -1;
    /** Loop switches whose current press started a hold-to-record. */
    private final boolean []              holdRecording         = new boolean [PacerMap.MAX_LOOP_TRACKS];
    /** Hold-to-record released before recording started: close as soon as it does (time of the release). */
    private final long []                 closeWhenRecording    = new long [PacerMap.MAX_LOOP_TRACKS];
    /** Quantized mute changes: track bank position to {target (1 = mute), apply at beats}. */
    private final Map<Integer, double []> pendingMutes          = new HashMap<> ();


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
        Arrays.fill (this.closeWhenRecording, -1);
    }


    // ---- Loop switches --------------------------------------------------------------------------------------------

    /**
     * @return True if loop switches fire their tap on press
     */
    public boolean isLoopSwitchTapOnPress ()
    {
        return this.configuration.getLoopSwitchMode () == LoopSwitchMode.HOLD_TO_RECORD || TapTiming.loopTapOnPress (this.configuration.isLoopOnPress (), this.configuration.getLoopHoldAction ());
    }


    /**
     * @return How much longer than a normal hold a loop switch must stay down before its hold runs
     */
    public long getLoopSwitchExtraHoldMillis ()
    {
        return this.configuration.getLoopHoldAction () == HoldAction.DELETE ? this.configuration.getClearHoldTime ().getExtraMillis () : 0;
    }


    /**
     * @return True if double-tapping a loop switch does something
     */
    public boolean isLoopSwitchDoubleTapEnabled ()
    {
        return this.configuration.getLoopDoubleTap () != LoopDoubleTap.NOTHING;
    }


    /**
     * A loop switch was tapped.
     *
     * @param switchIndex 0-5
     */
    public void loopSwitchTap (final int switchIndex)
    {
        final ITrack track = this.getTrackBank ().getItem (switchIndex);
        this.holdRecording[switchIndex] = false;
        if (this.configuration.getLoopSwitchMode () == LoopSwitchMode.HOLD_TO_RECORD && track.doesExist () && this.getLoopState (track) == LoopState.EMPTY)
        {
            if (this.configuration.isSelectOnPress ())
                track.select ();
            this.startRecording (track);
            this.holdRecording[switchIndex] = true;
            return;
        }
        this.loopTap (track);
    }


    /**
     * A loop switch was double-tapped (the first tap has already run).
     *
     * @param switchIndex 0-5
     */
    public void loopSwitchDoubleTap (final int switchIndex)
    {
        final ITrack track = this.getTrackBank ().getItem (switchIndex);
        if (!track.doesExist ())
            return;
        switch (this.configuration.getLoopDoubleTap ())
        {
            case STOP -> track.stop (false);
            case MUTE -> this.requestMute (track, !this.isEffectivelyMuted (track));
            case CLEAR -> {
                this.holdRecording[switchIndex] = false;
                this.closeWhenRecording[switchIndex] = -1;
                this.clearLoop (track);
            }
            case UNDO -> this.perform (Action.UNDO);
            case NOTHING -> this.loopSwitchTap (switchIndex);
        }
    }


    /**
     * A loop switch was held.
     *
     * @param switchIndex 0-5
     */
    public void loopSwitchHold (final int switchIndex)
    {
        // Holding is how hold-to-record records - never delete that recording
        if (!this.holdRecording[switchIndex])
            this.loopHold (this.getTrackBank ().getItem (switchIndex));
    }


    /**
     * A loop switch was released.
     *
     * @param switchIndex 0-5
     */
    public void loopSwitchRelease (final int switchIndex)
    {
        if (!this.holdRecording[switchIndex])
            return;
        this.holdRecording[switchIndex] = false;

        final ITrack track = this.getTrackBank ().getItem (switchIndex);
        if (!track.doesExist ())
            return;
        final LoopState state = this.getLoopState (track);
        if (state == LoopState.RECORDING)
            this.playLoop (track);
        else if (state == LoopState.RECORD_QUEUED)
            this.closeWhenRecording[switchIndex] = System.currentTimeMillis ();
    }


    // ---- LEDs ---------------------------------------------------------------------------------------------------

    /**
     * The LED test overrides every LED while it runs.
     *
     * @param now Wall-clock milliseconds
     * @return The light code, -1 if no test is running
     */
    public int getLedTestCode (final long now)
    {
        if (this.ledTestStartedAt < 0)
            return -1;
        final long step = (now - this.ledTestStartedAt) / LED_TEST_STEP_MS;
        if (step < LedColour.values ().length - 1)
            return LedColour.WHITE.ordinal () + (int) step;
        this.ledTestStartedAt = -1;
        return -1;
    }


    /**
     * @param switchIndex 0-5, a loop switch
     * @return What the loop switch's LED shows
     */
    public LedState loopSwitchLed (final int switchIndex)
    {
        return this.loopLed (this.getTrackBank ().getItem (switchIndex));
    }


    /**
     * The beat counter on the top row: while counting in (amber) or recording (red) the switch of the current beat
     * lights up.
     *
     * @param topRowIndex 0-3 (SW A-D)
     * @param now Wall-clock milliseconds
     * @return The light code, -1 if the counter is not active
     */
    public int getBeatCounterCode (final int topRowIndex, final long now)
    {
        if (!this.configuration.isCountBeats () || !this.clock.isPlaying ())
            return -1;

        final LoopState [] states = this.getRowStates ();
        final boolean recording = contains (states, LoopState.RECORDING);
        final boolean waiting = this.pendingCountIn != null || contains (states, LoopState.RECORD_QUEUED);
        if (!recording && !waiting)
            return -1;

        final LedClock beatClock = new LedClock (now, true, this.clock.getPositionInBeats (), this.clock.getBeatsPerBar ());
        if (Math.floorMod (beatClock.beatInBar (), TOP_ROW_SIZE) != topRowIndex)
            return 0;
        return (recording && this.pendingCountIn == null ? LedColour.RED : LedColour.AMBER).ordinal ();
    }


    /**
     * Cycle every LED through all colours, one second each.
     */
    public void startLedTest ()
    {
        this.ledTestStartedAt = System.currentTimeMillis ();
        this.notifyImportant ("LED test: white, red, green, amber, blue, purple");
    }


    // ---- Periodic and settings-driven work ----------------------------------------------------------------------

    /**
     * Runs on every tick: count-ins, hold-to-record, loop length matching, quantized mutes, fades, exclusive arm.
     */
    public void tick ()
    {
        this.updateCountIn ();
        this.updateHoldToRecord ();
        this.updateLoopLengths ();
        this.updatePendingMutes ();
        this.updateFade ();
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


    /**
     * Move the loop tracks to the position stored in the project.
     */
    public void applyLoopTrackStart ()
    {
        final ITrackBank trackBank = this.getTrackBank ();
        final int wanted = this.configuration.getLoopTrackStart () - 1;
        if (trackBank.getScrollPosition () == wanted)
            return;
        this.prepareForTrackScroll ();
        trackBank.scrollTo (wanted);
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
            case MUTE_SELECTED -> this.withSelectedTrack (track -> this.requestMute (track, !this.isEffectivelyMuted (track)));
            case SOLO_SELECTED -> this.withSelectedTrack (ITrack::toggleSolo);
            case MONITOR_SELECTED -> this.withSelectedTrack (track -> {
                this.notify (track.getName () + ": input monitoring " + (track.isMonitor () ? "off" : "on"));
                track.toggleMonitor ();
            });
            case CLEAR_SELECTED -> this.withSelectedTrack (this::clearLoop);
            case DOUBLE_SELECTED -> this.withSelectedTrack (track -> this.editLoopClip (track, true));
            case HALVE_SELECTED -> this.withSelectedTrack (track -> this.editLoopClip (track, false));
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
            case MUTE_ALL_TOGGLE -> this.toggleMuteAll ();
            case FADE_OUT -> this.toggleFade (VolumeFade.Direction.OUT);
            case FADE_IN -> this.toggleFade (VolumeFade.Direction.IN);
            case RESET -> this.reset ();
            case ROW_PREVIOUS -> this.scrollRows (false);
            case ROW_NEXT -> this.scrollRows (true);
            case DUPLICATE_ROW -> this.duplicateRow ();
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
            case SHOW_STATUS -> this.showStatus ();
            case LED_TEST -> this.startLedTest ();
            default -> {
                // FX actions run in the FxController, MOMENTARY in the PacerController
            }
        }
    }


    /**
     * The LED of a switch whose tap runs a looper action.
     *
     * @param action The action
     * @param ledClock The time
     * @return The LED state
     */
    public LedState actionLed (final Action action, final LedClock ledClock)
    {
        final ITransport transport = this.model.getTransport ();
        final ITrackBank trackBank = this.getTrackBank ();
        final Optional<ITrack> selected = this.getSelectedTrack ();
        final boolean selectedHasLoop = selected.isPresent () && this.getLoopSlot (selected.get ()).hasContent ();

        return switch (action)
        {
            case NONE -> LedState.DARK;
            case RECORD_NEXT_LAYER -> this.layerLed ();
            case CLEAR_LAST_LOOP -> LedState.when (this.anyLoop (false), LedColour.RED);
            case LOOP_SELECTED -> selected.map (this::loopLed).orElse (LedState.DARK);
            case STOP_SELECTED -> LedState.when (selected.isPresent () && selected.get ().isPlaying (), LedColour.WHITE);
            case MUTE_SELECTED -> {
                if (selected.isPresent () && this.pendingMutes.containsKey (Integer.valueOf (selected.get ().getIndex ())))
                    yield new LedState (LedColour.BLUE, LedPattern.BLINK_FAST);
                yield LedState.when (selected.isPresent () && selected.get ().isMute (), LedColour.BLUE);
            }
            case SOLO_SELECTED -> LedState.when (selected.isPresent () && selected.get ().isSolo (), LedColour.AMBER);
            case MONITOR_SELECTED -> LedState.when (selected.isPresent () && selected.get ().isMonitor (), LedColour.GREEN);
            case CLEAR_SELECTED -> LedState.when (selectedHasLoop, LedColour.RED);
            case DOUBLE_SELECTED, HALVE_SELECTED -> LedState.when (selectedHasLoop, LedColour.WHITE);
            case SELECT_PREVIOUS_LOOP, SELECT_NEXT_LOOP, SHOW_STATUS, LED_TEST -> LedState.solid (LedColour.WHITE);
            case PLAY_STOP_ALL -> {
                if (this.anyLoop (true))
                    yield LedState.solid (LedColour.GREEN);
                yield LedState.when (this.anyLoop (false), LedColour.AMBER);
            }
            case STOP_ALL -> LedState.when (this.anyLoop (true), LedColour.WHITE);
            case PLAY_ROW -> LedState.when (this.anyLoop (false), LedColour.GREEN);
            case CLEAR_ROW -> LedState.when (this.anyLoop (false), LedColour.RED);
            case MUTE_ALL_TOGGLE -> {
                if (!this.pendingMutes.isEmpty ())
                    yield new LedState (LedColour.BLUE, LedPattern.BLINK_FAST);
                yield LedState.when (this.anyLoopMuted (), LedColour.BLUE);
            }
            case FADE_OUT -> {
                if (this.fade != null && this.fade.getDirection () == VolumeFade.Direction.OUT)
                    yield new LedState (LedColour.WHITE, LedPattern.BLINK_MEDIUM);
                yield LedState.when (this.anyLoop (true), LedColour.WHITE);
            }
            case FADE_IN -> {
                if (this.fade != null && this.fade.getDirection () == VolumeFade.Direction.IN)
                    yield new LedState (LedColour.GREEN, LedPattern.BLINK_MEDIUM);
                yield LedState.when (this.anyLoop (false) && !this.anyLoop (true), LedColour.GREEN);
            }
            case RESET -> LedState.when (this.anyLoop (true), LedColour.PURPLE);
            case ROW_PREVIOUS -> LedState.when (trackBank.getSceneBank ().canScrollBackwards (), LedColour.WHITE);
            case ROW_NEXT -> LedState.solid (trackBank.getSceneBank ().canScrollForwards () ? LedColour.WHITE : LedColour.BLUE);
            case DUPLICATE_ROW -> LedState.when (this.anyLoop (false), LedColour.WHITE);
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
            default -> LedState.DARK;
        };
    }


    // ---- Loops --------------------------------------------------------------------------------------------------

    private void loopTap (final ITrack track)
    {
        if (!track.doesExist ())
        {
            this.notifyImportant ("No track for this loop switch");
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
            case TOGGLE_MUTE -> this.requestMute (track, !this.isEffectivelyMuted (track));
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

        final LedMode mode = this.configuration.getEffectiveLedMode ();
        final LoopColours colours = this.configuration.getLoopColours ();
        final double [] pendingMute = this.pendingMutes.get (Integer.valueOf (track.getIndex ()));
        if (pendingMute != null)
        {
            if (mode != LedMode.MULTI_COLOUR)
                return new LedState (LedColour.RED, LedPattern.BLINK_FAST);
            return new LedState (pendingMute[0] > 0 ? colours.muted () : colours.playing (), LedPattern.BLINK_FAST);
        }

        final LoopState state = this.getLoopState (track);
        final boolean overdubbing = state == LoopState.PLAYING && this.model.getTransport ().isLauncherOverdub () && track.isRecArm ();
        return LoopLeds.forLoop (state, overdubbing, track.isMute (), mode, colours);
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
        this.notifyImportant ("Count-in: " + countIn.getLabel ());
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
        this.pendingMutes.remove (Integer.valueOf (track.getIndex ()));
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
        this.notifyImportant ("Count-in cancelled");
    }


    /** Close hold-to-record loops whose switch was released before recording had started. */
    private void updateHoldToRecord ()
    {
        final long now = System.currentTimeMillis ();
        final ITrackBank trackBank = this.getTrackBank ();
        for (int i = 0; i < this.closeWhenRecording.length; i++)
        {
            if (this.closeWhenRecording[i] < 0)
                continue;
            final ITrack track = trackBank.getItem (i);
            final LoopState state = track.doesExist () ? this.getLoopState (track) : LoopState.EMPTY;
            if (state == LoopState.RECORDING)
            {
                this.closeWhenRecording[i] = -1;
                this.playLoop (track);
            }
            else if (state != LoopState.RECORD_QUEUED && now - this.closeWhenRecording[i] > CLOSE_WAIT_MS)
                this.closeWhenRecording[i] = -1;
        }
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
                this.notifyImportant ("Loops in this row are now " + bars + (bars == 1 ? " bar" : " bars") + " long");
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
            case FULL -> this.notifyImportant ("Every loop track in this row has a loop");
        }
    }


    private LedState layerLed ()
    {
        final LoopState [] states = this.getRowStates ();
        final int focus = LayerPlanner.focus (states);
        if (focus >= 0)
            return this.loopLed (this.getTrackBank ().getItem (focus));
        final LedMode mode = this.configuration.getEffectiveLedMode ();
        final LoopColours colours = this.configuration.getLoopColours ();
        if (this.anyLoop (true))
            return LoopLeds.forLoop (LoopState.PLAYING, false, false, mode, colours);
        if (this.anyLoop (false))
            return LoopLeds.forLoop (LoopState.STOPPED, false, false, mode, colours);
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
        this.notifyImportant ("No recorded loop left to clear in this row");
    }


    /** Double (duplicate the content) or halve the loop of a track, through the launcher cursor clip. */
    private void editLoopClip (final ITrack track, final boolean doubleIt)
    {
        final ISlot slot = this.getLoopSlot (track);
        if (!slot.hasContent ())
        {
            this.notifyImportant ("No loop on " + track.getName ());
            return;
        }

        track.select ();
        slot.select ();
        this.host.scheduleTask ( () -> {
            final INoteClip clip = this.model.getCursorClip ();
            if (!clip.doesExist ())
                return;
            if (doubleIt)
            {
                clip.duplicateContent ();
                this.notify (track.getName () + ": loop doubled");
                return;
            }
            final double length = clip.getLoopLength ();
            if (length < 2)
            {
                this.notifyImportant (track.getName () + ": loop is too short to halve");
                return;
            }
            clip.setLoopLength (length / 2);
            this.notify (track.getName () + ": loop halved");
        }, CURSOR_CLIP_FOLLOW_MS);
    }


    // ---- Mutes --------------------------------------------------------------------------------------------------

    /** Mute or unmute now, or on the next beat or bar (setting "Mute timing"). */
    private void requestMute (final ITrack track, final boolean mute)
    {
        final Integer index = Integer.valueOf (track.getIndex ());
        final MuteTiming timing = this.configuration.getMuteTiming ();
        if (timing == MuteTiming.IMMEDIATE || !this.clock.isPlaying ())
        {
            this.pendingMutes.remove (index);
            track.setMute (mute);
            return;
        }

        // Asking again for the state it already has cancels the pending change
        if (track.isMute () == mute)
        {
            this.pendingMutes.remove (index);
            return;
        }
        final double at = MuteTiming.nextBoundary (this.clock.getPositionInBeats (), timing.getUnitBeats (this.clock.getBeatsPerBar ()));
        this.pendingMutes.put (index, new double []
        {
            mute ? 1 : 0,
            at
        });
    }


    private boolean isEffectivelyMuted (final ITrack track)
    {
        final double [] pending = this.pendingMutes.get (Integer.valueOf (track.getIndex ()));
        return pending == null ? track.isMute () : pending[0] > 0;
    }


    private void updatePendingMutes ()
    {
        if (this.pendingMutes.isEmpty ())
            return;
        final boolean running = this.clock.isPlaying ();
        final double position = this.clock.getPositionInBeats ();
        this.applyPendingMutes (entry -> !running || position >= entry[1]);
    }


    private void applyPendingMutes (final Predicate<double []> due)
    {
        final ITrackBank trackBank = this.getTrackBank ();
        final Iterator<Map.Entry<Integer, double []>> it = this.pendingMutes.entrySet ().iterator ();
        while (it.hasNext ())
        {
            final Map.Entry<Integer, double []> entry = it.next ();
            if (!due.test (entry.getValue ()))
                continue;
            final ITrack track = trackBank.getItem (entry.getKey ().intValue ());
            if (track.doesExist ())
                track.setMute (entry.getValue ()[0] > 0);
            it.remove ();
        }
    }


    private void toggleMuteAll ()
    {
        final ITrackBank trackBank = this.getTrackBank ();
        boolean anyAudible = false;
        for (int i = 0; i < this.getLoopCount (); i++)
        {
            final ITrack track = trackBank.getItem (i);
            if (track.doesExist () && !this.isEffectivelyMuted (track) && this.getLoopSlot (track).hasContent ())
                anyAudible = true;
        }
        for (int i = 0; i < this.getLoopCount (); i++)
        {
            final ITrack track = trackBank.getItem (i);
            if (track.doesExist ())
                this.requestMute (track, anyAudible);
        }
        this.notify (anyAudible ? "All loops muted" : "All loops unmuted");
    }


    // ---- Fades --------------------------------------------------------------------------------------------------

    private void toggleFade (final VolumeFade.Direction direction)
    {
        if (this.fade != null)
        {
            this.restoreFadeVolumes ();
            this.notify ("Fade cancelled");
            return;
        }

        if (direction == VolumeFade.Direction.OUT ? !this.anyLoop (true) : !this.anyLoop (false))
        {
            this.notifyImportant (direction == VolumeFade.Direction.OUT ? "No loop is playing" : "No loops in this row");
            return;
        }

        final Map<Integer, Double> volumes = new LinkedHashMap<> ();
        final ITrackBank trackBank = this.getTrackBank ();
        for (int i = 0; i < this.getLoopCount (); i++)
        {
            final ITrack track = trackBank.getItem (i);
            if (track.doesExist ())
                volumes.put (Integer.valueOf (i), Double.valueOf (this.model.getValueChanger ().toNormalizedValue (track.getVolume ())));
        }

        final int bars = this.configuration.getFadeLength ().getBars ();
        this.fade = new VolumeFade (direction, bars * this.clock.getBeatsPerBar (), volumes);
        this.fadeStopRequestedAt = -1;

        if (direction == VolumeFade.Direction.IN)
        {
            // Silence first, then launch; the ramp starts once the loops actually play
            for (final Integer index: volumes.keySet ())
                trackBank.getItem (index.intValue ()).getVolumeParameter ().setNormalizedValue (0);
            if (!this.anyLoop (true))
                this.playRow ();
        }
        this.notify ("Fade " + (direction == VolumeFade.Direction.OUT ? "out" : "in") + " over " + this.configuration.getFadeLength ().getLabel ());
    }


    private void updateFade ()
    {
        final VolumeFade current = this.fade;
        if (current == null)
            return;

        if (this.fadeStopRequestedAt >= 0)
        {
            // Faded out: restore the volumes once the loops have really stopped
            if (!this.anyLoop (true) || System.currentTimeMillis () - this.fadeStopRequestedAt > FADE_STOP_TIMEOUT_MS)
                this.restoreFadeVolumes ();
            return;
        }

        if (!this.clock.isPlaying ())
        {
            if (current.isStarted ())
                this.restoreFadeVolumes ();
            return;
        }
        if (current.getDirection () == VolumeFade.Direction.IN && !this.anyLoop (true))
            return;

        final double position = this.clock.getPositionInBeats ();
        current.startAt (position);
        final ITrackBank trackBank = this.getTrackBank ();
        for (final Integer index: current.getOriginalVolumes ().keySet ())
        {
            final ITrack track = trackBank.getItem (index.intValue ());
            if (track.doesExist ())
                track.getVolumeParameter ().setNormalizedValue (current.getVolume (index.intValue (), position));
        }

        if (!current.isComplete (position))
            return;
        if (current.getDirection () == VolumeFade.Direction.OUT)
        {
            trackBank.stop (false);
            this.fadeStopRequestedAt = System.currentTimeMillis ();
        }
        else
            this.fade = null;
    }


    private void restoreFadeVolumes ()
    {
        final VolumeFade current = this.fade;
        this.fade = null;
        this.fadeStopRequestedAt = -1;
        if (current == null)
            return;
        final ITrackBank trackBank = this.getTrackBank ();
        for (final Map.Entry<Integer, Double> entry: current.getOriginalVolumes ().entrySet ())
        {
            final ITrack track = trackBank.getItem (entry.getKey ().intValue ());
            if (track.doesExist ())
                track.getVolumeParameter ().setNormalizedValue (entry.getValue ().doubleValue ());
        }
    }


    // ---- Arm, rows, tracks --------------------------------------------------------------------------------------

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
        this.notify ("Play " + LooperText.rowLabel (sceneBank.getScrollPosition (), scene.getName ()));
    }


    private void clearRow ()
    {
        if (this.pendingCountIn != null)
            this.cancelCountIn ();
        if (this.fade != null)
            this.restoreFadeVolumes ();
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


    /** Panic button: stop everything and leave the loop tracks clean. */
    private void reset ()
    {
        if (this.pendingCountIn != null)
            this.cancelCountIn ();
        if (this.fade != null)
            this.restoreFadeVolumes ();
        this.pendingMutes.clear ();
        Arrays.fill (this.holdRecording, false);
        Arrays.fill (this.closeWhenRecording, -1);

        final ITrackBank trackBank = this.getTrackBank ();
        trackBank.stop (false);
        for (int i = 0; i < this.getLoopCount (); i++)
        {
            final ITrack track = trackBank.getItem (i);
            if (!track.doesExist ())
                continue;
            track.setMute (false);
            track.setSolo (false);
            track.setRecArm (false);
        }
        this.armedByLooper.clear ();
        this.lastArmedIndex = -1;
        this.model.getTransport ().setLauncherOverdub (false);
        this.notifyImportant ("Looper reset: stopped, unmuted, unsoloed, disarmed");
    }


    private void showStatus ()
    {
        final ITrackBank trackBank = this.getTrackBank ();
        final ISceneBank sceneBank = trackBank.getSceneBank ();
        final LoopState [] states = this.getRowStates ();
        final boolean [] muted = new boolean [states.length];
        for (int i = 0; i < states.length; i++)
            muted[i] = states[i] != null && this.isEffectivelyMuted (trackBank.getItem (i));
        // Asked for explicitly, so shown whatever the notification level
        this.host.showNotification (LooperText.status (sceneBank.getScrollPosition (), sceneBank.getItem (0).getName (), states, muted));
    }


    private void scrollRows (final boolean forwards)
    {
        final ISceneBank sceneBank = this.getTrackBank ().getSceneBank ();
        boolean created = false;
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
            created = true;
        }

        final boolean isNew = created;
        this.host.scheduleTask ( () -> {
            final int row = sceneBank.getScrollPosition ();
            final IScene scene = sceneBank.getItem (0);
            String name = scene.getName ();
            if (isNew && (name == null || name.isBlank ()))
            {
                final String wanted = LooperText.rowName (this.configuration.getRowNames (), row);
                if (!wanted.isEmpty ())
                {
                    scene.setName (wanted);
                    name = wanted;
                }
            }
            this.notifyImportant (LooperText.rowLabel (row, name));
        }, isNew ? 3 * NOTIFY_DELAY_MS : NOTIFY_DELAY_MS);
    }


    private void duplicateRow ()
    {
        if (!this.anyLoop (false))
        {
            this.notifyImportant ("Nothing to duplicate in this row");
            return;
        }
        final ISceneBank sceneBank = this.getTrackBank ().getSceneBank ();
        sceneBank.getItem (0).duplicate ();
        // The copy is inserted right after this row
        this.host.scheduleTask (sceneBank::scrollForwards, 100);
        this.host.scheduleTask ( () -> this.notifyImportant ("Row duplicated - now on " + LooperText.rowLabel (sceneBank.getScrollPosition (), sceneBank.getItem (0).getName ())), 2 * NOTIFY_DELAY_MS);
    }


    /** Positions in the track bank are about to point at other tracks: settle everything that refers to them. */
    private void prepareForTrackScroll ()
    {
        if (this.fade != null)
            this.restoreFadeVolumes ();
        if (this.pendingCountIn != null)
            this.cancelCountIn ();
        this.applyPendingMutes (entry -> true);
        Arrays.fill (this.holdRecording, false);
        Arrays.fill (this.closeWhenRecording, -1);
        this.armedByLooper.clear ();
        this.lastArmedIndex = -1;
        this.recordHistory.clear ();
        this.lengthTracker.reset ();
    }


    private void scrollTracks (final boolean forwards)
    {
        this.prepareForTrackScroll ();

        final ITrackBank trackBank = this.getTrackBank ();
        if (forwards)
            trackBank.scrollForwards ();
        else
            trackBank.scrollBackwards ();
        this.host.scheduleTask ( () -> {
            // Remember the position in the project
            this.configuration.setLoopTrackStart (trackBank.getScrollPosition () + 1);
            this.notifyImportant ("Loop tracks start at " + trackBank.getItem (0).getName ());
        }, NOTIFY_DELAY_MS);
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
            this.notifyImportant ("Select one of the loop tracks first");
    }


    /**
     * Is any loop track playing (or, with playing = false, holding a clip in the row)?
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


    private boolean anyLoopMuted ()
    {
        final ITrackBank trackBank = this.getTrackBank ();
        for (int i = 0; i < this.getLoopCount (); i++)
        {
            final ITrack track = trackBank.getItem (i);
            if (track.doesExist () && track.isMute ())
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


    private static boolean contains (final LoopState [] states, final LoopState wanted)
    {
        for (final LoopState state: states)
            if (state == wanted)
                return true;
        return false;
    }


    // ---- Helpers ------------------------------------------------------------------------------------------------

    /**
     * @param target A pedal target
     * @return The Bitwig parameter of a parameter target, null for other targets
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
     * @param now Wall-clock milliseconds
     * @return The time LED patterns are evaluated at, beat-synced while the transport runs (setting)
     */
    public LedClock getLedClock (final long now)
    {
        if (!this.configuration.isBeatSyncedLeds () || !this.clock.isPlaying ())
            return LedClock.unsynced (now);
        return new LedClock (now, true, this.clock.getPositionInBeats (), this.clock.getBeatsPerBar ());
    }


    /**
     * @param switchIndex 0-9
     * @return True if the switch is a loop switch (on the looper preset)
     */
    public boolean isLoopSwitch (final int switchIndex)
    {
        return switchIndex < this.configuration.getLoopSwitchCount ().getCount () && switchIndex < this.getLoopCount ();
    }


    private int getLoopCount ()
    {
        return this.configuration.getLoopTrackCount ();
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


    /** A confirmation, shown only at notification level "All". */
    private void notify (final String message)
    {
        if (this.configuration.getNotificationLevel ().shows (false))
            this.host.showNotification (message);
    }


    /** Navigation, warnings and state that is not visible on the Pacer. */
    private void notifyImportant (final String message)
    {
        if (this.configuration.getNotificationLevel ().shows (true))
            this.host.showNotification (message);
    }
}
