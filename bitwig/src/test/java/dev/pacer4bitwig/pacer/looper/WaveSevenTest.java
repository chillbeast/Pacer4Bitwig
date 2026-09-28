// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.live.PacerSysex;

import org.junit.jupiter.api.Test;


/** Loops and mutes anywhere, and the row on the display. */
class WaveSevenTest
{
    @Test
    void theRowFitsTheDisplay ()
    {
        assertEquals ("ROW 1", LooperText.displayRow (0));
        assertEquals ("ROW 9", LooperText.displayRow (8));
        assertEquals ("ROW10", LooperText.displayRow (9));
        assertEquals ("ROW99", LooperText.displayRow (98));
        assertEquals ("R100", LooperText.displayRow (99));
        for (int row = 0; row < 128; row++)
            assertTrue (LooperText.displayRow (row).length () <= PacerSysex.NAME_LENGTH, "row " + (row + 1) + " fits five characters");
    }


    @Test
    void everyLoopTrackHasAMute ()
    {
        for (int track = 0; track < 6; track++)
        {
            final Action loop = Action.loopTrack (track);
            final Action mute = Action.valueOf ("MUTE_LOOP_" + (track + 1));
            assertEquals (track, mute.getMutedLoopTrack ());
            assertEquals (-1, mute.getLoopTrack (), "a mute switch is not a loop switch");
            assertEquals (-1, loop.getMutedLoopTrack ());
            assertTrue (mute.isTimingCritical (), "a mute fires on press; Mute timing does the waiting");
        }
        assertEquals (-1, Action.MUTE_SELECTED.getMutedLoopTrack ());
    }


    @Test
    void rowsOnlyPlayAlongWhenAsked ()
    {
        // The default keeps the old behaviour: moving never launches anything
        assertEquals (RowMove.SELECT, RowMove.values ()[0]);
    }
}
