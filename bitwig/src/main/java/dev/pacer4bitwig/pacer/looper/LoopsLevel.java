// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import java.util.Arrays;


/**
 * One fader over all the loop tracks that keeps their balance: at the toe every track sits at its own level, at the
 * heel all are silent, and in between each is its level times the pedal.
 * <p>
 * A track's level is taken from the track the first time the fader moves it. After that the fader only adopts a new
 * level when the track was moved by someone else - its volume is no longer what the fader wrote - and only once the
 * fader has rested, because Bitwig reports a volume a little after it was set and a moving pedal would read its own
 * writes back as someone else's. Pure: the looper feeds it the tracks' volumes and writes back what it answers.
 */
public final class LoopsLevel
{
    /** A volume this close to what the fader wrote is the fader's own. */
    private static final double TOLERANCE    = 0.02;
    /** The fader has rested this long, so the volumes Bitwig reports are settled. */
    public static final long    SETTLED_MILLIS = 500;
    /** Below this a volume says nothing about the level it was scaled from. */
    private static final double MIN_GAIN     = 0.05;

    private final double []     levels;
    private final double []     written;
    private double              gain         = 1;
    /** When the fader last moved; 0 (long ago) before the first move. */
    private long                movedAt;
    private int                 generation;


    /**
     * Constructor.
     *
     * @param size How many loop tracks there can be
     */
    public LoopsLevel (final int size)
    {
        this.levels = new double [size];
        this.written = new double [size];
        this.reset ();
    }


    /**
     * Forget the tracks' levels, e.g. because the loop tracks moved and are other tracks now.
     */
    public void reset ()
    {
        Arrays.fill (this.levels, Double.NaN);
        Arrays.fill (this.written, Double.NaN);
        this.gain = 1;
        this.movedAt = 0;
        this.generation++;
    }


    /**
     * @return Counts the resets, so a picked-up pedal knows its fader started over
     */
    public int getGeneration ()
    {
        return this.generation;
    }


    /**
     * @return Where the fader is, 0-1; 1 until the pedal first moves it
     */
    public double getGain ()
    {
        return this.gain;
    }


    /**
     * The level of a track the fader has moved, to hand it back before the fader lets go of the track.
     *
     * @param index The loop track
     * @return Its level, NaN if the fader never moved it
     */
    public double getLevel (final int index)
    {
        return index < this.levels.length && !Double.isNaN (this.written[index]) ? this.levels[index] : Double.NaN;
    }


    /**
     * Move the fader.
     *
     * @param volumes The loop tracks' volumes now, normalized; NaN for a track that does not exist
     * @param newGain Where the pedal puts the fader, 0-1
     * @param now Milliseconds
     * @return The volumes to write, NaN where a track is to be left alone
     */
    public double [] apply (final double [] volumes, final double newGain, final long now)
    {
        final boolean settled = now - this.movedAt >= SETTLED_MILLIS;
        final double [] result = new double [volumes.length];
        for (int i = 0; i < volumes.length; i++)
        {
            if (Double.isNaN (volumes[i]) || i >= this.levels.length)
            {
                result[i] = Double.NaN;
                continue;
            }
            if (Double.isNaN (this.levels[i]))
                this.levels[i] = volumes[i];
            else if (settled && !Double.isNaN (this.written[i]) && Math.abs (volumes[i] - this.written[i]) > TOLERANCE)
                // Moved by someone else while the fader rested: that is the level at the fader's position
                this.levels[i] = this.gain >= MIN_GAIN ? Math.min (1, volumes[i] / this.gain) : volumes[i];
            result[i] = Math.max (0, Math.min (1, this.levels[i] * newGain));
            this.written[i] = result[i];
        }
        this.gain = Math.max (0, Math.min (1, newGain));
        this.movedAt = now;
        return result;
    }
}
