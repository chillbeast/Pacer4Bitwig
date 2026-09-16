// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.util.Labelled;

import static dev.pacer4bitwig.pacer.mode.SwitchLayout.LOOP_TRACK;
import static dev.pacer4bitwig.pacer.mode.SwitchLayout.MODE_SWITCH;
import static dev.pacer4bitwig.pacer.mode.SwitchLayout.of;
import static dev.pacer4bitwig.pacer.mode.SwitchLayout.on;


/**
 * A board of switch assignments the extension applies to the Pacer live (docs/LIVE-COLOURS-AND-MODES.md). The Pacer
 * stays on one preset and its stored memory is never touched: switching modes only repaints the switches and writes
 * the mode's name to the display.
 * <p>
 * Modes are fixed for now. Settings come later, where they turn out to matter.
 */
public enum Mode implements Labelled, ModeBoard
{
    /** The looper: five loop tracks plus the transport essentials. */
    LOOP ("LOOP", "Looper", 5, new SwitchLayout []
    {
        LOOP_TRACK,
        LOOP_TRACK,
        LOOP_TRACK,
        LOOP_TRACK,
        LOOP_TRACK,
        MODE_SWITCH,
        of (Action.UNDO, Action.REDO, PacerColour.WHITE),
        of (Action.PLAY_STOP_ALL, Action.CLEAR_ROW, PacerColour.GOLD),
        of (Action.LAUNCHER_OVERDUB, Action.METRONOME, PacerColour.RED),
        of (Action.TAP_TEMPO, Action.TRANSPORT_PLAY_STOP, PacerColour.CYAN)
    }),

    /** The pedalboard: instruments on the bottom row, their FX switches on the top row. */
    FX ("FX", "FX pedalboard", 0, new SwitchLayout []
    {
        of (Action.FOCUS_A, Action.MUTE_A, Action.ASSIGN_A, PacerColour.WHITE),
        of (Action.FOCUS_B, Action.MUTE_B, Action.ASSIGN_B, PacerColour.WHITE),
        of (Action.FOCUS_C, Action.MUTE_C, Action.ASSIGN_C, PacerColour.WHITE),
        of (Action.FOCUS_D, Action.MUTE_D, Action.ASSIGN_D, PacerColour.WHITE),
        of (Action.SNAPSHOT_NEXT, Action.SNAPSHOT_FIRST, Action.SNAPSHOT_STORE, PacerColour.MAGENTA),
        MODE_SWITCH,
        of (Action.FX_1, Action.MOMENTARY, PacerColour.GREEN),
        of (Action.FX_2, Action.MOMENTARY, PacerColour.GREEN),
        of (Action.FX_3, Action.MOMENTARY, PacerColour.GREEN),
        of (Action.FX_4, Action.MOMENTARY, PacerColour.GREEN)
    }),

    /**
     * The mixer. SW 1-4 sit under the printed words {@code Solo}, {@code Mute}, {@code Rec Arm} and {@code Click}
     * and light that word, so the panel labels itself (docs/PACER-MAP.md).
     */
    MIX ("MIX", "Mixer", 0, new SwitchLayout []
    {
        on (Action.SOLO_SELECTED, Action.NONE, PacerColour.YELLOW, LedRow.WORD),
        on (Action.MUTE_SELECTED, Action.NONE, PacerColour.BLUE, LedRow.WORD),
        on (Action.MONITOR_SELECTED, Action.NONE, PacerColour.RED, LedRow.WORD),
        on (Action.METRONOME, Action.NONE, PacerColour.WHITE, LedRow.WORD),
        of (Action.SELECT_NEXT_LOOP, Action.SELECT_PREVIOUS_LOOP, PacerColour.LAVENDER),
        MODE_SWITCH,
        of (Action.MUTE_ALL_TOGGLE, Action.RESET, PacerColour.BLUE),
        of (Action.FADE_OUT, Action.FADE_IN, PacerColour.ORANGE),
        of (Action.STOP_ALL, Action.PLAY_ROW, PacerColour.GOLD),
        of (Action.SHOW_STATUS, Action.LED_TEST, PacerColour.WHITE)
    }),

