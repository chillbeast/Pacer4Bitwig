// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * What "previous / next row" does while loops play: only move there (and play the row with play/stop all), or take
 * the song along - play the row moved to when it has loops, so a section change is one press.
 */
public enum RowMove implements Labelled
{
    /** Only move to the row. */
    SELECT ("Only move to it (play it with Play row / stop all)"),
    /** Move to the row and, if loops are playing and the row has loops, play it. */
    PLAY ("Move to it and play it, when loops play and it has loops");


    private final String label;


    RowMove (final String label)
    {
        this.label = label;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }
}
