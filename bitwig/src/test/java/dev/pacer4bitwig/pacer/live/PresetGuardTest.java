// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.controller.PacerMap;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;


/**
 * The preset check: reading the loaded preset's name back, and what the live board does with the answer.
 */
class PresetGuardTest
{
    private final PresetGuard guard = new PresetGuard (PacerMap.PRESET_NAME);
    private long              now   = 100_000;


    /** Ask, and have the Pacer answer a moment later. */
    private PresetGuard.Verdict ask (final String name)
    {
        assertTrue (this.guard.shouldProbe (this.now, true, false), "the guard wants to ask");
        this.guard.probeSent (this.now);
        this.now += 20;
        return this.guard.answered (name, this.now);
    }


    /** The extension wrote a name, and time passed. */
    private void wrote (final String name, final long thenWait)
    {
        this.guard.nameWritten (name, this.now);
        this.now += thenWait;
    }


    @Test
    void beforeTheFirstAnswerEverythingIsWrittenAsBefore ()
    {
        assertEquals (PresetGuard.State.UNKNOWN, this.guard.getState ());
        assertTrue (this.guard.mayWrite (this.now));
        assertTrue (this.guard.shouldProbe (this.now, false, false), "it asks at once, to learn where it is");
    }


    @Test
    void itsOwnNameMeansOurs ()
    {
        this.wrote ("ROW 1", 2000);
        assertEquals (PresetGuard.Verdict.NONE, this.ask ("ROW 1"));
        assertEquals (PresetGuard.State.OURS, this.guard.getState ());
        assertTrue (this.guard.mayWrite (this.now));

        // The answer gets old: a write waits and makes it ask again
        this.now += PresetGuard.CONFIRM_MILLIS + 1;
        assertFalse (this.guard.mayWrite (this.now));
        assertFalse (this.guard.shouldProbe (this.now, false, false), "only before painting: nothing waits, nothing asked");
        assertTrue (this.guard.shouldProbe (this.now, true, false));
    }


    @Test
    void anotherPresetIsLeftAloneUntilOursIsAnnounced ()
    {
        this.wrote ("ROW 1", 2000);
        this.ask ("ROW 1");
        this.now += 2000;
        assertEquals (PresetGuard.Verdict.FOREIGN, this.ask ("G-MST"));
        assertEquals (PresetGuard.State.FOREIGN, this.guard.getState ());
        assertFalse (this.guard.mayWrite (this.now));
        assertTrue (this.guard.isElsewhere (), "not even the blackout goes out");
        assertFalse (this.guard.shouldProbe (this.now + 60_000, true, false), "without the heartbeat, CC 119 brings it back");

        this.guard.presetAnnounced (this.now);
        assertEquals (PresetGuard.State.OURS, this.guard.getState ());
        assertTrue (this.guard.mayWrite (this.now));
    }


    @Test
    void theStoredNameMeansOursFreshlyLoaded ()
    {
        this.wrote ("ROW 1", 5000);
        this.ask ("ROW 1");
        this.now += 5000;
        assertEquals (PresetGuard.Verdict.REPAINT, this.ask ("PACER"), "every live edit is gone: paint it all");
        assertEquals (PresetGuard.State.OURS, this.guard.getState ());
    }


    @Test
    void anAnswerFromBeforeAWriteIsNotTakenAtItsWord ()
    {
        // Startup: the RAM still has the stored name when the first question goes out right after the first write
        this.wrote ("ROW 1", 10);
        assertEquals (PresetGuard.Verdict.NONE, this.ask ("PACER"), "no second repaint at startup");
        assertEquals (PresetGuard.State.OURS, this.guard.getState ());

        // A name from the previous session, answered right after this session's first write: wait and see
        final PresetGuard fresh = new PresetGuard (PacerMap.PRESET_NAME);
        fresh.nameWritten ("LOOP", this.now);
        fresh.probeSent (this.now + 10);
        assertEquals (PresetGuard.Verdict.NONE, fresh.answered ("VERSE", this.now + 30));
        assertEquals (PresetGuard.State.UNKNOWN, fresh.getState ());
    }


    @Test
    void aNameWrittenJustBeforeTheQuestionStillCounts ()
    {
        this.wrote ("ROW 1", 100);
        this.wrote ("ROW 2", 100);
        assertEquals (PresetGuard.Verdict.NONE, this.ask ("ROW 1"), "the answer predates the last write");
        assertEquals (PresetGuard.State.OURS, this.guard.getState ());
    }


    @Test
    void aPacerThatNeverAnswersIsTreatedAsIfThereWereNoCheck ()
    {
        this.guard.probeSent (this.now);
        assertFalse (this.guard.checkTimeout (this.now + PresetGuard.TIMEOUT_MILLIS), "not yet");
        assertTrue (this.guard.checkTimeout (this.now + PresetGuard.TIMEOUT_MILLIS + 1));
        assertEquals (PresetGuard.State.UNANSWERED, this.guard.getState ());
        assertTrue (this.guard.mayWrite (this.now), "a firmware that ignores the question costs nothing");
    }


