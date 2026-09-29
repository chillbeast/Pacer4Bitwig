// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.parameter.IParameter;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.led.LedClock;
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedPattern;
import dev.pacer4bitwig.pacer.led.LedState;
import dev.pacer4bitwig.pacer.live.LiveBoard;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.looper.ExpressionTarget;
import dev.pacer4bitwig.pacer.looper.PedalHold;
import dev.pacer4bitwig.pacer.looper.PedalPickup;
import dev.pacer4bitwig.pacer.looper.PedalResponse;
import dev.pacer4bitwig.pacer.looper.PedalTakeover;
import dev.pacer4bitwig.pacer.looper.TapTiming;
import dev.pacer4bitwig.pacer.midi.RawMidiSender;
import dev.pacer4bitwig.pacer.mode.CustomBoard;
import dev.pacer4bitwig.pacer.mode.CustomTarget;
import dev.pacer4bitwig.pacer.mode.Mode;
import dev.pacer4bitwig.pacer.mode.ModeBoard;
import dev.pacer4bitwig.pacer.mode.ModeMenu;
import dev.pacer4bitwig.pacer.mode.ModePainter;
import dev.pacer4bitwig.pacer.mode.ModeState;
import dev.pacer4bitwig.pacer.mode.ShiftLayer;
import dev.pacer4bitwig.pacer.mode.ShiftedBoard;
import dev.pacer4bitwig.pacer.mode.SwitchLayout;
import dev.pacer4bitwig.pacer.mode.SwitchRole;
import dev.pacer4bitwig.pacer.preset.PresetAnnouncement;
import dev.pacer4bitwig.pacer.preset.PresetKind;
import dev.pacer4bitwig.util.Diagnostics;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;


/**
 * The Pacer's switches, jacks and pedals - and what its LEDs show - for the active {@link Mode}. The Pacer stays on
 * one preset; a mode decides what each switch does and is painted onto the device live (docs/LIVE-COLOURS-AND-MODES.md).
 * <p>
 * SW 6 is the mode switch everywhere: a tap toggles between the last two modes, a hold opens the {@link ModeMenu}
 * where SW 1-5 pick a mode and SW A-D navigate; its double-tap is a setting. {@link Action#MOMENTARY}, the mode
 * actions and the shift layer ({@link ShiftLayer}) are handled here. The setup only wires hardware to these methods.
 * <p>
 * Controls are numbered for the shift layer's "while held": the switches 0-9, then the jacks.
 */
public class PacerController
{
    /** How long an event word stays on the display - long enough to outlast the release of the switch that caused it. */
    private static final long        EVENT_MILLIS          = 1500;

    private final IHost              host;
    private final PacerConfiguration configuration;
    private final LooperController   looper;
    private final FxController       fx;
    private final LiveBoard          board;
    private final ModeState          modes                 = new ModeState (Mode.LOOP);
    /**
     * The colour each switch last asked for, filled in by {@link #getLedCode(int)} during the flush. Painting reads
     * this cache instead of querying Bitwig again: the flush already worked it out, and asking twice both doubled
     * the load and let the two answers disagree.
     */
    private final PacerColour []     switchColours         = new PacerColour [PacerMap.NUM_SWITCHES];
    private RawMidiSender            midiSender            = RawMidiSender.NONE;
    /** What each expression pedal is holding on a receiver, so it can be handed back. */
    private final PedalHold []       holds                 = {
        new PedalHold (),
        new PedalHold ()
    };
    /** Called when the active mode changed, so the setup can re-bind the pedals. */
    private Runnable                 modeListener;
    /** The tap action a momentary hold runs again on release, null if none. */
    private final Action []          momentarySwitches     = new Action [PacerMap.NUM_SWITCHES];
    /**
     * What each switch was for when it went down, and on which board. A press can change the mode - a menu slot does
     * exactly that on press - and its hold and release must still belong to the switch that was pressed, not to
     * whatever the new mode puts there: holding the Song slot a moment too long used to run Song's SW 4 hold, a fade
     * out of every loop.
     */
    private final SwitchRole []      pressRoles            = new SwitchRole [PacerMap.NUM_SWITCHES];
    private final ModeBoard []       pressBoards           = new ModeBoard [PacerMap.NUM_SWITCHES];
    /** True if a switch's latest press latched the same role and board as the one before it. */
    private final boolean []         pressSame             = new boolean [PacerMap.NUM_SWITCHES];
    private final Action []          momentaryFootswitches = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final ShiftLayer         shift                 = new ShiftLayer ();
    /** The active board's shift layer, kept while the board stays the same. */
    private ShiftedBoard             shiftedBoard;
    /** Which switches and jacks are down right now, by control number. */
    private final boolean []         controlDown           = new boolean [PacerMap.NUM_SWITCHES + PacerMap.NUM_FOOTSWITCHES];
    /** The control whose gesture is running an action, -1 outside of one: "shift while held" needs to know. */
    private int                      performingControl     = -1;
    /** Pick-up takeover, per pedal. */
    private final PedalPickup []     pickups               = new PedalPickup [PacerConfiguration.NUM_EXPRESSION];
    /** The last value sent to each MIDI target, which is where a picked-up pedal has to reach. */
    private final Map<ExpressionTarget, Integer> midiSent  = new EnumMap<> (ExpressionTarget.class);
    /** What each pedal's target was at its last move (see {@link #getTargetKey}); a new one is picked up again. */
    private final long []            targetKeys            = new long [PacerConfiguration.NUM_EXPRESSION];
    /** Which switches showed a pattern that moves (a blink, the beat counter) in the last flush. */
    private final boolean []         animated              = new boolean [PacerMap.NUM_SWITCHES];
    private Diagnostics              diagnostics           = Diagnostics.NONE;
    /** A word for what just happened, shown on the display instead of the name until {@link #eventUntil}. */
    private volatile String          eventWord;
    private volatile long            eventUntil;


