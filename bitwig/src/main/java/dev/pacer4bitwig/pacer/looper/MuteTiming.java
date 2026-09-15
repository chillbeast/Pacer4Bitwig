// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * When mute changes from the looper take effect.
 */
public enum MuteTiming implements Labelled
{
    /** Right away. */
    IMMEDIATE ("Immediately"),
    /** On the next beat. */
    NEXT_BEAT ("On the next beat"),
    /** On the next downbeat. */
    NEXT_BAR ("On the next bar");


    /** A press this late after a boundary still counts as on it, in quarter notes. */
    public static final double TOLERANCE_BEATS = 0.05;

    private final String       label;


    MuteTiming (final String label)
    {
        this.label = label;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @param beatsPerBar Bar length in quarter notes
     * @return The quantization unit in quarter notes, 0 for immediate
     */
    public double getUnitBeats (final double beatsPerBar)
    {
        return switch (this)
        {
            case IMMEDIATE -> 0;
            case NEXT_BEAT -> 1;
            case NEXT_BAR -> beatsPerBar > 0 ? beatsPerBar : 4;
        };
    }


    /**
     * The next boundary at or after a position.
     *
     * @param beats The play position in quarter notes
     * @param unitBeats The quantization unit, 0 for none
     * @return The position at which to apply the change
     */
    public static double nextBoundary (final double beats, final double unitBeats)
    {
        if (unitBeats <= 0)
            return beats;
        return Math.ceil ((beats - TOLERANCE_BEATS) / unitBeats) * unitBeats;
    }
}
