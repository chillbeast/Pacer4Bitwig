// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.pacer4bitwig.pacer.looper.LayerPlanner.Kind;
import dev.pacer4bitwig.pacer.looper.LayerPlanner.Step;

import org.junit.jupiter.api.Test;


class WaveTwoTest
{
    private static final LoopState E = LoopState.EMPTY;
    private static final LoopState P = LoopState.PLAYING;
    private static final LoopState R = LoopState.RECORDING;
    private static final LoopState Q = LoopState.RECORD_QUEUED;
    private static final LoopState S = LoopState.STOPPED;


    @Test
    void oneButtonLooperWalksThroughTheRow ()
    {
        assertEquals (new Step (Kind.RECORD, 0), LayerPlanner.next (new LoopState [] {E, E, E, E}));
        assertEquals (new Step (Kind.CANCEL, 0), LayerPlanner.next (new LoopState [] {Q, E, E, E}));
        assertEquals (new Step (Kind.CLOSE, 0), LayerPlanner.next (new LoopState [] {R, E, E, E}));
        assertEquals (new Step (Kind.RECORD, 1), LayerPlanner.next (new LoopState [] {P, E, E, E}));
        // A stopped loop is still a layer; the gap after it gets filled first
        assertEquals (new Step (Kind.RECORD, 2), LayerPlanner.next (new LoopState [] {S, P, E, P}));
        // Missing tracks are skipped
        assertEquals (new Step (Kind.RECORD, 2), LayerPlanner.next (new LoopState [] {P, null, E, null}));
        assertEquals (new Step (Kind.FULL, -1), LayerPlanner.next (new LoopState [] {P, P, S, null}));

        assertEquals (1, LayerPlanner.focus (new LoopState [] {P, R, E, E}));
        assertEquals (2, LayerPlanner.focus (new LoopState [] {P, R, Q, E}));
        assertEquals (-1, LayerPlanner.focus (new LoopState [] {P, P, E, E}));
    }


    @Test
    void countInLandsOnTheTargetBar ()
    {
        assertEquals (3.5, CountIn.ONE_BAR.recordAtBeats (0, 4));
        assertEquals (7.5, CountIn.TWO_BARS.recordAtBeats (0, 4));
        // Started mid-bar: counts from the start of that bar
        assertEquals (19.5, CountIn.ONE_BAR.recordAtBeats (17.2, 4));
        // 3/4
        assertEquals (5.5, CountIn.TWO_BARS.recordAtBeats (0, 3));
    }


    @Test
    void loopLengthIsMeasuredInBars ()
    {
        final LoopLengthTracker tracker = new LoopLengthTracker (4);

        assertEquals (0, tracker.update (0, Q, true, 3.6, 4));
        assertEquals (0, tracker.update (0, R, true, 4.03, 4));
        assertEquals (0, tracker.update (0, R, true, 10.0, 4));
        // Closed 2 bars later, observed a little late
        assertEquals (2, tracker.update (0, P, true, 12.08, 4));
        // Only reported once
        assertEquals (0, tracker.update (0, P, true, 12.2, 4));

        // Deleted while recording
        tracker.update (1, R, true, 20, 4);
        assertEquals (0, tracker.update (1, E, true, 23, 4));

        // Arranger loop jumped back
        tracker.update (2, R, true, 30, 4);
        assertEquals (0, tracker.update (2, P, true, 2, 4));

        // Very short recordings still count as one bar
        tracker.update (3, R, true, 40, 4);
        assertEquals (1, tracker.update (3, S, true, 41, 4));
    }
}
