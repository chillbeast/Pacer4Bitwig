// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

/**
 * What one switch LED should show: a colour and a pattern.
 *
 * @param colour The colour
 * @param pattern The on/off pattern
 */
public record LedState (LedColour colour, LedPattern pattern)
{
    /** Dark. */
    public static final LedState DARK = new LedState (LedColour.OFF, LedPattern.SOLID);


    /**
     * A solid colour.
     *
     * @param colour The colour
     * @return The state
     */
    public static LedState solid (final LedColour colour)
    {
        return new LedState (colour, LedPattern.SOLID);
    }


    /**
     * A solid colour if the condition holds, otherwise dark.
     *
     * @param condition The condition
     * @param colour The colour
     * @return The state
     */
    public static LedState when (final boolean condition, final LedColour colour)
    {
        return condition ? solid (colour) : DARK;
    }


    /**
     * Get the light code at the given time: the colour ordinal while the pattern is on, otherwise 0.
     *
     * @param clock The time
     * @return The code
     */
    public int code (final LedClock clock)
    {
        return this.colour == LedColour.OFF || !this.pattern.isOn (clock) ? 0 : this.colour.ordinal ();
    }
}
