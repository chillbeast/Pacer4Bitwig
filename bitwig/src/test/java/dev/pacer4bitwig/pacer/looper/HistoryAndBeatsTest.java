// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Set;


class HistoryAndBeatsTest
{
    @Test
    void clearingTheLastLoopOfOneRowKeepsTheOtherRowsHistory ()
    {
        final RecordHistory history = new RecordHistory (64);
        history.recorded (0, 0);
        history.recorded (0, 1);
        history.recorded (1, 0);

        // Row 2: clear its one loop, then ask again - nothing left there
        assertEquals (0, history.takeLatest (1, index -> true));
        assertEquals (-1, history.takeLatest (1, index -> true));

        // Back in row 1 both of its loops are still known, newest first
        assertEquals (1, history.takeLatest (0, index -> true));
        assertEquals (0, history.takeLatest (0, index -> true));
        assertEquals (-1, history.takeLatest (0, index -> true));
    }


    @Test
    void loopsClearedByHandAreSkipped ()
    {
        final RecordHistory history = new RecordHistory (64);
        history.recorded (0, 0);
        history.recorded (0, 1);
        history.recorded (0, 2);
        // Loop 3 was deleted with a hold in the meantime
        final Set<Integer> stillThere = Set.of (Integer.valueOf (0), Integer.valueOf (1));
        assertEquals (1, history.takeLatest (0, index -> stillThere.contains (Integer.valueOf (index))));
    }


    @Test
    void theHistoryForgetsTheOldestFirst ()
    {
        final RecordHistory history = new RecordHistory (2);
        history.recorded (0, 0);
        history.recorded (0, 1);
        history.recorded (0, 2);
        assertEquals (2, history.takeLatest (0, index -> true));
        assertEquals (1, history.takeLatest (0, index -> true));
        assertEquals (-1, history.takeLatest (0, index -> true), "loop 1 fell off the end");
    }


    @Test
    void beatsPlayedCountOnWhenTheArrangerLoopWraps ()
    {
        final ElapsedBeats beats = new ElapsedBeats ();
        // Arranger loop 0-16: at beat 13 a 2-bar count-in aims for bar 5 (beat 20), which the position never reaches
        final double target = beats.toElapsed (20, 13);
        for (double position = 13; position < 16; position += 0.5)
            assertTrue (beats.update (position) < target);
        // Wrap: the position is back at 0, but beats played keep going
        double position = 0;
        while (beats.update (position) < target)
            position += 0.5;
        assertEquals (4, position, 0.5, "the target arrives seven beats later, as it would without the loop");
    }


    @Test
    void aJumpBackCostsNoTime ()
    {
        final ElapsedBeats beats = new ElapsedBeats ();
        beats.update (10);
        assertEquals (2, beats.update (12), 1e-9);
        assertEquals (2, beats.update (0), 1e-9, "the jump itself adds nothing");
        assertEquals (3, beats.update (1), 1e-9);
        assertEquals (3, beats.update (1), 1e-9, "asking twice for the same position is harmless");
    }


    @Test
    void eventWordsFitTheDisplay ()
    {
        assertEquals ("REC 3", LooperText.loopWord ("REC ", 2));
        assertEquals ("MUTE8", LooperText.loopWord ("MUTE", 7));
        assertEquals ("4 BAR", LooperText.barsWord (4));
        assertEquals ("12BAR", LooperText.barsWord (12));
        assertEquals ("128B", LooperText.barsWord (128));
    }


    @Test
    void theRecordHistoryCanBeReadWithoutForgetting ()
    {
        final RecordHistory history = new RecordHistory (8);
        history.recorded (0, 1);
        history.recorded (0, 3);
        assertEquals (3, history.peekLatest (0, index -> true));
        assertEquals (3, history.peekLatest (0, index -> true), "still there");
        assertEquals (1, history.peekLatest (0, index -> index != 3), "a loop that is gone is skipped");
        assertEquals (-1, history.peekLatest (1, index -> true));
    }


    @Test
    void theLengthTrackerTellsWhenARecordingStarted ()
    {
        final LoopLengthTracker tracker = new LoopLengthTracker (8);
        assertTrue (Double.isNaN (tracker.getRecordingSince (0)));
        tracker.update (0, LoopState.RECORDING, true, 16, 4);
        assertEquals (16, tracker.getRecordingSince (0), 1e-9);
        assertEquals (2, tracker.update (0, LoopState.PLAYING, true, 24, 4), "two bars");
        assertTrue (Double.isNaN (tracker.getRecordingSince (0)));
    }
}
