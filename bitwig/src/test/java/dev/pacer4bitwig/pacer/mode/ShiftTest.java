// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.looper.Action;

import org.junit.jupiter.api.Test;


/**
 * The shift layer: its state, the board seen through it, the built-in layers and the custom layout's overlay.
 */
class ShiftTest
{
    private static final int SW_1 = 0;
    private static final int SW_5 = 4;
    private static final int SW_A = 6;
    private static final int SW_D = 9;


    @Test
    void aLatchedLayerStaysUpUntilSwitchedOff ()
    {
        final ShiftLayer shift = new ShiftLayer ();
        shift.toggle ();
        assertEquals (ShiftLayer.State.ON, shift.getState ());
        assertFalse (shift.usedByPress (), "a latched layer is not used up by a press");
        assertTrue (shift.isOn ());
        shift.toggle ();
        assertFalse (shift.isOn ());
    }


    @Test
    void aLayerForOnePressIsUsedUpByIt ()
    {
        final ShiftLayer shift = new ShiftLayer ();
        shift.once ();
        assertEquals (ShiftLayer.State.ONCE, shift.getState ());
        assertTrue (shift.usedByPress ());
        assertFalse (shift.isOn ());
        assertFalse (shift.usedByPress (), "only once");

        shift.once ();
        shift.once ();
        assertFalse (shift.isOn (), "asking twice before the press cancels it");
    }


    @Test
    void aHeldLayerGoesDownWithTheControlThatHoldsIt ()
    {
        final ShiftLayer shift = new ShiftLayer ();
        shift.hold (11);
        assertFalse (shift.release (3), "another control's release changes nothing");
        assertTrue (shift.isOn ());
        assertTrue (shift.release (11));
        assertFalse (shift.isOn ());
        assertFalse (shift.release (11));
    }


    @Test
    void resetTakesTheLayerDownWhateverRaisedIt ()
    {
        final ShiftLayer shift = new ShiftLayer ();
        assertFalse (shift.reset ());
        shift.hold (2);
        assertTrue (shift.reset ());
        assertFalse (shift.release (2), "the hold is forgotten");
    }


    @Test
    void aLatchedLayerSurvivesAHold ()
    {
        final ShiftLayer shift = new ShiftLayer ();
        shift.toggle ();
        shift.hold (12);
        assertTrue (shift.release (12));
        assertEquals (ShiftLayer.State.ON, shift.getState (), "still latched after the jack let go");
    }


    @Test
    void aShiftKeyStaysAShiftKeyOnItsOwnLayer ()
    {
        final CustomBoard board = new CustomBoard ();
        board.setTarget (CustomTarget.LOOP);
        board.setTap (SW_5, Action.SHIFT_TOGGLE);
        board.rebuild ();
        assertEquals (Action.SHIFT_TOGGLE, new ShiftedBoard (board).getLayout (SW_5).tap (), "not the Looper's shifted SW 5");
    }


    @Test
    void theShiftedBoardFallsThroughWhereTheLayerIsEmpty ()
    {
        final ShiftedBoard shifted = new ShiftedBoard (Mode.FX);
        assertSame (Mode.FX.getLayout (SW_1), shifted.getLayout (SW_1), "FX has nothing on SW 1's shift layer");
        assertEquals (Action.FX_5, shifted.getLayout (SW_A).tap ());
        assertSame (SwitchLayout.MODE_SWITCH, shifted.getLayout (Mode.MODE_SWITCH_INDEX));
        assertEquals (Mode.FX.getDisplayName (), shifted.getDisplayName ());

        final ShiftedBoard song = new ShiftedBoard (Mode.SONG);
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
            assertSame (Mode.SONG.getLayout (i), song.getLayout (i), "the Song mode has no shift layer yet");
    }


    @Test
    void theLoopersShiftLayerAddsLoopTracks5To8 ()
    {
        final ShiftedBoard shifted = new ShiftedBoard (Mode.LOOP);
        for (int i = 0; i < 4; i++)
        {
            assertEquals (i + 4, shifted.getLoopTrack (i), "shifted SW " + (i + 1) + " is loop track " + (i + 5));
            assertNotEquals (Mode.LOOP.getLayout (i).colour (), shifted.getLayout (i).colour (), "the second bank of loops rests in another colour");
        }
        assertFalse (shifted.isLoopSwitch (SW_5));
        assertEquals (Action.TRANSPORT_PLAY_STOP, shifted.getLayout (SW_D).tap ());
        assertTrue (shifted.countsBeats (), "the beat counter keeps running on the shift layer");
    }


    @Test
    void everyShiftLayerKeepsTheModeSwitchAndTheTopRowOffTheWordRow ()
    {
        for (final Mode mode: Mode.values ())
        {
            final SwitchLayout sw6 = mode.getShiftLayout (Mode.MODE_SWITCH_INDEX);
            assertTrue (sw6 == SwitchLayout.MODE_SWITCH || sw6.isUnassigned (), mode + " must not use SW 6 on its shift layer");
            for (int i = PacerMap.FIRST_TOP_ROW_SWITCH; i < PacerMap.NUM_SWITCHES; i++)
                assertNotEquals (LedRow.WORD, mode.getShiftLayout (i).row (), mode + " shifted SW " + i + " cannot light a word row");
        }
    }


    @Test
    void theCustomLayoutKeepsTheModesShiftLayerUntilToldOtherwise ()
    {
        final CustomBoard board = new CustomBoard ();
        board.setTarget (CustomTarget.LOOP);
        board.rebuild ();
        assertEquals (Mode.LOOP.getShiftLayout (SW_1), board.getShiftLayout (SW_1));

        board.setShiftTap (SW_1, Action.MUTE_LOOP_1);
        board.setShiftHold (SW_A, Action.CLEAR_ROW);
        board.rebuild ();
        assertEquals (Action.MUTE_LOOP_1, board.getShiftLayout (SW_1).tap ());
        assertEquals (-1, new ShiftedBoard (board).getLoopTrack (SW_1), "no longer a loop switch when shifted");
        assertEquals (Action.DUPLICATE_ROW, board.getShiftLayout (SW_A).tap (), "the tap stays as in the mode");
        assertEquals (Action.CLEAR_ROW, board.getShiftLayout (SW_A).hold ());
        assertEquals (Mode.LOOP.getLayout (SW_1).tap (), board.getLayout (SW_1).tap (), "the normal layer is untouched");
    }


    @Test
    void aCustomModeOfItsOwnStartsWithoutAShiftLayer ()
    {
        final CustomBoard board = new CustomBoard ();
        board.setTap (SW_1, Action.LOOP_1);
        board.rebuild ();
        assertSame (board.getLayout (SW_1), new ShiftedBoard (board).getLayout (SW_1), "SW 1 keeps its job while shifted");

        board.setShiftTap (SW_1, Action.LOOP_5);
        board.rebuild ();
        assertEquals (4, new ShiftedBoard (board).getLoopTrack (SW_1));
    }
}
