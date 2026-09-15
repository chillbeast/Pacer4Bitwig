// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * How long a switch must be held before a hold action that deletes loops runs. The framework reports a hold after
 * half a second; longer times wait that much more while the switch stays down.
 */
public enum ClearHoldTime implements Labelled
{
    /** Like every other hold. */
    NORMAL ("Normal (0.5 s)", 0),
    /** One more second. */
    LONG ("Long (1.5 s)", 1000),
    /** Two more seconds. */
    VERY_LONG ("Very long (2.5 s)", 2000);


    private final String label;
    private final long   extraMillis;


    ClearHoldTime (final String label, final long extraMillis)
    {
        this.label = label;
        this.extraMillis = extraMillis;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return How much longer than a normal hold to wait
     */
    public long getExtraMillis ()
    {
        return this.extraMillis;
    }
}