    /**
     * Constructor.
     *
     * @param host The host
     * @param configuration The configuration
     * @param looper The looper
     * @param fx The FX preset
     * @param board The Pacer's live colours and display name
     */
    public PacerController (final IHost host, final PacerConfiguration configuration, final LooperController looper, final FxController fx, final LiveBoard board)
    {
        this.host = host;
        this.configuration = configuration;
        this.looper = looper;
        this.fx = fx;
        this.board = board;
        for (int i = 0; i < this.pickups.length; i++)
            this.pickups[i] = new PedalPickup ();
        Arrays.fill (this.targetKeys, Long.MIN_VALUE);
        // While the custom layout changes a built-in mode, the custom mode's own menu slot has nothing to show
        this.modes.setOffered (mode -> mode != Mode.CUSTOM || this.configuration.getCustomBoard ().getTarget () == CustomTarget.OWN);
    }


    /**
     * @param midiSender Sends pedal MIDI into Bitwig
     */
    public void setMidiSender (final RawMidiSender midiSender)
    {
        this.midiSender = midiSender;
    }


    /**
     * @param diagnostics Where presses, actions and mode changes are logged
     */
    public void setDiagnostics (final Diagnostics diagnostics)
    {
        this.diagnostics = diagnostics == null ? Diagnostics.NONE : diagnostics;
    }


    /**
     * @param modeListener Runs after the active mode changed
     */
    public void setModeListener (final Runnable modeListener)
    {
        this.modeListener = modeListener;
    }


    // ---- Modes --------------------------------------------------------------------------------------------------

    /**
     * @return The active mode
     */
    public Mode getMode ()
    {
        return this.modes.getActive ();
    }


    /**
     * The board of the active mode as the switches see it right now: through its shift layer while that is up.
     * Every read of a layout goes through here.
     *
     * @return The board
     */
    private ModeBoard getBoard ()
    {
        final ModeBoard base = this.getBaseBoard ();
        if (!this.shift.isOn () || this.modes.isMenuOpen ())
            return base;
        if (this.shiftedBoard == null || this.shiftedBoard.getBase () != base)
            this.shiftedBoard = new ShiftedBoard (base);
        return this.shiftedBoard;
    }


    /**
     * The board of the active mode. The custom layout is the board of {@link Mode#CUSTOM}, or of the built-in mode it
     * changes.
     *
     * @return The board, without its shift layer
     */
    private ModeBoard getBaseBoard ()
    {
        final Mode mode = this.modes.getActive ();
        final CustomBoard custom = this.configuration.getCustomBoard ();
        return mode == Mode.CUSTOM || mode == custom.getTarget ().getMode () ? custom : mode;
    }


    /**
     * The custom layout changed in the settings. Standing in the mode it lays out, that shows at once; and if the
     * layout now changes a built-in mode while the custom mode is active, that mode takes over - the custom slot
     * has gone dark.
     */
    public void customLayoutChanged ()
    {
        final CustomTarget target = this.configuration.getCustomBoard ().getTarget ();
        if (this.modes.getActive () == Mode.CUSTOM && target != CustomTarget.OWN && this.modes.activate (target.getMode ()))
        {
            this.modeChanged ();
            return;
        }
        if (this.getBaseBoard () == this.configuration.getCustomBoard () || this.modes.isMenuOpen ())
        {
            this.board.invalidate ();
            this.repaintSwitches ();
        }
    }


    /**
     * Start in the mode the settings ask for. Called once the extension is running.
     */
    public void applyStartupMode ()
    {
        this.modes.activate (this.configuration.getModeAtStartup ());
        this.board.invalidate ();
        this.refreshColours ();
        this.paint ();
        // The pedals were bound during init, before the mode was known
        if (this.modeListener != null)
            this.modeListener.run ();
    }


    /**
     * Darken the Pacer and say so, for when the extension stops driving it.
     */
    public void blackout ()
    {
        this.board.blackout ("OFF");
    }


    /**
     * @return How the shift layer is up, if it is
     */
    public ShiftLayer.State getShiftState ()
    {
        return this.shift.getState ();
    }


    /**
     * @return True while SW 6 is held and the mode menu is showing
     */
    public boolean isMenuOpen ()
    {
        return this.modes.isMenuOpen ();
    }


