// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * How quickly the second tap of a double-tap must follow the first.
 */
public enum DoubleTapWindow implements Labelled
{
    /** 0.25 s. */
    FAST ("Fast (0.25 s)", 250),
    /** 0.35 s. */
    NORMAL ("Normal (0.35 s)", 350),
    /** 0.5 s. */
    RELAXED ("Relaxed (0.5 s)", 500);


    private final String label;
    private final long   millis;


    DoubleTapWindow (final String label, final long millis)
    {
        this.label = label;
        this.millis = millis;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return The window in milliseconds
     */
    public long getMillis ()
    {
        return this.millis;
    }
}
