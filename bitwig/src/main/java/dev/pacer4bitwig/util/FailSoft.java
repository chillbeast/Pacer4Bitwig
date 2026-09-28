// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;


/**
 * Runs the extension's entry points so that one bug cannot take the rest down: an exception in the periodic tick
 * used to end the tick for good (no more LEDs, count-ins or fades until a restart), one in a switch handler reached
 * Bitwig. A failure is reported - the first time from each place at once, then at most every few seconds - and the
 * extension carries on.
 */
public final class FailSoft
{
    /** Receives the failures. */
    @FunctionalInterface
    public interface Reporter
    {
        /**
         * @param where Which entry point failed
         * @param error What was thrown
         * @param first True the first time this entry point failed
         */
        void report (String where, RuntimeException error, boolean first);
    }


    /** Repeats from the same place are reported at most this often. */
    public static final long        QUIET_MILLIS = 5000;

    private final Reporter          reporter;
    private final LongSupplier      clock;
    private final Map<String, Long> reportedAt   = new HashMap<> ();
    private int                     failures;


    /**
     * Constructor.
     *
     * @param reporter Receives the failures
     * @param clock Milliseconds
     */
    public FailSoft (final Reporter reporter, final LongSupplier clock)
    {
        this.reporter = reporter;
        this.clock = clock;
    }


    /**
     * Run a task, reporting what it throws.
     *
     * @param where Which entry point this is
     * @param task The task
     */
    public void run (final String where, final Runnable task)
    {
        try
        {
            task.run ();
        }
        catch (final RuntimeException ex)
        {
            this.failed (where, ex);
        }
    }


    /**
     * Ask for a number, falling back to a default if that throws.
     *
     * @param where Which entry point this is
     * @param supplier The question
     * @param fallback The answer if it fails
     * @return The answer
     */
    public int getAsInt (final String where, final IntSupplier supplier, final int fallback)
    {
        try
        {
            return supplier.getAsInt ();
        }
        catch (final RuntimeException ex)
        {
            this.failed (where, ex);
            return fallback;
        }
    }


    /**
     * @param where Which entry point this is
     * @param task The task
     * @return The task, wrapped
     */
    public Runnable wrap (final String where, final Runnable task)
    {
        return () -> this.run (where, task);
    }


    /**
     * @return How many failures were caught
     */
    public int getFailures ()
    {
        return this.failures;
    }


    private void failed (final String where, final RuntimeException ex)
    {
        this.failures++;
        final long now = this.clock.getAsLong ();
        final Long last = this.reportedAt.get (where);
        if (last != null && now - last.longValue () < QUIET_MILLIS)
            return;
        this.reportedAt.put (where, Long.valueOf (now));
        this.reporter.report (where, ex, last == null);
    }
}
