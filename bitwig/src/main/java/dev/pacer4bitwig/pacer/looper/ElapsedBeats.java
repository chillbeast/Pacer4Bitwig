// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

/**
 * Beats played, counting on when the play position jumps back - the arranger loop wrapping round, or a relocate. A
 * count-in, a waiting mute or a fade that aimed at an absolute beat past the loop end would otherwise never get there:
 * the metronome stayed on, the mute blinked for ever, the fade restarted at every wrap.
 * <p>
 * Targets are still worked out on the real position (where the next bar is), then moved onto this scale with
 * {@link #toElapsed(double, double)}. A jump back counts as no time at all, which loses at most one tick.
 */
public final class ElapsedBeats
{
    private double last = Double.NaN;
    private double elapsed;


    /**
     * Follow the play position.
     *
     * @param position The play position in beats
     * @return The beats played so far
     */
    public double update (final double position)
    {
        if (!Double.isNaN (this.last) && position > this.last)
            this.elapsed += position - this.last;
        this.last = position;
        return this.elapsed;
    }


    /**
     * Put a target given as a play position onto this scale.
     *
     * @param target The play position to reach, e.g. the next bar
     * @param position The play position now
     * @return The target in beats played
     */
    public double toElapsed (final double target, final double position)
    {
        return this.update (position) + target - position;
    }
}
