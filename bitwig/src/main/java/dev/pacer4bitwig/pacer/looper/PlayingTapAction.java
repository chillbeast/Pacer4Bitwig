// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * What tapping a playing loop does.
 */
public enum PlayingTapAction implements Labelled
{
    /** Stop the track (quantized). */
    STOP ("Stop"),
    /** Mute/unmute: the clip keeps running, so it comes back in sync. */
    MUTE ("Mute/unmute (the loop keeps running)"),
    /** Toggle the launcher overdub - note clips only, Bitwig does not overdub audio in the launcher. */
    OVERDUB ("Toggle launcher overdub (note clips)"),
    /** Ignore. */
    NOTHING ("Nothing");


    private final String label;


    PlayingTapAction (final String label)
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