    /**
     * Building a song out of rows. SW 1-5 sit under the printed transport icons - loop, rewind, fast forward, stop
     * and play - and light those rather than the colour strip, so the panel labels itself a second time.
     */
    SONG ("SONG", "Song", 0, new SwitchLayout []
    {
        on (Action.PLAY_STOP_ALL, Action.CLEAR_ROW, PacerColour.GREEN, LedRow.ICON),
        on (Action.ROW_PREVIOUS, Action.NONE, PacerColour.LAVENDER, LedRow.ICON),
        on (Action.ROW_NEXT, Action.NONE, PacerColour.LAVENDER, LedRow.ICON),
        on (Action.STOP_ALL, Action.FADE_OUT, PacerColour.RED, LedRow.ICON),
        on (Action.TRANSPORT_PLAY_STOP, Action.TAP_TEMPO, PacerColour.GOLD, LedRow.ICON),
        MODE_SWITCH,
        of (Action.DUPLICATE_ROW, Action.CLEAR_ROW, PacerColour.CYAN),
        of (Action.TRACKS_HERE, Action.NONE, PacerColour.LAVENDER),
        of (Action.UNDO, Action.REDO, PacerColour.WHITE),
        of (Action.SHOW_STATUS, Action.RESET, PacerColour.WHITE)
    }),

    /**
     * Laid out entirely in the settings. Its own layouts here are placeholders and are never read: the controller
     * swaps in the board built from the settings (see {@code PacerConfiguration.getCustomBoard}).
     */
    CUSTOM ("CUST", "Custom", 0, new SwitchLayout []
    {
        SwitchLayout.EMPTY,
        SwitchLayout.EMPTY,
        SwitchLayout.EMPTY,
        SwitchLayout.EMPTY,
        SwitchLayout.EMPTY,
        MODE_SWITCH,
        SwitchLayout.EMPTY,
        SwitchLayout.EMPTY,
        SwitchLayout.EMPTY,
        SwitchLayout.EMPTY
    });


    /** SW 6 is the mode switch in every mode: tap toggles, hold opens the menu. */
    public static final int      MODE_SWITCH_INDEX = 5;

    private final String         displayName;
    private final String         label;
    private final int            loopSwitches;
    private final SwitchLayout [] layouts;


    Mode (final String displayName, final String label, final int loopSwitches, final SwitchLayout [] layouts)
    {
        if (layouts.length != PacerMap.NUM_SWITCHES)
            throw new IllegalArgumentException (label + " must lay out all " + PacerMap.NUM_SWITCHES + " switches");
        this.displayName = displayName;
        this.label = label;
        this.loopSwitches = loopSwitches;
        this.layouts = layouts;
    }


    /** {@inheritDoc} */
    @Override
    public String getDisplayName ()
    {
        return this.displayName;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /** {@inheritDoc} */
    @Override
    public SwitchLayout getLayout (final int switchIndex)
    {
        return this.layouts[switchIndex];
    }


    /** {@inheritDoc} */
    @Override
    public int getLoopSwitches ()
    {
        return this.loopSwitches;
    }


    /**
     * The mode after this one, wrapping round. Only modes that have a menu slot are reachable.
     *
     * @return The next mode
     */
    public Mode next ()
    {
        final Mode [] all = values ();
        for (int step = 1; step <= all.length; step++)
        {
            final Mode candidate = all[(this.ordinal () + step) % all.length];
            if (ModeMenu.slotOf (candidate) >= 0)
                return candidate;
        }
        return this;
    }


    /**
     * @param switchIndex 0-9
     * @return True if the switch is the mode switch, which never runs a mode's action
     */
    public static boolean isModeSwitch (final int switchIndex)
    {
        return switchIndex == MODE_SWITCH_INDEX;
    }
}
