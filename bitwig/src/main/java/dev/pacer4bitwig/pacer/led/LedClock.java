// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

/**
 * The time an LED pattern is evaluated at: wall clock always, the transport position when beat-synced.
 *
 * @param millis Wall-clock milliseconds
 * @param synced True if patterns follow the transport
 * @param beats Play position in quarter notes (only meaningful when synced)
 * @param beatsPerBar Bar length in quarter notes
 */
public record LedClock (long millis, boolean synced, double beats, double beatsPerBar)
{
    /**
     * A wall-clock-only time.
     *
     * @param millis Wall-clock milliseconds
     * @return The clock
     */
    public static LedClock unsynced (final long millis)
    {
        return new LedClock (millis, false, 0, 4);
    }


    /**
     * @return Position within the current beat, 0..1
     */
    public double beatPhase ()
    {
        return this.beats - Math.floor (this.beats);
    }


    /**
     * @return Position within the current bar in quarter notes, 0..beatsPerBar
     */
    public double barPosition ()
    {
        final double bar = this.beatsPerBar > 0 ? this.beatsPerBar : 4;
        return this.beats - Math.floor (this.beats / bar) * bar;
    }


    /**
     * @return The beat within the bar, 0-based
     */
    public int beatInBar ()
    {
        return (int) Math.floor (this.barPosition ());
    }
}
