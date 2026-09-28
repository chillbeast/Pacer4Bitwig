// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import java.util.Arrays;


/**
 * One fader over all the loop tracks that keeps their balance: at the toe every track sits at its own level, at the
 * heel all are silent, and in between each is its level times the pedal.
 * <p>
 * Each track's level is taken from the track the first time the pedal moves it, and again whenever the pedal moves
 * off the toe - so a balance set in Bitwig with the pedal at the toe is the one the pedal keeps. Pure: the looper
 * feeds it the tracks' volumes and writes back what it answers.
 */
public final class LoopsLevel
{
    /** At or above this the pedal counts as resting at the toe. */
    private static final double FULL = 0.99;

    private final double []     levels;
    private double              gain = 1;


    /**
     * Constructor.
     *
     * @param size How many loop tracks there can be
     */
    public LoopsLevel (final int size)
    {
        this.levels = new double [size];
        this.reset ();
    }


    /**
     * Forget the tracks' levels, e.g. because the loop tracks moved and are other tracks now.
     */
    public void reset ()
    {
        Arrays.fill (this.levels, Double.NaN);
        this.gain = 1;
    }


    /**
     * @return Where the fader is, 0-1; 1 until the pedal first moves it
     */
    public double getGain ()
    {
        return this.gain;
    }


    /**
     * Move the fader.
     *
     * @param volumes The loop tracks' volumes now, normalized; NaN for a track that does not exist
     * @param newGain Where the pedal puts the fader, 0-1
     * @return The volumes to write, NaN where a track is to be left alone
     */
    public double [] apply (final double [] volumes, final double newGain)
    {
        final boolean fromTheToe = this.gain >= FULL;
        final double [] result = new double [volumes.length];
        for (int i = 0; i < volumes.length; i++)
        {
            if (Double.isNaN (volumes[i]) || i >= this.levels.length)
            {
                result[i] = Double.NaN;
                continue;
            }
            if (Double.isNaN (this.levels[i]) || fromTheToe)
                this.levels[i] = volumes[i];
            result[i] = Math.max (0, Math.min (1, this.levels[i] * newGain));
        }
        this.gain = Math.max (0, Math.min (1, newGain));
        return result;
    }
}
