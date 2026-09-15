// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * How many of the bottom switches are loop switches. The others take their assigned actions.
 */
public enum SwitchLayout implements Labelled
{
    /** SW 1-4 loops, SW 5 and SW 6 assignable. */
    FOUR_LOOPS ("SW 1-4 loops, SW 5-6 assignable", 4),
    /** SW 1-6 loops. */
    SIX_LOOPS ("SW 1-6 loops", 6);


    private final String label;
    private final int    loopCount;


    SwitchLayout (final String label, final int loopCount)
    {
        this.label = label;
        this.loopCount = loopCount;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return The number of loop switches, 4 or 6
     */
    public int getLoopCount ()
    {
        return this.loopCount;
    }


    /**
     * @param switchIndex 0-9 (SW 1-6, SW A-D)
     * @return True if the switch is a loop switch in this layout
     */
    public boolean isLoopSwitch (final int switchIndex)
    {
        return switchIndex < this.loopCount;
    }
}
