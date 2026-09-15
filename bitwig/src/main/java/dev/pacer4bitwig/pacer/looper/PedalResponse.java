// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

/**
 * How an expression pedal position becomes an output value: a curve, then scaled into a range. A minimum above the
 * maximum reverses the direction.
 *
 * @param curve The response curve
 * @param minPercent The output at the heel, 0-100
 * @param maxPercent The output at the toe, 0-100
 */
public record PedalResponse (PedalCurve curve, int minPercent, int maxPercent)
{
    /** Linear, full range. */
    public static final PedalResponse DEFAULT = new PedalResponse (PedalCurve.LINEAR, 0, 100);


    /**
     * @return True if the output equals the input, so the pedal can be bound to a parameter directly
     */
    public boolean isIdentity ()
    {
        return this.curve == PedalCurve.LINEAR && this.minPercent == 0 && this.maxPercent == 100;
    }


    /**
     * @param position The pedal position, 0..1
     * @return The output, 0..1
     */
    public double map (final double position)
    {
        final double min = clamp (this.minPercent) / 100.0;
        final double max = clamp (this.maxPercent) / 100.0;
        return min + (max - min) * this.curve.apply (position);
    }


    /**
     * @param value The pedal value, 0-127
     * @return The output, 0-127
     */
    public int map (final int value)
    {
        return (int) Math.round (this.map (value / 127.0) * 127);
    }


    private static int clamp (final int percent)
    {
        return Math.max (0, Math.min (100, percent));
    }
}
