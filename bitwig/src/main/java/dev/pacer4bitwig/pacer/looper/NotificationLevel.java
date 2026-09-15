// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * Which pop-up notifications the looper shows.
 */
public enum NotificationLevel implements Labelled
{
    /** Everything, including confirmations like "Undo". */
    ALL ("All"),
    /** Navigation, warnings and state you cannot see on the Pacer (rows, loop lengths, count-ins, status). */
    IMPORTANT ("Only important ones"),
    /** None. */
    OFF ("Off");


    private final String label;


    NotificationLevel (final String label)
    {
        this.label = label;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @param important True for an important notification
     * @return True if it should be shown
     */
    public boolean shows (final boolean important)
    {
        return this == ALL || this == IMPORTANT && important;
    }
}
