// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


/**
 * The tap that waits for the double-tap window (SW 6): a double-tap never runs the tap, a single tap runs once the
 * window has passed, and a hold after a tap does not lose it.
 */
class DelayedTapTest
{
    private final List<String>   events = new ArrayList<> ();
    private final List<Runnable> later  = new ArrayList<> ();
    private final AtomicLong     now    = new AtomicLong (10_000);


    private TapHoldCommand command ()
    {
        // Tap on release, like SW 6
        return new TapHoldCommand ( () -> false, () -> this.events.add ("tap"), () -> this.events.add ("hold"), null, null, () -> 0, (task, delay) -> this.later.add (task)).withDoubleTap ( () -> this.events.add ("double"), () -> true, () -> 350, this.now::get).withDelayedTap ( () -> true);
    }


    private void tap (final TapHoldCommand command, final long atMillis)
    {
        this.now.set (atMillis);
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);
    }


    private void windowPasses ()
    {
        final List<Runnable> due = new ArrayList<> (this.later);
        this.later.clear ();
        due.forEach (Runnable::run);
    }


    @Test
    void aDoubleTapNeverRunsTheTap ()
    {
        final TapHoldCommand command = this.command ();
        this.tap (command, 10_000);
        assertTrue (this.events.isEmpty (), "the tap waits");
        this.tap (command, 10_200);
        this.windowPasses ();
        assertEquals (List.of ("double"), this.events);
    }


    @Test
    void aSingleTapRunsOnceTheWindowHasPassed ()
    {
        final TapHoldCommand command = this.command ();
        this.tap (command, 10_000);
        this.windowPasses ();
        assertEquals (List.of ("tap"), this.events);

        // A later tap is a new first tap, not the second half of a double-tap
        this.tap (command, 11_000);
        this.windowPasses ();
        assertEquals (List.of ("tap", "tap"), this.events);
    }


    @Test
    void aHoldRightAfterATapRunsTheTapFirst ()
    {
        final TapHoldCommand command = this.command ();
        this.tap (command, 10_000);
        this.now.set (10_100);
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        command.execute (ButtonEvent.UP, 0);
        this.windowPasses ();
        assertEquals (List.of ("tap", "hold"), this.events, "as it would have run without the wait, and only once");
    }
}
