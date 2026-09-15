// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * What holding a loop switch does.
 */
public enum HoldAction implements Labelled
{
    /** Stop the track and delete the clip. */
    DELETE ("Delete the clip"),
    /** Stop the track. */
    STOP ("Stop"),
    /** Ignore. */
    NOTHING ("Nothing");


    private final String label;


    HoldAction (final String label)
    {
        this.label = label;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }
}
