// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

/**
 * Pick-up takeover for one pedal: after it gets a target, the pedal leaves the target alone until it reaches the
 * target's value - or passes it between two moves - and follows from then on. Pure; the controller asks it on every
 * move and resets it whenever the pedal is pointed somewhere else.
 */
public final class PedalPickup
{
    /** Close enough to count as "reached", as a fraction of the range. */
    private static final double TOLERANCE  = 0.02;

    private boolean             engaged;
    private double              lastOutput = Double.NaN;


    /**
     * Start over: the next move has to pick the target up again.
     */
    public void reset ()
    {
        this.engaged = false;
        this.lastOutput = Double.NaN;
    }


    /**
     * @return True once the pedal follows its target
     */
    public boolean isEngaged ()
    {
        return this.engaged;
    }


    /**
     * The pedal moved.
     *
     * @param output What the pedal asks the target to be, 0-1 (after its response curve)
     * @param current What the target is now, 0-1; NaN if that is not known, which picks it up at once
     * @return True if the target should follow
     */
    public boolean accept (final double output, final double current)
    {
        final double previous = this.lastOutput;
        this.lastOutput = output;
        if (this.engaged)
            return true;
        final boolean reached = Math.abs (output - current) <= TOLERANCE;
        // A fast pedal skips values: passing the target between two moves counts as reaching it
        final boolean passed = !Double.isNaN (previous) && (previous - current) * (output - current) < 0;
        this.engaged = Double.isNaN (current) || reached || passed;
        return this.engaged;
    }
}
