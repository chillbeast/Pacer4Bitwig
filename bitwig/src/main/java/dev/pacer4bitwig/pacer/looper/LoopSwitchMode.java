// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * How a loop switch records.
 */
public enum LoopSwitchMode implements Labelled
{
    /** Tap an empty slot to record, tap again to close. */
    TAP ("Tap to record, tap again to close"),
    /** Hold an empty slot's switch to record, release it to close. */
    HOLD_TO_RECORD ("Hold to record, release to close");


    private final String label;


    LoopSwitchMode (final String label)
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