    @Test
    void aPacerThatStopsAnsweringIsGoneAndRepaintedWhenItComesBack ()
    {
        this.wrote ("ROW 1", 2000);
        this.ask ("ROW 1");

        for (int miss = 0; miss < PresetGuard.MISSES_TO_ABSENT; miss++)
        {
            this.now += PresetGuard.CONFIRM_MILLIS + 1;
            assertTrue (this.guard.shouldProbe (this.now, true, false));
            this.guard.probeSent (this.now);
            this.now += PresetGuard.TIMEOUT_MILLIS + 1;
            this.guard.checkTimeout (this.now);
        }
        assertEquals (PresetGuard.State.ABSENT, this.guard.getState ());
        assertFalse (this.guard.mayWrite (this.now));

        // Plugged back in: it asks every few seconds, and the answer brings everything back
        assertFalse (this.guard.shouldProbe (this.now + 100, true, false));
        this.now += PresetGuard.RETRY_MILLIS + 1;
        assertEquals (PresetGuard.Verdict.REPAINT, this.ask ("PACER"));
        assertTrue (this.guard.mayWrite (this.now));
    }


    @Test
    void oneSlowAnswerIsNotAnUnpluggedPacer ()
    {
        this.wrote ("ROW 1", 2000);
        this.ask ("ROW 1");
        this.now += 2000;
        this.guard.probeSent (this.now);
        this.guard.checkTimeout (this.now + PresetGuard.TIMEOUT_MILLIS + 1);
        assertEquals (PresetGuard.State.OURS, this.guard.getState ());
    }


    @Test
    void theHeartbeatAsksWithoutWrites ()
    {
        this.wrote ("ROW 1", 2000);
        this.ask ("ROW 1");
        assertFalse (this.guard.shouldProbe (this.now + 1000, false, true));
        assertTrue (this.guard.shouldProbe (this.now + PresetGuard.HEARTBEAT_MILLIS + 1, false, true));
    }


    @Test
    void answersNobodyAskedForAreIgnored ()
    {
        // Pacer Studio dumping the preset on the same port
        assertEquals (PresetGuard.Verdict.NONE, this.guard.answered ("G-MST", this.now));
        assertEquals (PresetGuard.State.UNKNOWN, this.guard.getState ());
    }


    @Test
    void questionsNeverCrowdEachOther ()
    {
        this.guard.probeSent (this.now);
        assertFalse (this.guard.shouldProbe (this.now + 10, true, true), "one question at a time");
        this.guard.answered ("PACER", this.now + 20);
        assertFalse (this.guard.shouldProbe (this.now + 30, true, true), "and a pause between them");
    }


    @Test
    void theLiveBoardHoldsWritesBackWhileTheGateIsShut ()
    {
        final List<String> sent = new ArrayList<> ();
        final boolean [] open =
        {
            false
        };
        final List<String> names = new ArrayList<> ();
        final int [] blocked = new int [1];
        final LiveBoard board = new LiveBoard (sent::add);
        board.setGate (new WriteGate ()
        {
            @Override
            public boolean mayWrite ()
            {
                return open[0];
            }


            @Override
            public void blocked ()
            {
                blocked[0]++;
            }


            @Override
            public void nameWritten (final String name)
            {
                names.add (name);
            }
        });

        assertFalse (board.setLed (0, PacerColour.RED, PacerColour.OFF, LedRow.STRIP));
        assertFalse (board.setName ("LOOP"));
        assertTrue (sent.isEmpty ());
        assertEquals (2, blocked[0]);

        open[0] = true;
        assertTrue (board.setLed (0, PacerColour.RED, PacerColour.OFF, LedRow.STRIP), "held back, so not remembered as done");
        assertTrue (board.setName ("LOOP"));
        assertEquals (List.of ("LOOP "), names);

        // Reads and the last word go out whatever the gate says
        open[0] = false;
        board.request (PacerSysex.requestName ());
        board.blackout ("OFF");
        assertEquals (PacerSysex.name ("OFF"), sent.get (sent.size () - 1));
    }


    @Test
    void theNameQuestionAndItsAnswer ()
    {
        assertEquals ("F0 00 01 77 7F 02 01 00 01 7C F7", PacerSysex.requestName ());
        assertEquals ("ROW 1", PacerSysex.parseName (PacerSysex.name ("ROW 1")));
        // Bitwig hands SysEx over as lower-case hex without spaces
        assertEquals ("PACER", PacerSysex.parseName (PacerSysex.name ("PACER").replace (" ", "").toLowerCase ()));
        assertNull (PacerSysex.parseName (PacerSysex.requestName ()), "the question is not an answer");
        assertNull (PacerSysex.parseName (PacerSysex.led (0, PacerColour.RED, PacerColour.OFF, LedRow.STRIP)));
        assertNull (PacerSysex.parseName ("F0 00 01 77 7F 01 01 05 01 01 05 41 42 43 44 45 00 F7"), "a stored slot, not the loaded preset");
        assertNull (PacerSysex.parseName ("zz"));
        assertNull (PacerSysex.parseName (null));
    }
}
