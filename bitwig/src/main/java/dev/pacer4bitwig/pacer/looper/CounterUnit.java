// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * What the top-row counter counts while a loop records. Count-ins always count beats.
 */
public enum CounterUnit implements Labelled
{
    /** SW A-D light beat 1-4 of the bar. */
    BEATS ("Beats of the bar"),
    /** SW A-D light bar 1-4 since the recording started, then 5-8 on A-D again, and so on. */
    BARS ("Bars since the recording started");


    private final String label;


    CounterUnit (final String label)
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