    /**
     * Paint the active mode (or the menu) onto the Pacer. Only switches that would change are written.
     */
    public void paint ()
    {
        ModePainter.paint (this.board, this.modes, this.getBoard (), this.getDisplayName (), this::stateColour);
    }


    /**
     * What the Pacer's display should read: the mode's name, or - when the mode has something more useful to say and
     * the setting allows it - what it is doing. The FX mode names the focused instrument, the Looper and Song modes
     * name the row (the scene's name, or ROW n).
     *
     * @return At most five characters' worth; the writer cuts it
     */
    private String getDisplayName ()
    {
        final String event = this.eventWord;
        if (event != null)
        {
            if (System.currentTimeMillis () < this.eventUntil)
                return event;
            this.eventWord = null;
        }
        final ModeBoard board = this.getBoard ();
        if (!this.configuration.isShowContext ())
            return board.getDisplayName ();
        final String context = switch (this.modes.getActive ())
        {
            case FX -> this.fx.getFocusedInstrumentName ();
            case LOOP, SONG -> this.looper.getRowDisplayName ();
            default -> "";
        };
        return context == null || context.isBlank () ? board.getDisplayName () : context.trim ();
    }


    /**
     * Put a word for what just happened on the display for a moment ("REC 2", "4 BAR", "UNDO"), if the setting
     * allows it. It costs two writes: the word, and the name again after it.
     *
     * @param word At most five characters
     */
    public void showEvent (final String word)
    {
        if (!this.configuration.isShowEvents () || word == null || word.isBlank ())
            return;
        this.eventUntil = System.currentTimeMillis () + EVENT_MILLIS;
        this.eventWord = word;
    }


    /**
     * The colour a switch shows while it is lit, as of the last flush. {@link PacerColour#OFF} means "no state of its
     * own", and the switch keeps the colour its mode gave it.
     *
     * @param switchIndex 0-9
     * @return The colour
     */
    private PacerColour stateColour (final int switchIndex)
    {
        final PacerColour colour = this.switchColours[switchIndex];
        return colour == null ? PacerColour.OFF : colour;
    }


    /**
     * Forget what the Pacer shows and paint everything again - the device discards every live edit when a preset is
     * selected on it.
     */
    public void repaintAll ()
    {
        this.board.invalidate ();
        this.paint ();
    }


    // ---- Stomp switches -----------------------------------------------------------------------------------------

    /**
     * A switch went down. Runs before anything else about the press is asked: from here until the next press, the
     * switch keeps the role and board it has now, whatever the mode does in between.
     *
     * @param switchIndex 0-9
     */
    public void press (final int switchIndex)
    {
        this.controlDown[switchIndex] = true;
        final SwitchRole role = this.role (switchIndex);
        final ModeBoard board = this.getBoard ();
        // SW 6 is the mode switch on every board
        this.pressSame[switchIndex] = Mode.isModeSwitch (switchIndex) || role == this.pressRoles[switchIndex] && board == this.pressBoards[switchIndex];
        this.pressRoles[switchIndex] = role;
        this.pressBoards[switchIndex] = board;
        this.log ( () -> PacerMap.SWITCH_NAMES[switchIndex] + " down: " + this.pressRoles[switchIndex] + " on " + this.pressBoards[switchIndex].getDisplayName () + (this.shift.isOn () ? " (shift " + this.shift.getState () + ")" : "") + (this.modes.isMenuOpen () ? " (menu)" : ""));
        // A layer raised for one press is used up by it - the press keeps the shifted board it latched. A switch
        // that raises the layer itself does not use it up: pressing it again takes the layer down instead.
        if (!Mode.isModeSwitch (switchIndex) && !this.modes.isMenuOpen () && !this.getSwitchTap (switchIndex).isShift () && this.shift.usedByPress ())
            this.shiftChanged ();
    }


    /**
     * @param switchIndex 0-9
     * @return True if the switch's latest press latched the same role and board as the press before it, so it may
     *         finish that press's double-tap
     */
    public boolean isSamePressTarget (final int switchIndex)
    {
        return this.pressSame[switchIndex];
    }


    /**
     * @param switchIndex 0-9
     * @return True if the switch fires its tap on press
     */
    public boolean isTapOnPress (final int switchIndex)
    {
        return switch (this.pressRole (switchIndex))
        {
            // The mode switch must let a hold pre-empt its tap, and menu slots should answer at once
            case MODE_SWITCH -> false;
            case MODE_SLOT, NAVIGATION -> true;
            case LOOP_TRACK -> this.looper.isLoopSwitchTapOnPress ();
            case ACTION -> TapTiming.actionTapOnPress (this.getSwitchTap (switchIndex), this.getSwitchHold (switchIndex));
            case NONE -> false;
        };
    }


    /**
     * @param switchIndex 0-9
     * @return How much longer than a normal hold the switch must stay down before its hold runs
     */
    public long getExtraHoldMillis (final int switchIndex)
    {
        return switch (this.pressRole (switchIndex))
        {
            // The menu opens at the normal hold time - waiting longer for it would feel broken
            case MODE_SWITCH, MODE_SLOT, NAVIGATION, NONE -> 0;
            case LOOP_TRACK -> this.looper.getLoopSwitchExtraHoldMillis ();
            case ACTION -> this.getExtraHoldMillis (this.getSwitchHold (switchIndex));
        };
    }


