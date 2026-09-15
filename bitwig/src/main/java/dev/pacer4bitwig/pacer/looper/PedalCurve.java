// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * The response of an expression pedal: maps the pedal position (heel 0 .. toe 1) to the output value.
 */
public enum PedalCurve implements Labelled
{
    /** Straight. */
    LINEAR ("Linear"),
    /** Toe = minimum, heel = maximum. */
    INVERTED ("Inverted"),
    /** Slow start: fine control near the heel. */
    SLOW_START ("Slow start (fine control at the heel)"),
    /** Fast start: fine control near the toe. */
    FAST_START ("Fast start (fine control at the toe)");


    private final String label;


    PedalCurve (final String label)
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
     * Apply the curve.
     *
     * @param position The pedal position, 0..1
     * @return The output, 0..1
     */
    public double apply (final double position)
    {
        final double x = Math.max (0, Math.min (1, position));
        return switch (this)
        {
            case LINEAR -> x;
            case INVERTED -> 1 - x;
            case SLOW_START -> x * x;
            case FAST_START -> Math.sqrt (x);
        };
    }


    /**
     * Apply the curve to a 7-bit value.
     *
     * @param value The pedal value, 0-127
     * @return The output, 0-127
     */
    public int apply (final int value)
    {
        return (int) Math.round (this.apply (value / 127.0) * 127);
    }
}
