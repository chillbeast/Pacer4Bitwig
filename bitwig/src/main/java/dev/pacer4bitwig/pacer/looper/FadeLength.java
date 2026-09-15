// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * How long the fade actions take.
 */
public enum FadeLength implements Labelled
{
    /** 1 bar. */
    BAR_1 ("1 bar", 1),
    /** 2 bars. */
    BARS_2 ("2 bars", 2),
    /** 4 bars. */
    BARS_4 ("4 bars", 4),
    /** 8 bars. */
    BARS_8 ("8 bars", 8);


    private final String label;
    private final int    bars;


    FadeLength (final String label, final int bars)
    {
        this.label = label;
        this.bars = bars;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return The length in bars
     */
    public int getBars ()
    {
        return this.bars;
    }
}
