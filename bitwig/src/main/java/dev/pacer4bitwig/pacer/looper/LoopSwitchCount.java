// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * How many of the bottom switches are loop switches (SW 1 upwards). The others take their assigned actions.
 */
public enum LoopSwitchCount implements Labelled
{
    /** No loop switches: all bottom switches are assignable. */
    NONE ("None (all bottom switches assignable)", 0),
    /** SW 1. */
    ONE ("SW 1", 1),
    /** SW 1-2. */
    TWO ("SW 1-2", 2),
    /** SW 1-3. */
    THREE ("SW 1-3", 3),
    /** SW 1-4. */
    FOUR ("SW 1-4", 4),
    /** SW 1-5. */
    FIVE ("SW 1-5", 5),
    /** SW 1-6. */
    SIX ("SW 1-6", 6);


    private final String label;
    private final int    count;


    LoopSwitchCount (final String label, final int count)
    {
        this.label = label;
        this.count = count;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return The number of loop switches, 0-6
     */
    public int getCount ()
    {
        return this.count;
    }
}
