// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * Fixed-length recording, implemented with Bitwig's post-recording action ("play recorded" after N beats).
 */
public enum LoopLength implements Labelled
{
    /** Leave the project's post-recording settings alone. */
    KEEP ("Keep project setting", 0),
    /** Recording runs until the loop switch is pressed again. */
    FREE ("Free: press again to close the loop", 0),
    /** The first loop of a row is free; every later loop closes automatically after the same number of bars. */
    MATCH_FIRST ("Match the first loop of the row", 0),
    /** 1 bar. */
    BARS_1 ("1 bar", 1),
    /** 2 bars. */
    BARS_2 ("2 bars", 2),
    /** 4 bars. */
    BARS_4 ("4 bars", 4),
    /** 8 bars. */
    BARS_8 ("8 bars", 8);


    private final String label;
    private final int    bars;


    LoopLength (final String label, final int bars)
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
     * Get the length in bars.
     *
     * @return The bars, 0 if not fixed
     */
    public int getBars ()
    {
        return this.bars;
    }
}
