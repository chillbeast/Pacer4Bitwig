// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.IntPredicate;


/**
 * The loops recorded from the Pacer, newest first, so "clear the last recorded loop" can walk back through a row -
 * and walk back through another row later, without the first walk having thrown that row's history away.
 */
public final class RecordHistory
{
    private final Deque<int []> entries = new ArrayDeque<> ();
    private final int           capacity;


    /**
     * Constructor.
     *
     * @param capacity How many recordings to remember; the oldest are forgotten first
     */
    public RecordHistory (final int capacity)
    {
        this.capacity = capacity;
    }


    /**
     * A recording started.
     *
     * @param row The scene row
     * @param track The loop track's bank position
     */
    public void recorded (final int row, final int track)
    {
        this.entries.push (new int []
        {
            row,
            track
        });
        while (this.entries.size () > this.capacity)
            this.entries.removeLast ();
    }


    /**
     * Take the most recent recording of a row that still holds a loop. It is forgotten, and so are the row's newer
     * entries that hold nothing any more (cleared by hand, say); other rows' entries are kept.
     *
     * @param row The scene row
     * @param stillThere Whether the track at this bank position still holds a loop in the row
     * @return The track's bank position, -1 if the row has none left
     */
    public int takeLatest (final int row, final IntPredicate stillThere)
    {
        final Iterator<int []> iterator = this.entries.iterator ();
        while (iterator.hasNext ())
        {
            final int [] entry = iterator.next ();
            if (entry[0] != row)
                continue;
            iterator.remove ();
            if (stillThere.test (entry[1]))
                return entry[1];
        }
        return -1;
    }


    /**
     * The most recent recording of a row that is still there, without forgetting it.
     *
     * @param row The scene row
     * @param stillThere Whether the loop on a track position still exists
     * @return The track bank position, -1 if none
     */
    public int peekLatest (final int row, final IntPredicate stillThere)
    {
        for (final int [] entry: this.entries)
            if (entry[0] == row && stillThere.test (entry[1]))
                return entry[1];
        return -1;
    }


    /**
     * Forget everything.
     */
    public void clear ()
    {
        this.entries.clear ();
    }
}
