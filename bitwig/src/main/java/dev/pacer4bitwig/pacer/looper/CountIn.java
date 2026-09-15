// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * Count-in when a recording is started from a stopped transport: the transport starts right away, and the recording is
 * queued shortly before the target bar so the launch quantization lands it exactly on that bar.
 */
public enum CountIn implements Labelled
{
    /** Record right away. */
    OFF ("Off", 0),
    /** One bar. */
    ONE_BAR ("1 bar", 1),
    /** Two bars. */
    TWO_BARS ("2 bars", 2);


    /** Queue the recording this many quarter notes before the target bar. */
    public static final double LEAD_BEATS = 0.5;

    private final String       label;
    private final int          bars;


    CountIn (final String label, final int bars)
    {
        this.label = label;
        this.bars = bars;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return The count-in length in bars
     */
    public int getBars ()
    {
        return this.bars;
    }


    /**
     * When to queue the recording.
     *
     * @param startBeats Where the transport started, in quarter notes
     * @param beatsPerBar Bar length in quarter notes
     * @return The position at which to call record
     */
    public double recordAtBeats (final double startBeats, final double beatsPerBar)
    {
        final double bar = beatsPerBar > 0 ? beatsPerBar : 4;
        final double startBar = Math.floor (startBeats / bar) * bar;
        return startBar + this.bars * bar - LEAD_BEATS;
    }
}
