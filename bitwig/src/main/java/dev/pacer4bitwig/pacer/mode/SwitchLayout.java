// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;


/**
 * What one switch does in one mode, and how it looks. A switch whose tap is a loop track action ({@link #loop(int)})
 * is a loop switch: its behaviour - hold, double-tap, hold to record - comes from the looper, so its own hold and
 * double-tap are ignored.
 *
 * @param tap The tap action
 * @param doubleTap The double-tap action
 * @param hold The hold action
 * @param colour The colour the switch is painted when the mode becomes active
 * @param row Which of the switch's LEDs lights
 */
public record SwitchLayout (Action tap, Action doubleTap, Action hold, PacerColour colour, LedRow row)
{
    /** The colour loop switches rest at, dimmed, while their slot is empty. */
    public static final PacerColour  LOOP_COLOUR = PacerColour.DARK_GREEN;
    /** SW 6, which is the mode switch in every mode and never runs a mode's action. */
    public static final SwitchLayout MODE_SWITCH = new SwitchLayout (Action.NONE, Action.NONE, Action.NONE, PacerColour.WHITE, LedRow.STRIP);
    /** An unused switch. */
    public static final SwitchLayout EMPTY       = new SwitchLayout (Action.NONE, Action.NONE, Action.NONE, PacerColour.OFF, LedRow.STRIP);


    /**
     * A loop switch.
     *
     * @param track The loop track it stands for, 0-7
     * @return The layout
     */
    public static SwitchLayout loop (final int track)
    {
        return loop (track, LOOP_COLOUR);
    }


    /**
     * A loop switch resting in a colour of its own, e.g. the loops a shift layer adds.
     *
     * @param track The loop track it stands for, 0-7
     * @param colour The colour it rests at, dimmed, while its slot is empty
     * @return The layout
     */
    public static SwitchLayout loop (final int track, final PacerColour colour)
    {
        return new SwitchLayout (Action.loopTrack (track), Action.NONE, Action.NONE, colour, LedRow.STRIP);
    }


    /**
     * @return True if no gesture does anything - on a shift layer, the switch keeps its normal job
     */
    public boolean isUnassigned ()
    {
        return this.tap == Action.NONE && this.doubleTap == Action.NONE && this.hold == Action.NONE;
    }


    /**
     * @return The loop track this switch stands for, -1 if it is not a loop switch
     */
    public int loopTrack ()
    {
        return this.tap.getLoopTrack ();
    }


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