    /**
     * @param switchIndex 0-9
     * @return True if a double-tap action is assigned to the switch
     */
    public boolean isDoubleTapEnabled (final int switchIndex)
    {
        return switch (this.pressRole (switchIndex))
        {
            // A tap that closes the menu should close it at once, so the double-tap only exists with the menu shut
            case MODE_SWITCH -> !this.modes.isMenuOpen () && this.configuration.getModeSwitchDoubleTap () != Action.NONE;
            case MODE_SLOT, NAVIGATION, NONE -> false;
            case LOOP_TRACK -> this.looper.isLoopSwitchDoubleTapEnabled ();
            case ACTION -> this.getSwitchDoubleTap (switchIndex) != Action.NONE;
        };
    }


    /**
     * A switch was tapped.
     *
     * @param switchIndex 0-9
     */
    public void tap (final int switchIndex)
    {
        this.log ( () -> PacerMap.SWITCH_NAMES[switchIndex] + " tap");
        switch (this.pressRole (switchIndex))
        {
            case MODE_SWITCH -> {
                // Closes the menu if it is open, otherwise goes back to the mode before this one
                if (this.modes.tapModeSwitch ())
                    this.modeChanged ();
                else
                    this.repaintSwitches ();
            }
            case MODE_SLOT -> {
                // Picking a mode closes the menu, so one foot can hold, let go, and tap
                if (this.modes.select (switchIndex))
                    this.modeChanged ();
                else
                    this.repaintSwitches ();
            }
            // Navigation leaves the menu open so it can be pressed again
            case NAVIGATION -> this.perform (ModeMenu.actionAt (switchIndex));
            case LOOP_TRACK -> this.looper.loopSwitchTap (this.pressLoopTrack (switchIndex));
            case ACTION -> this.performFor (switchIndex, this.getSwitchTap (switchIndex));
            case NONE -> {
                // Nothing assigned
            }
        }
    }


    /**
     * A switch was double-tapped (the first tap has already run).
     *
     * @param switchIndex 0-9
     */
    public void doubleTap (final int switchIndex)
    {
        this.log ( () -> PacerMap.SWITCH_NAMES[switchIndex] + " double-tap");
        switch (this.pressRole (switchIndex))
        {
            // SW 6 waited for this, so its tap never ran
            case MODE_SWITCH -> this.performFor (switchIndex, this.configuration.getModeSwitchDoubleTap ());
            case LOOP_TRACK -> this.looper.loopSwitchDoubleTap (this.pressLoopTrack (switchIndex));
            case ACTION -> this.performFor (switchIndex, this.getSwitchDoubleTap (switchIndex));
            default -> {
                // The menu has no double-tap
            }
        }
    }


    /**
     * A switch was held.
     *
     * @param switchIndex 0-9
     */
    public void hold (final int switchIndex)
    {
        this.log ( () -> PacerMap.SWITCH_NAMES[switchIndex] + " hold");
        if (Mode.isModeSwitch (switchIndex))
        {
            // The menu stays open when the foot comes off - a foot cannot hold one switch and press another
            this.modes.openMenu ();
            this.paint ();
            return;
        }
        this.momentarySwitches[switchIndex] = null;
        switch (this.pressRole (switchIndex))
        {
            case LOOP_TRACK -> this.looper.loopSwitchHold (this.pressLoopTrack (switchIndex));
            case ACTION -> {
                final Action hold = this.getSwitchHold (switchIndex);
                if (hold == Action.MOMENTARY)
                    this.momentarySwitches[switchIndex] = this.getSwitchTap (switchIndex);
                else
                    this.performFor (switchIndex, hold);
            }
            default -> {
                // While the menu is open the other switches belong to it, and it has no hold actions
            }
        }
    }


    /**
     * A switch was released.
     *
     * @param switchIndex 0-9
     */
    public void release (final int switchIndex)
    {
        this.log ( () -> PacerMap.SWITCH_NAMES[switchIndex] + " up");
        this.controlDown[switchIndex] = false;
        if (this.shift.release (switchIndex))
            this.shiftChanged ();
        if (Mode.isModeSwitch (switchIndex))
            // Nothing: the menu latches, and a tap is what closes it again
            return;
        final Action momentary = this.momentarySwitches[switchIndex];
        this.momentarySwitches[switchIndex] = null;
        if (momentary != null)
            this.performFor (switchIndex, momentary);
        if (this.pressRole (switchIndex) == SwitchRole.LOOP_TRACK)
            this.looper.loopSwitchRelease (this.pressLoopTrack (switchIndex));
    }


    private void modeChanged ()
    {
        this.log ( () -> "mode " + this.modes.getPrevious () + " -> " + this.modes.getActive ());
        // A new board starts on its normal layer
        this.shift.reset ();
        // Momentary holds stay armed: their release belongs to the press that started them (see pressRoles), so a
        // held FX switch still switches off when the mode changed under it
        // Saved with the project, for "Mode at startup = whatever this project used last"
        this.configuration.setProjectMode (this.modes.getActive ());
        this.repaintSwitches ();
        if (this.modeListener != null)
            this.modeListener.run ();
        if (this.configuration.getNotificationLevel ().shows (true))
            this.host.showNotification (this.modes.getActive ().getLabel ());
    }


