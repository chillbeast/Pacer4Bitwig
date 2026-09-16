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
     * @return How many of the leading switches stand for loop tracks
     */
    int getLoopSwitches ();


    /**
     * @param switchIndex 0-9
     * @return True if the switch stands for a loop track. SW 6 never does: it is the mode switch.
     */
    default boolean isLoopSwitch (final int switchIndex)
    {
        return switchIndex < this.getLoopSwitches () && !Mode.isModeSwitch (switchIndex);
    }
}
