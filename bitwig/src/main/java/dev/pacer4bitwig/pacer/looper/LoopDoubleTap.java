// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * What a second tap on a loop switch does. The first tap has already done its normal job by then, so these are
 * chosen to make sense afterwards (e.g. clear whatever the first tap started).
 */
public enum LoopDoubleTap implements Labelled
{
    /** A second tap is just another tap. */
    NOTHING ("Nothing (just another tap)"),
    /** Stop the track. */
    STOP ("Stop the loop"),
    /** Mute or unmute the track. */
    MUTE ("Mute/unmute the loop"),
    /** Stop the track and delete the clip. */
    CLEAR ("Clear the loop"),
    /** Undo. */
    UNDO ("Undo");


    private final String label;


    LoopDoubleTap (final String label)
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