    // ---- Footswitch jacks ---------------------------------------------------------------------------------------

    /**
     * A footswitch jack went down.
     *
     * @param index 0-3
     */
    public void footswitchPress (final int index)
    {
        this.log ( () -> "FS " + (index + 1) + " down");
        this.controlDown[PacerMap.NUM_SWITCHES + index] = true;
    }


    /**
     * @param index 0-3
     * @return True if the footswitch jack fires its tap on press
     */
    public boolean isFootswitchTapOnPress (final int index)
    {
        if (this.jackLoopTrack (index) >= 0)
            return this.looper.isLoopSwitchTapOnPress ();
        return TapTiming.actionTapOnPress (this.configuration.getFootswitchTap (index), this.configuration.getFootswitchHold (index));
    }


    /**
     * @param index 0-3
     * @return How much longer than a normal hold the jack must stay down before its hold runs
     */
    public long getFootswitchExtraHoldMillis (final int index)
    {
        if (this.jackLoopTrack (index) >= 0)
            return this.looper.getLoopSwitchExtraHoldMillis ();
        return this.getExtraHoldMillis (this.configuration.getFootswitchHold (index));
    }


    /**
     * @param index 0-3
     * @return True if a double-tap action is assigned to the jack
     */
    public boolean isFootswitchDoubleTapEnabled (final int index)
    {
        if (this.jackLoopTrack (index) >= 0)
            return this.looper.isLoopSwitchDoubleTapEnabled ();
        return this.configuration.getFootswitchDoubleTap (index) != Action.NONE;
    }


    /**
     * A footswitch jack was tapped.
     *
     * @param index 0-3
     */
    public void footswitchTap (final int index)
    {
        final int loopTrack = this.jackLoopTrack (index);
        if (loopTrack >= 0)
            this.looper.loopSwitchTap (loopTrack);
        else
            this.performFor (PacerMap.NUM_SWITCHES + index, this.configuration.getFootswitchTap (index));
    }


    /**
     * A footswitch jack was double-tapped.
     *
     * @param index 0-3
     */
    public void footswitchDoubleTap (final int index)
    {
        final int loopTrack = this.jackLoopTrack (index);
        if (loopTrack >= 0)
            this.looper.loopSwitchDoubleTap (loopTrack);
        else
            this.performFor (PacerMap.NUM_SWITCHES + index, this.configuration.getFootswitchDoubleTap (index));
    }


    /**
     * A footswitch jack was held.
     *
     * @param index 0-3
     */
    public void footswitchHold (final int index)
    {
        this.momentaryFootswitches[index] = null;
        final int loopTrack = this.jackLoopTrack (index);
        if (loopTrack >= 0)
        {
            this.looper.loopSwitchHold (loopTrack);
            return;
        }
        final Action hold = this.configuration.getFootswitchHold (index);
        if (hold == Action.MOMENTARY)
            this.momentaryFootswitches[index] = this.configuration.getFootswitchTap (index);
        else
            this.performFor (PacerMap.NUM_SWITCHES + index, hold);
    }


    /**
     * A footswitch jack was released.
     *
     * @param index 0-3
     */
    public void footswitchRelease (final int index)
    {
        final int control = PacerMap.NUM_SWITCHES + index;
        this.controlDown[control] = false;
        if (this.shift.release (control))
            this.shiftChanged ();
        final Action momentary = this.momentaryFootswitches[index];
        this.momentaryFootswitches[index] = null;
        if (momentary != null)
            this.performFor (control, momentary);
        final int loopTrack = this.jackLoopTrack (index);
        if (loopTrack >= 0)
            this.looper.loopSwitchRelease (loopTrack);
    }


    /**
     * A jack whose tap is a loop track is a loop switch, like a stomp switch would be: the Looper settings decide its
     * hold and double-tap, and hold to record works on it.
     *
     * @param index 0-3
     * @return The loop track, -1 if the jack is not a loop switch (or the project has too few loop tracks)
     */
    private int jackLoopTrack (final int index)
    {
        final int track = this.configuration.getFootswitchTap (index).getLoopTrack ();
        return track >= 0 && track < this.configuration.getLoopTrackCount () ? track : -1;
    }


    // ---- Expression pedals --------------------------------------------------------------------------------------

    /**
     * The parameter a pedal is bound to directly. Only parameter targets with a linear, full-range response are bound,
     * and only while the takeover jumps; everything else (MIDI, FX and loop targets, curves, ranges, pick-up) goes
     * through {@link #pedalMoved(int, int)}.
     *
     * @param index 0-1
     * @return The parameter, or null to route the pedal through its command
     */
    public IParameter getPedalBinding (final int index)
    {
        final ExpressionTarget target = this.getExpressionTarget (index);
        if (target.getKind () != ExpressionTarget.Kind.PARAMETER || !this.configuration.getPedalResponse (index).isIdentity () || this.isPickup ())
            return null;
        return this.looper.getExpressionParameter (target);
    }


