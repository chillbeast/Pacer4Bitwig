// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;

import org.junit.jupiter.api.Test;


class CustomBoardTest
{
    private static final int SW_1 = 0;
    private static final int SW_5 = 4;
    private static final int SW_A = 6;
    private static final int SW_D = 9;


    @Test
    void aModeOfItsOwnStartsEmpty ()
    {
        final CustomBoard board = new CustomBoard ();
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            if (Mode.isModeSwitch (i))
                assertSame (SwitchLayout.MODE_SWITCH, board.getLayout (i), "SW 6 stays the mode switch");
            else
                assertEquals (Action.NONE, board.getLayout (i).tap ());
        }
        assertEquals ("CUST", board.getDisplayName ());
        assertFalse (board.countsBeats (), "no loop switches, nothing to count for");
    }


    @Test
    void changingTheLooperStartsFromTheLooperBoard ()
    {
        final CustomBoard board = new CustomBoard ();
        board.setTarget (CustomTarget.LOOP);
        board.rebuild ();
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
            assertEquals (Mode.LOOP.getLayout (i), board.getLayout (i), "SW index " + i + " as in the Looper");
        assertEquals ("LOOP", board.getDisplayName (), "it is still the Looper on the display");
        assertTrue (board.countsBeats (), "and it keeps the beat counter");
    }


    @Test
    void oneSettingSwapsOneSwitch ()
    {
        // The request that started this: tap tempo where overdub is, everything else as in the Looper
        final CustomBoard board = new CustomBoard ();
        board.setTarget (CustomTarget.LOOP);
        board.setTap (SW_D, Action.TAP_TEMPO);
        board.rebuild ();

        final SwitchLayout swD = board.getLayout (SW_D);
        assertEquals (Action.TAP_TEMPO, swD.tap ());
        assertEquals (Mode.LOOP.getLayout (SW_D).hold (), swD.hold (), "the hold it did not touch stays the Looper's");
        assertEquals (SwitchColour.automatic (Action.TAP_TEMPO), swD.colour (), "a changed switch takes its action's colour");
        for (int i = 0; i < SW_D; i++)
            assertEquals (Mode.LOOP.getLayout (i), board.getLayout (i));
    }


    @Test
    void anUnchangedSwitchKeepsTheModesColourAndLight ()
    {
        // The Mixer lights printed words; changing nothing about SW 1 keeps its word and colour
        final CustomBoard board = new CustomBoard ();
        board.setTarget (CustomTarget.MIX);
        board.rebuild ();
        assertEquals (LedRow.WORD, board.getLayout (SW_1).row ());
        assertEquals (Mode.MIX.getLayout (SW_1).colour (), board.getLayout (SW_1).colour ());

        // A picked colour or LED always wins
        board.setColour (SW_1, SwitchColour.CYAN);
        board.setRow (SW_1, LedRow.STRIP);
        board.rebuild ();
        assertEquals (PacerColour.CYAN, board.getLayout (SW_1).colour ());
        assertEquals (LedRow.STRIP, board.getLayout (SW_1).row ());
    }


    @Test
    void theTopRowNeverLightsAWordRowItDoesNotHave ()
    {
        final CustomBoard board = new CustomBoard ();
        board.setRow (SW_A, LedRow.WORD);
        board.rebuild ();
        assertEquals (LedRow.STRIP, board.getLayout (SW_A).row ());
    }


    @Test
    void aLoopTrackTapMakesAnySwitchALoopSwitch ()
    {
        // Loops on the top row, which no built-in mode does
        final CustomBoard board = new CustomBoard ();
        board.setTap (SW_A, Action.LOOP_1);
        board.setTap (SW_D, Action.LOOP_4);
        board.rebuild ();
        assertEquals (0, board.getLoopTrack (SW_A));
        assertEquals (3, board.getLoopTrack (SW_D));
        assertEquals (SwitchLayout.LOOP_COLOUR, board.getLayout (SW_A).colour (), "loop switches rest in the loop colour");
        assertTrue (board.countsBeats (), "a board of its own with loop switches counts beats");
    }


    @Test
    void nothingMeansNothingEvenOnABuiltInSwitch ()
    {
        // "As in the mode" keeps a switch; "Nothing" empties it
        final CustomBoard board = new CustomBoard ();
        board.setTarget (CustomTarget.LOOP);
        board.setTap (SW_5, Action.NONE);
        board.setHold (SW_5, Action.NONE);
        board.rebuild ();
        assertEquals (Action.NONE, board.getLayout (SW_5).tap ());
        assertEquals (Action.NONE, board.getLayout (SW_5).hold ());
        assertEquals (PacerColour.OFF, board.getLayout (SW_5).colour (), "an empty switch stays dark");
    }


    @Test
    void aLoopSwitchCanBeTakenOver ()
    {
        final CustomBoard board = new CustomBoard ();
        board.setTarget (CustomTarget.LOOP);
        board.setTap (SW_1, Action.RECORD_NEXT_LAYER);
        board.rebuild ();
        assertFalse (board.isLoopSwitch (SW_1));
        assertEquals (Action.RECORD_NEXT_LAYER, board.getLayout (SW_1).tap ());
    }


    @Test
    void aNameWinsOverTheModesOwn ()
    {
        final CustomBoard board = new CustomBoard ();
        board.setTarget (CustomTarget.FX);
        board.rebuild ();
        assertEquals ("FX", board.getDisplayName ());
        board.setName (" GTR ");
        assertEquals ("GTR", board.getDisplayName ());
        board.setName ("");
        board.setTarget (CustomTarget.OWN);
        assertEquals ("CUST", board.getDisplayName ());
    }


    @Test
    void theModeSwitchCannotBeLaidOut ()
    {
        final CustomBoard board = new CustomBoard ();
        board.setTap (Mode.MODE_SWITCH_INDEX, Action.UNDO);
        board.rebuild ();
        assertSame (SwitchLayout.MODE_SWITCH, board.getLayout (Mode.MODE_SWITCH_INDEX));
        assertFalse (board.isLoopSwitch (Mode.MODE_SWITCH_INDEX));
    }


    @Test
    void theMenuCanHideAMode ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        state.setOffered (mode -> mode != Mode.CUSTOM);
        assertFalse (state.isOffered (Mode.CUSTOM));
        assertEquals (PacerColour.OFF, ModeMenu.colourAt (SW_5, Mode.LOOP, state::isOffered), "the custom slot goes dark");
        assertEquals (SwitchRole.NONE, SwitchRole.of (SW_5, Mode.LOOP, true, 6, state::isOffered), "and does nothing");
        assertEquals (SwitchRole.MODE_SLOT, SwitchRole.of (SW_1, Mode.LOOP, true, 6, state::isOffered));

        // Next mode skips it: SONG wraps straight back to LOOP
        state.activate (Mode.SONG);
        assertTrue (state.next ());
        assertEquals (Mode.LOOP, state.getActive ());
    }
}
