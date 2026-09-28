// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Failures are caught and reported without flooding; diagnostics cost nothing while off.
 */
class FailSoftTest
{
    private final List<String> reports = new ArrayList<> ();
    private final AtomicLong   now     = new AtomicLong (1000);
    private final FailSoft     failSoft = new FailSoft ( (where, error, first) -> this.reports.add (where + (first ? " first" : " again") + ": " + error.getMessage ()), this.now::get);


    @Test
    void aFailureIsReportedAndTheCallerCarriesOn ()
    {
        final List<String> ran = new ArrayList<> ();
        this.failSoft.run ("tick", () -> {
            throw new IllegalStateException ("boom");
        });
        this.failSoft.run ("tick", () -> ran.add ("next tick"));
        assertEquals (List.of ("tick first: boom"), this.reports);
        assertEquals (List.of ("next tick"), ran);
        assertEquals (1, this.failSoft.getFailures ());
    }


    @Test
    void repeatsAreReportedAtMostEveryFewSeconds ()
    {
        for (int i = 0; i < 25; i++)
        {
            this.failSoft.run ("LEDs", () -> {
                throw new IllegalStateException ("again");
            });
            this.now.addAndGet (40);
        }
        assertEquals (1, this.reports.size (), "25 failures a second, one report");
        this.now.addAndGet (FailSoft.QUIET_MILLIS);
        this.failSoft.run ("LEDs", () -> {
            throw new IllegalStateException ("still");
        });
        assertEquals ("LEDs again: still", this.reports.get (1));
        assertEquals (26, this.failSoft.getFailures ());
    }


    @Test
    void aFailingQuestionGetsTheFallback ()
    {
        assertEquals (0, this.failSoft.getAsInt ("LEDs", () -> {
            throw new IllegalArgumentException ("no");
        }, 0));
        assertEquals (7, this.failSoft.getAsInt ("LEDs", () -> 7, 0));
    }


    @Test
    void diagnosticsBuildNothingWhileOff ()
    {
        final List<String> lines = new ArrayList<> ();
        final Diagnostics.Level [] level =
        {
            Diagnostics.Level.OFF
        };
        final Diagnostics diagnostics = new Diagnostics (lines::add, () -> level[0]);
        final int [] built = new int [1];
        diagnostics.log (Diagnostics.Level.ACTIONS, () -> "press " + ++built[0]);
        assertEquals (0, built[0]);

        level[0] = Diagnostics.Level.ACTIONS;
        diagnostics.log (Diagnostics.Level.ACTIONS, () -> "press");
        diagnostics.log (Diagnostics.Level.ALL, () -> "SysEx");
        assertEquals (List.of ("PACER press"), lines);

        level[0] = Diagnostics.Level.ALL;
        diagnostics.log (Diagnostics.Level.ALL, () -> "SysEx");
        assertTrue (lines.contains ("PACER SysEx"));
    }
}
