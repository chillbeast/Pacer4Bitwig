// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;


/**
 * What one switch does in one mode, and how it looks. Loop switches ({@link #LOOP_TRACK}) ignore the actions: their
 * behaviour comes from the loop track they stand for.
 *
 * @param tap The tap action
 * @param doubleTap The double-tap action
 * @param hold The hold action
 * @param colour The colour the switch is painted when the mode becomes active
 * @param row Which of the switch's LEDs lights
 */
public record SwitchLayout (Action tap, Action doubleTap, Action hold, PacerColour colour, LedRow row)
{
    /** A loop switch: it stands for a loop track, so its actions come from the looper. */
    public static final SwitchLayout LOOP_TRACK  = new SwitchLayout (Action.NONE, Action.NONE, Action.NONE, PacerColour.DARK_GREEN, LedRow.STRIP);
    /** SW 6, which is the mode switch in every mode and never runs a mode's action. */
    public static final SwitchLayout MODE_SWITCH = new SwitchLayout (Action.NONE, Action.NONE, Action.NONE, PacerColour.WHITE, LedRow.STRIP);
    /** An unused switch. */
    public static final SwitchLayout EMPTY       = new SwitchLayout (Action.NONE, Action.NONE, Action.NONE, PacerColour.OFF, LedRow.STRIP);


    /**
     * A switch with a tap and a hold action, lit on its colour strip.
     *
     * @param tap The tap action
     * @param hold The hold action
     * @param colour The colour
     * @return The layout
     */
    public static SwitchLayout of (final Action tap, final Action hold, final PacerColour colour)
    {
        return new SwitchLayout (tap, Action.NONE, hold, colour, LedRow.STRIP);
    }


    /**
     * A switch with all three gestures, lit on its colour strip.
     *
     * @param tap The tap action
     * @param doubleTap The double-tap action
     * @param hold The hold action
     * @param colour The colour
     * @return The layout
     */
    public static SwitchLayout of (final Action tap, final Action doubleTap, final Action hold, final PacerColour colour)
    {
        return new SwitchLayout (tap, doubleTap, hold, colour, LedRow.STRIP);
    }


    /**
     * A switch lit on a row other than its colour strip, e.g. the printed word when the function matches it.
     *
     * @param tap The tap action
     * @param hold The hold action
     * @param colour The colour
     * @param row The row
     * @return The layout
     */
    public static SwitchLayout on (final Action tap, final Action hold, final PacerColour colour, final LedRow row)
    {
        return new SwitchLayout (tap, Action.NONE, hold, colour, row);
    }
}
