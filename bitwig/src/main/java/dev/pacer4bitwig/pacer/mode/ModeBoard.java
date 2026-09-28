// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

/**
 * A board of switch assignments: what every switch does, what colour it is and which of its LEDs lights.
 * <p>
 * The built-in {@link Mode}s are their own boards. {@link Mode#CUSTOM} is not - its board is built from the
 * settings, so the user can lay the whole thing out. Everything that reads a layout goes through this interface so
 * the two are interchangeable.
 */
public interface ModeBoard
{
    /**
     * @return The name written to the Pacer's display, at most five characters
     */
    String getDisplayName ();


    /**
     * @param switchIndex 0-9
     * @return What the switch does
     */
    SwitchLayout getLayout (int switchIndex);


    /**
     * @param switchIndex 0-9
     * @return The loop track the switch stands for, -1 if it is not a loop switch. SW 6 never is: it is the mode switch.
     */
    default int getLoopTrack (final int switchIndex)
    {
        return Mode.isModeSwitch (switchIndex) ? -1 : this.getLayout (switchIndex).loopTrack ();
    }


    /**
     * @param switchIndex 0-9
     * @return True if the switch stands for a loop track
     */
    default boolean isLoopSwitch (final int switchIndex)
    {
        return this.getLoopTrack (switchIndex) >= 0;
    }


    /**
     * The beat counter takes over the top row while counting in and recording, on the boards made for looping.
     *
     * @return True if SW A-D count the beats here (those of them that are not loop switches)
     */
    default boolean countsBeats ()
    {
        return false;
    }
}
