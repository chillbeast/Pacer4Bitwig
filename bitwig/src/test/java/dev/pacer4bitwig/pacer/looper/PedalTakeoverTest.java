// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * Pick-up takeover, the "all loop tracks" fader and the loop pedal targets.
 */
class PedalTakeoverTest
{
    private static final double EPSILON = 1e-9;


    @Test
    void aPickedUpPedalWaitsUntilItReachesTheTarget ()
    {
        final PedalPickup pickup = new PedalPickup ();
        assertFalse (pickup.accept (0.1, 0.6));
        assertFalse (pickup.accept (0.3, 0.6), "still below");
        assertTrue (pickup.accept (0.59, 0.6), "close enough");
        assertTrue (pickup.accept (0.2, 0.6), "and then it follows wherever it goes");
    }


    @Test
    void passingTheTargetBetweenTwoMovesCountsAsReachingIt ()
    {
        final PedalPickup pickup = new PedalPickup ();
        assertFalse (pickup.accept (0.9, 0.5));
        assertTrue (pickup.accept (0.2, 0.5), "a fast heel-down skips right over 0.5");
    }


    @Test
    void aTargetThatCannotBeReadWaits ()
    {
        // An FX page that is not selected yet: following now would jump it
        final PedalPickup pickup = new PedalPickup ();
        assertFalse (pickup.accept (0.8, Double.NaN));
        assertFalse (pickup.accept (0.2, Double.NaN));
        assertTrue (pickup.accept (0.21, 0.2), "picked up once it can be read and is reached");
    }


    @Test
    void aResetStartsThePickUpOver ()
    {
        final PedalPickup pickup = new PedalPickup ();
        assertTrue (pickup.accept (0.5, 0.5));
        pickup.reset ();
        assertFalse (pickup.isEngaged ());
        assertFalse (pickup.accept (0.1, 0.5));
    }


    @Test
    void theLoopFaderKeepsTheBalance ()
    {
        final LoopsLevel level = new LoopsLevel (8);
        final double [] volumes =
        {
            0.8,
            0.4,
            Double.NaN
        };
        final double [] half = level.apply (volumes, 0.5, 1000);
        assertEquals (0.4, half[0], EPSILON);
        assertEquals (0.2, half[1], EPSILON);
        assertTrue (Double.isNaN (half[2]), "a missing track is left alone");
        assertEquals (0.5, level.getGain (), EPSILON);

        // The tracks now sit at the halved levels; the fader still knows their own levels
        final double [] full = level.apply (half, 1, 1010);
        assertEquals (0.8, full[0], EPSILON);
        assertEquals (0.4, full[1], EPSILON);
        assertEquals (0, level.apply (full, 0, 1020)[1], EPSILON, "the heel silences them");
        assertEquals (0.4, level.getLevel (1), EPSILON, "and it knows what to hand back");
    }


    @Test
    void aLevelChangedInBitwigWhileTheFaderRestsIsAdopted ()
    {
        final LoopsLevel level = new LoopsLevel (8);
        level.apply (new double []
        {
            0.8
        }, 1, 1000);
        // Resting at the toe, the track was turned down in Bitwig
        final double [] moved = level.apply (new double []
        {
            0.6
        }, 0.5, 1000 + LoopsLevel.SETTLED_MILLIS + 100);
        assertEquals (0.3, moved[0], EPSILON);
    }


    @Test
    void aMovingPedalDoesNotMistakeLateReportsForSomeoneElse ()
    {
        final LoopsLevel level = new LoopsLevel (8);
        level.apply (new double []
        {
            0.8
        }, 1, 1000);
        level.apply (new double []
        {
            0.8
        }, 0.5, 1010);
        // Bitwig still reports 0.8 - its answer to the write before last
        assertEquals (0.32, level.apply (new double []
        {
            0.8
        }, 0.4, 1020)[0], EPSILON);
    }


    @Test
    void rockingAtTheToeDoesNotWearTheLevelDown ()
    {
        final LoopsLevel level = new LoopsLevel (8);
        double [] volumes =
        {
            0.8
        };
        long now = 1000;
        for (int i = 0; i < 100; i++)
        {
            volumes = level.apply (volumes, i % 2 == 0 ? 126 / 127.0 : 1, now);
            now += 20;
        }
        assertEquals (0.8, level.apply (volumes, 1, now)[0], EPSILON);
    }


    @Test
    void untilItsFirstMoveTheLoopFaderIsFullUp ()
    {
        final LoopsLevel level = new LoopsLevel (8);
        assertEquals (1, level.getGain (), EPSILON, "which is where a picked-up pedal has to reach");
        assertTrue (Double.isNaN (level.getLevel (0)), "nothing moved, nothing to hand back");
        final int generation = level.getGeneration ();
        level.apply (new double []
        {
            0.5
        }, 0.2, 1000);
        level.reset ();
        assertEquals (1, level.getGain (), EPSILON);
        assertTrue (level.getGeneration () != generation, "a picked-up pedal starts over");
    }


    @Test
    void loopTrackVolumeTargetsKnowTheirTrack ()
    {
        assertEquals (0, ExpressionTarget.LOOP_1_VOLUME.getLoopTrack ());
        assertEquals (7, ExpressionTarget.LOOP_8_VOLUME.getLoopTrack ());
        assertEquals (ExpressionTarget.Kind.PARAMETER, ExpressionTarget.LOOP_3_VOLUME.getKind ());
        assertEquals (-1, ExpressionTarget.SELECTED_VOLUME.getLoopTrack ());
        assertEquals (-1, ExpressionTarget.FOCUSED_REMOTE_1.getLoopTrack ());
        assertEquals (-1, ExpressionTarget.ACTIVE_LOOP_VOLUME.getLoopTrack ());
        assertEquals (null, ExpressionTarget.ALL_LOOPS_VOLUME.toMidi (64, 0), "not a MIDI target");
    }
}
