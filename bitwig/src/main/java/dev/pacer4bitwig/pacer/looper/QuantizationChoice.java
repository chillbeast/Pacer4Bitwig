// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import de.mossgrabers.framework.daw.constants.LaunchQuantization;

import dev.pacer4bitwig.util.Labelled;


/**
 * The default launch quantization to push into the project. It also decides where recorded loops start and end.
 */
public enum QuantizationChoice implements Labelled
{
    /** Leave the project setting alone. */
    KEEP ("Keep project setting", null),
    /** None. */
    NONE ("None", LaunchQuantization.RES_NONE),
    /** 1/16. */
    SIXTEENTH ("1/16", LaunchQuantization.RES_1_16),
    /** 1/8. */
    EIGHTH ("1/8", LaunchQuantization.RES_1_8),
    /** 1/4. */
    QUARTER ("1/4", LaunchQuantization.RES_1_4),
    /** 1/2. */
    HALF ("1/2", LaunchQuantization.RES_1_2),
    /** 1 bar. */
    BAR_1 ("1 bar", LaunchQuantization.RES_1),
    /** 2 bars. */
    BARS_2 ("2 bars", LaunchQuantization.RES_2),
    /** 4 bars. */
    BARS_4 ("4 bars", LaunchQuantization.RES_4),
    /** 8 bars. */
    BARS_8 ("8 bars", LaunchQuantization.RES_8);


    private final String             label;
    private final LaunchQuantization quantization;


    QuantizationChoice (final String label, final LaunchQuantization quantization)
    {
        this.label = label;
        this.quantization = quantization;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * Get the quantization.
     *
     * @return The quantization, null for KEEP
     */
    public LaunchQuantization getQuantization ()
    {
        return this.quantization;
    }
}