    /**
     * A pedal was pointed at a new target (a mode change, a setting), so a pick-up starts over.
     *
     * @param index 0-1
     */
    public void pedalRetargeted (final int index)
    {
        this.pickups[index].reset ();
        this.targetKeys[index] = Long.MIN_VALUE;
    }


    /**
     * Hand back a pedal's controller if it is stale, then state where the pedal physically is under whatever it
     * drives now. Called whenever a pedal is re-bound: the active mode changed or a pedal setting was edited.
     * <p>
     * Both halves matter. Without the release the old controller stays parked on the instrument for good; without
     * the re-assert the receiver sits at neutral while the foot is still on the toe, and the next nudge jumps. With
     * <i>Pedal takeover = Pick up</i> there is no re-assert: the pedal has to reach the receiver's value first.
     *
     * @param index 0-1
     */
    public void repointPedal (final int index)
    {
        final ExpressionTarget target = this.getExpressionTarget (index);
        final int channel = this.configuration.getPedalMidiChannel ();
        final PedalHold hold = this.holds[index];
        if (hold.isStale (target, channel))
            this.release (hold);
        if (this.isPickup ())
            return;
        final PedalResponse response = this.configuration.getPedalResponse (index);
        this.send (hold.reassert (target, channel, response::map));
    }


    /**
     * Hand back whatever both pedals are holding. For teardown - exit, or a restart - where nothing will be driving
     * those controllers any more. Idempotent.
     */
    public void releasePedals ()
    {
        for (final PedalHold hold: this.holds)
            this.release (hold);
    }


    /** Hand back what a pedal holds; a pick-up then starts from the resting value the receiver was left at. */
    private void release (final PedalHold hold)
    {
        final ExpressionTarget target = hold.getTarget ();
        final int [] message = hold.release ();
        if (message == null)
            return;
        this.send (message);
        this.midiSent.put (target, Integer.valueOf (target.restingValue ()));
    }


    private void send (final int [] message)
    {
        if (message != null)
            this.midiSender.send (message[0], message[1], message[2]);
    }


    /**
     * An expression pedal moved and is not bound directly to a parameter.
     *
     * @param index 0-1
     * @param value The pedal position, 0-127
     */
    public void pedalMoved (final int index, final int value)
    {
        final ExpressionTarget target = this.getExpressionTarget (index);
        final PedalResponse response = this.configuration.getPedalResponse (index);
        final double output = response.map (value / 127.0);
        if (this.isPickup ())
        {
            // The same setting can mean another target now: another instrument focused, another track selected, the
            // loop track window moved. That one has to be picked up again.
            final long key = this.getTargetKey (target);
            if (key != this.targetKeys[index])
            {
                this.pickups[index].reset ();
                this.targetKeys[index] = key;
            }
        }

        switch (target.getKind ())
        {
            case CC, CHANNEL_PRESSURE, PITCH_BEND_UP -> {
                final int mapped = response.map (value);
                // Nothing sent yet: there is no value to reach, so the pedal follows at once
                final Integer last = this.midiSent.get (target);
                if (!this.pickUp (index, mapped / 127.0, last == null ? mapped / 127.0 : last.intValue () / 127.0))
                    return;
                // Recorded as well as sent: whatever goes out here stays on the instrument until something hands
                // it back
                this.send (this.holds[index].press (target, this.configuration.getPedalMidiChannel (), value, mapped));
                this.midiSent.put (target, Integer.valueOf (mapped));
            }
            case FX_REMOTE -> {
                if (this.pickUp (index, output, this.fx.getRemoteValue (target.getRemoteIndex ())))
                    this.fx.setRemoteValue (target.getRemoteIndex (), output);
            }
            case PARAMETER -> this.moveParameter (index, this.looper.getExpressionParameter (target), output);
            // A new recording is a new target, which the target key picks up again
            case ACTIVE_LOOP -> this.moveParameter (index, this.looper.getLoopVolume (this.looper.getActiveLoop ()), output);
            case ALL_LOOPS -> {
                if (this.pickUp (index, output, this.looper.getLoopsLevel ()))
                    this.looper.setLoopsLevel (output);
            }
            case NONE -> {
                // Not assigned
            }
        }
    }


    private void moveParameter (final int index, final IParameter parameter, final double output)
    {
        if (parameter != null && this.pickUp (index, output, this.isPickup () ? this.looper.getNormalizedValue (parameter) : Double.NaN))
            parameter.setNormalizedValue (output);
    }


    /** With a jumping takeover everything goes through; with pick-up, only once the pedal reached the target. */
    private boolean pickUp (final int index, final double output, final double current)
    {
        return !this.isPickup () || this.pickups[index].accept (output, current);
    }


    private long getTargetKey (final ExpressionTarget target)
    {
        return switch (target.getKind ())
        {
            case FX_REMOTE -> this.configuration.getFocusedInstrument ();
            case PARAMETER, ACTIVE_LOOP, ALL_LOOPS -> this.looper.getTargetKey (target);
            case CC, CHANNEL_PRESSURE, PITCH_BEND_UP, NONE -> 0;
        };
    }


