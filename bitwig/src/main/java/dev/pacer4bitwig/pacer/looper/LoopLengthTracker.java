// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import java.util.Arrays;


/**
 * Measures how many bars a loop recorded, by watching its state on every tick: recording starts and ends on
 * quantization boundaries, so the transport positions of both transitions give the length.
 */
public final class LoopLengthTracker
{
    private final double [] recordingSince;


    /**
     * Constructor.
     *
     * @param size Number of loop tracks
     */
    public LoopLengthTracker (final int size)
    {
        this.recordingSince = new double [size];
        this.reset ();
    }


    /**
     * Feed the current state of one loop.
     *
     * @param index The loop track
     * @param state Its state
     * @param clockRunning True while the transport plays
     * @param beats The play position in quarter notes
     * @param beatsPerBar Bar length in quarter notes
     * @return The length in bars of a loop that has just been closed, 0 otherwise
     */
    public int update (final int index, final LoopState state, final boolean clockRunning, final double beats, final double beatsPerBar)
    {
        if (state == LoopState.RECORDING)
        {
            if (Double.isNaN (this.recordingSince[index]) && clockRunning)
                this.recordingSince[index] = beats;
            return 0;
        }

        final double start = this.recordingSince[index];
        if (Double.isNaN (start))
            return 0;
        this.recordingSince[index] = Double.NaN;

        // Deleted, aborted, transport stopped, or the arranger loop jumped back: nothing to measure
        if (!clockRunning || state == LoopState.EMPTY || state == LoopState.RECORD_QUEUED || beats <= start)
            return 0;

        final double bar = beatsPerBar > 0 ? beatsPerBar : 4;
        return (int) Math.max (1, Math.round ((beats - start) / bar));
    }


    /**
     * Forget all recordings in progress, e.g. after the track window moved.
     */
    public void reset ()
    {
        Arrays.fill (this.recordingSince, Double.NaN);
    }
}