    private boolean isPickup ()
    {
        return this.configuration.getPedalTakeover () == PedalTakeover.PICKUP;
    }


    // ---- LEDs ---------------------------------------------------------------------------------------------------

    /**
     * Get the light code of a switch right now. With the colours written live, this only decides whether the LED is
     * lit: the colour itself comes from {@link LiveBoard}.
     *
     * @param switchIndex 0-9
     * @return The code, see {@link LedState#code(LedClock)}
     */
    public int getLedCode (final int switchIndex)
    {
        final long now = System.currentTimeMillis ();
        final int testCode = this.looper.getLedTestCode (now);
        this.animated[switchIndex] = testCode >= 0;
        if (testCode >= 0)
        {
            this.switchColours[switchIndex] = LedColour.fromCode (testCode).toPacer ();
            return testCode;
        }

        if (this.modes.isMenuOpen ())
            // The menu paints its own colours; the code only says lit or dark. The cached state colours are left
            // alone, so closing the menu does not first repaint every switch in its bare mode colour.
            return ModeMenu.colourAt (switchIndex, this.modes.getActive (), this.modes::isOffered) == PacerColour.OFF ? 0 : LedColour.WHITE.ordinal ();

        final ModeBoard shown = this.getBoard ();
        // The beat counter lights the top row on the boards made for looping, but never over a loop switch
        if (switchIndex >= PacerMap.FIRST_TOP_ROW_SWITCH && shown.countsBeats () && !shown.isLoopSwitch (switchIndex))
        {
            final int beatCode = this.looper.getBeatCounterCode (switchIndex - PacerMap.FIRST_TOP_ROW_SWITCH, now);
            if (beatCode >= 0)
            {
                this.animated[switchIndex] = true;
                // The counter is dark on the beats that are not this switch's. Only a lit code carries a colour;
                // caching the dark ones would flip every switch's colour on every beat, and each flip is a SysEx.
                if (beatCode > 0)
                    this.switchColours[switchIndex] = LedColour.fromCode (beatCode).toPacer ();
                return beatCode;
            }
        }

        final LedClock ledClock = this.looper.getLedClock (now);
        final LedState state = switch (this.role (switchIndex))
        {
            case LOOP_TRACK -> this.looper.loopSwitchLed (shown.getLoopTrack (switchIndex));
            // The mode switch is always lit: it is the way back to everything else. It turns gold while the shift
            // layer is up, and blinks while that is for one press only.
            case MODE_SWITCH -> this.shiftLed ();
            case ACTION -> {
                // The board showing now, not the one a held switch was pressed on
                final Action action = shown.getLayout (switchIndex).tap ();
                if (action.isShift ())
                    yield this.shift.isOn () ? this.shiftLed () : LedState.DARK;
                yield action.isFx () ? this.fx.actionLed (action) : this.looper.actionLed (action, ledClock);
            }
            default -> LedState.DARK;
        };
        this.switchColours[switchIndex] = state.colour ().toPacer ();
        this.animated[switchIndex] = state.colour () != LedColour.OFF && state.pattern () != LedPattern.SOLID;
        return state.code (ledClock);
    }


    /**
     * @return True if any LED showed a moving pattern in the last flush - a blink, the beat counter, the LED test - so
     *         the LEDs have to be flushed regularly; otherwise Bitwig's own flushes on state changes are enough
     */
    public boolean isAnimating ()
    {
        for (final boolean moving: this.animated)
            if (moving)
                return true;
        return false;
    }


    private LedState shiftLed ()
    {
        return switch (this.shift.getState ())
        {
            case OFF -> LedState.solid (LedColour.WHITE);
            case ONCE -> new LedState (LedColour.AMBER, LedPattern.BLINK_MEDIUM);
            case ON, HELD -> LedState.solid (LedColour.AMBER);
        };
    }


    // ---- Presets and periodic work ----------------------------------------------------------------------------------

    /**
     * The preset-loaded CC arrived. Selecting a preset on the Pacer discards every live edit, so the whole board has
     * to be written again.
     *
     * @param value The CC value
     */
    public void presetAnnounced (final int value)
    {
        // The Bitwig preset (127) arrives whenever it is selected or the Pacer starts up, and coming back to it must
        // not throw away the mode you were in. Only the retired FX preset (17, 18), still on some Pacers, asks for a
        // mode of its own.
        this.log ( () -> "preset announced (CC 119 = " + value + ")");
        final boolean fxPreset = PresetAnnouncement.fromValue (value).kind () == PresetKind.FX;
        this.board.invalidate ();
        if (fxPreset && this.modes.activate (Mode.FX))
            this.modeChanged ();
        else
            this.paint ();
    }


    /**
     * Runs on every tick.
     */
    public void tick ()
    {
        this.tickLooper ();
        this.tickFx ();
        // Colours follow state; the board drops everything that would not change, so this is almost always silent
        this.paint ();
    }


    /**
     * The looper's part of the tick. The setup runs each part on its own, so one failing does not stop the others.
     */
    public void tickLooper ()
    {
        this.looper.tick ();
    }


    /**
     * The FX mode's part of the tick.
     */
    public void tickFx ()
    {
        this.fx.tick ();
    }


    /**
     * Run an assignable action.
     *
     * @param action The action
     */
    public void perform (final Action action)
    {
        if (action != Action.NONE)
            this.log ( () -> "action " + action + (this.performingControl >= 0 ? " (" + this.controlName (this.performingControl) + ")" : ""));
        if (action.isShift ())
        {
            this.performShift (action);
            return;
        }
        if (action.isMode ())
        {
            if (this.performMode (action))
                this.modeChanged ();
            return;
        }
        if (action.isFx ())
            this.fx.perform (action);
        else
            this.looper.perform (action);
    }


    private boolean performMode (final Action action)
    {
        return switch (action)
        {
            case MODE_NEXT -> this.modes.next ();
            case MODE_TOGGLE -> this.modes.toggle ();
            case MODE_LOOP -> this.modes.activate (Mode.LOOP);
            case MODE_FX -> this.modes.activate (Mode.FX);
            case MODE_MIX -> this.modes.activate (Mode.MIX);
            case MODE_SONG -> this.modes.activate (Mode.SONG);
            // While the custom layout changes a built-in mode, that mode is where it lives
            case MODE_CUSTOM -> this.modes.activate (this.configuration.getCustomBoard ().getTarget ().getMode ());
            default -> false;
        };
    }


    /** Run an action for a switch or jack, which "shift while held" ties the layer to. */
    private void performFor (final int control, final Action action)
    {
        this.performingControl = control;
        try
        {
            this.perform (action);
        }
        finally
        {
            this.performingControl = -1;
        }
    }


    private void performShift (final Action action)
    {
        switch (action)
        {
            case SHIFT_TOGGLE -> this.shift.toggle ();
            case SHIFT_ONCE -> this.shift.once ();
            case SHIFT_HOLD -> {
                // Only a control that is still down can hold the layer up. Anywhere else - a double-tap that fires
                // on release, a mode's own action slot - there is no release coming, so it latches instead.
                final int control = this.performingControl;
                if (control >= 0 && this.controlDown[control])
                    this.shift.hold (control);
                else
                    this.shift.toggle ();
            }
            default -> {
                return;
            }
        }
        this.shiftChanged ();
    }


    /** The shift layer went up or down: the switches show the other layer at once. */
    private void shiftChanged ()
    {
        this.log ( () -> "shift " + this.shift.getState ());
        if (this.shift.isOn ())
            this.showEvent ("SHIFT");
        this.repaintSwitches ();
    }


    // ---- Helpers ------------------------------------------------------------------------------------------------

    /**
     * Paint after the board changed under the switches (a mode change, the menu closing). The cached colours are the
     * last flush's answers for the old board, so they are worked out again first - once per change, not per flush -
     * and the Pacer gets one burst with the right colours rather than a wrong one and then a correction.
     */
    private void repaintSwitches ()
    {
        this.refreshColours ();
        this.paint ();
    }


    private void refreshColours ()
    {
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
            this.getLedCode (i);
    }


    /** The mode decides which switches are loop tracks, the project decides how many tracks there are. */
    private SwitchRole role (final int switchIndex)
    {
        return SwitchRole.of (switchIndex, this.getBoard (), this.modes.isMenuOpen (), this.configuration.getLoopTrackCount (), this.modes::isOffered);
    }


    /** The role the switch had when it was pressed; before its first press, its role now. */
    private SwitchRole pressRole (final int switchIndex)
    {
        final SwitchRole latched = this.pressRoles[switchIndex];
        return latched == null ? this.role (switchIndex) : latched;
    }


    /** The loop track of a switch on the board it was pressed on. */
    private int pressLoopTrack (final int switchIndex)
    {
        final ModeBoard latched = this.pressBoards[switchIndex];
        return (latched == null ? this.getBoard () : latched).getLoopTrack (switchIndex);
    }


    /** The layout of the switch on the board it was pressed on. */
    private SwitchLayout getLayout (final int switchIndex)
    {
        final ModeBoard latched = this.pressBoards[switchIndex];
        return (latched == null ? this.getBoard () : latched).getLayout (switchIndex);
    }


    private Action getSwitchTap (final int switchIndex)
    {
        return this.getLayout (switchIndex).tap ();
    }


    private Action getSwitchDoubleTap (final int switchIndex)
    {
        return this.getLayout (switchIndex).doubleTap ();
    }


    private Action getSwitchHold (final int switchIndex)
    {
        return this.getLayout (switchIndex).hold ();
    }


    private void log (final Supplier<String> line)
    {
        this.diagnostics.log (Diagnostics.Level.ACTIONS, line);
    }


    private String controlName (final int control)
    {
        return control < PacerMap.NUM_SWITCHES ? PacerMap.SWITCH_NAMES[control] : "FS " + (control - PacerMap.NUM_SWITCHES + 1);
    }


    private ExpressionTarget getExpressionTarget (final int index)
    {
        return this.configuration.getExpressionTarget (this.getMode (), index);
    }


    private long getExtraHoldMillis (final Action hold)
    {
        return hold.isDestructive () ? this.configuration.getClearHoldTime ().getExtraMillis () : 0;
    }

}
