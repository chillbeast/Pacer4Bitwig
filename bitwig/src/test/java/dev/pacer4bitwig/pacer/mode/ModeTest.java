// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.live.PacerSysex;
import dev.pacer4bitwig.pacer.looper.Action;

import org.junit.jupiter.api.Test;


class ModeTest
{
    @Test
    void everyModeLaysOutEverySwitch ()
    {
        for (final Mode mode: Mode.values ())
            for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
                assertNotNull (mode.getLayout (i), mode + " switch " + i);
    }


    @Test
    void sw6IsTheModeSwitchInEveryMode ()
    {
        assertEquals (5, Mode.MODE_SWITCH_INDEX, "SW 6, under the display and encoder");
        assertTrue (Mode.isModeSwitch (5));
        assertFalse (Mode.isModeSwitch (4));
        for (final Mode mode: Mode.values ())
        {
            assertSame (SwitchLayout.MODE_SWITCH, mode.getLayout (Mode.MODE_SWITCH_INDEX), mode + " must not use SW 6");
            assertFalse (mode.isLoopSwitch (Mode.MODE_SWITCH_INDEX), mode + " must not loop on SW 6");
        }
    }


    @Test
    void displayNamesFitThePacerDisplay ()
    {
        for (final Mode mode: Mode.values ())
        {
            assertTrue (mode.getDisplayName ().length () <= PacerSysex.NAME_LENGTH, mode + " name is too long");
            assertFalse (mode.getDisplayName ().isEmpty (), mode + " needs a name");
            assertEquals (mode.getDisplayName (), PacerSysex.pad (mode.getDisplayName ()).trim (), mode + " survives padding");
        }
    }


    @Test
    void loopModeOwnsTheBottomRowUpToTheModeSwitch ()
    {
        assertEquals (5, Mode.LOOP.getLoopSwitches ());
        for (int i = 0; i < 5; i++)
            assertTrue (Mode.LOOP.isLoopSwitch (i), "SW " + (i + 1) + " is a loop track");
        assertFalse (Mode.LOOP.isLoopSwitch (6), "the top row is not a loop track");
        assertEquals (0, Mode.FX.getLoopSwitches (), "FX has no loop switches");
        assertEquals (0, Mode.MIX.getLoopSwitches (), "MIX has no loop switches");
    }


    @Test
    void topRowNeverUsesTheWordRowBecauseItHasNone ()
    {
        // SW A-D have only a colour strip and one label row; there is no LED number 3 on them
        for (final Mode mode: Mode.values ())
            for (int i = PacerMap.FIRST_TOP_ROW_SWITCH; i < PacerMap.NUM_SWITCHES; i++)
                assertFalse (mode.getLayout (i).row () == LedRow.WORD, mode + " SW " + i + " cannot light a word row");
    }


    @Test
    void mixPutsItsFunctionsUnderThePrintedWords ()
    {
        // The panel prints Solo, Mute, Rec Arm and Click above SW 1-4, so the switch labels itself
        assertEquals (Action.SOLO_SELECTED, Mode.MIX.getLayout (0).tap (), "SW 1 is under 'Solo'");
        assertEquals (Action.MUTE_SELECTED, Mode.MIX.getLayout (1).tap (), "SW 2 is under 'Mute'");
        assertEquals (Action.MONITOR_SELECTED, Mode.MIX.getLayout (2).tap (), "SW 3 is under 'Rec Arm'");
        assertEquals (Action.METRONOME, Mode.MIX.getLayout (3).tap (), "SW 4 is under 'Click'");
        for (int i = 0; i < 4; i++)
            assertEquals (LedRow.WORD, Mode.MIX.getLayout (i).row (), "SW " + (i + 1) + " lights its word");
    }


    @Test
    void loopSwitchesCarryNoActionsOfTheirOwn ()
    {
        for (int i = 0; i < Mode.LOOP.getLoopSwitches (); i++)
        {
            final SwitchLayout layout = Mode.LOOP.getLayout (i);
            assertEquals (Action.NONE, layout.tap (), "a loop switch gets its behaviour from the looper");
            assertEquals (Action.NONE, layout.hold ());
        }
    }


    @Test
    void fxModeUsesFxActionsOnTheTopRowAndInstrumentsBelow ()
    {
        for (int i = 0; i < 4; i++)
            assertTrue (Mode.FX.getLayout (i).tap ().isFx (), "SW " + (i + 1) + " focuses an instrument");
        assertEquals (Action.FX_1, Mode.FX.getLayout (6).tap ());
        assertEquals (Action.FX_4, Mode.FX.getLayout (9).tap ());
        assertEquals (Action.MOMENTARY, Mode.FX.getLayout (6).hold (), "hold an FX switch for a momentary kick");
    }


    @Test
    void menuOffersTheModesOnTheBottomRowAndNavigationOnTop ()
    {
        assertEquals (Mode.LOOP, ModeMenu.modeAt (0));
        assertEquals (Mode.FX, ModeMenu.modeAt (1));
        assertEquals (Mode.MIX, ModeMenu.modeAt (2));
        assertEquals (Mode.SONG, ModeMenu.modeAt (3));
        assertEquals (Mode.CUSTOM, ModeMenu.modeAt (4), "the custom board");
        assertNull (ModeMenu.modeAt (Mode.MODE_SWITCH_INDEX), "SW 6 is the menu switch itself");
        assertNull (ModeMenu.modeAt (6), "the top row navigates");

        assertEquals (Action.TRACKS_LEFT, ModeMenu.actionAt (6), "SW A");
        assertEquals (Action.TRACKS_RIGHT, ModeMenu.actionAt (7), "SW B");
        assertEquals (Action.ROW_NEXT, ModeMenu.actionAt (8), "SW C is printed with a down arrow");
        assertEquals (Action.ROW_PREVIOUS, ModeMenu.actionAt (9), "SW D is printed with an up arrow");
        assertEquals (Action.NONE, ModeMenu.actionAt (0), "the bottom row picks modes, it does not navigate");
    }


    @Test
    void menuColoursSeparateModesFromNavigation ()
    {
        assertEquals (PacerColour.LAVENDER, ModeMenu.colourAt (6, Mode.LOOP));
        assertEquals (PacerColour.WHITE, ModeMenu.colourAt (Mode.MODE_SWITCH_INDEX, Mode.LOOP));
        for (int slot = 0; slot < 5; slot++)
            assertNotEquals (PacerColour.OFF, ModeMenu.colourAt (slot, Mode.LOOP), "every slot holds a mode, so every slot lights");
        assertTrue (ModeMenu.isActiveSlot (0, Mode.LOOP));
        assertFalse (ModeMenu.isActiveSlot (0, Mode.FX));
        assertTrue (ModeMenu.NAME.length () <= PacerSysex.NAME_LENGTH);
    }


    @Test
    void songPutsItsFunctionsUnderThePrintedTransportIcons ()
    {
        // The panel prints loop, rewind, fast forward, stop and play above SW 1-5
        assertEquals (Action.PLAY_STOP_ALL, Mode.SONG.getLayout (0).tap (), "SW 1 is under the loop icon");
        assertEquals (Action.ROW_PREVIOUS, Mode.SONG.getLayout (1).tap (), "SW 2 is under rewind");
        assertEquals (Action.ROW_NEXT, Mode.SONG.getLayout (2).tap (), "SW 3 is under fast forward");
        assertEquals (Action.STOP_ALL, Mode.SONG.getLayout (3).tap (), "SW 4 is under stop");
        assertEquals (Action.TRANSPORT_PLAY_STOP, Mode.SONG.getLayout (4).tap (), "SW 5 is under play");
        for (int i = 0; i < 5; i++)
            assertEquals (LedRow.ICON, Mode.SONG.getLayout (i).row (), "SW " + (i + 1) + " lights its icon");
    }


    @Test
    void everyModeWithASlotIsReachableByCycling ()
    {
        Mode mode = Mode.LOOP;
        final java.util.Set<Mode> seen = new java.util.HashSet<> ();
        for (int i = 0; i < Mode.values ().length; i++)
        {
            seen.add (mode);
            mode = mode.next ();
        }
        assertEquals (Mode.LOOP, mode, "cycling wraps round");
        for (final Mode candidate: Mode.values ())
            if (ModeMenu.slotOf (candidate) >= 0)
                assertTrue (seen.contains (candidate), candidate + " is reachable");
    }


    @Test
    void slotOfFindsWhereAModeLives ()
    {
        assertEquals (0, ModeMenu.slotOf (Mode.LOOP));
        assertEquals (3, ModeMenu.slotOf (Mode.SONG));
        for (final Mode mode: Mode.values ())
            assertTrue (ModeMenu.slotOf (mode) >= 0, mode + " needs a menu slot, or it cannot be reached");
    }


    @Test
    void modeActionsAreRunByTheControllerNotTheLooper ()
    {
        for (final Action action: new Action []
        {
            Action.MODE_NEXT,
            Action.MODE_TOGGLE,
            Action.MODE_LOOP,
            Action.MODE_FX,
            Action.MODE_MIX,
            Action.MODE_SONG
        })
        {
            assertTrue (action.isMode (), action + " is a mode action");
            assertFalse (action.isFx (), action + " is not an FX action");
            assertFalse (action.isDestructive (), action + " deletes nothing");
        }
        assertFalse (Action.UNDO.isMode ());
        assertFalse (Action.FX_1.isMode ());
    }


    @Test
    void nextMovesThroughTheModesAndComesBack ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        assertTrue (state.next ());
        assertEquals (Mode.FX, state.getActive ());
        assertTrue (state.next ());
        assertEquals (Mode.MIX, state.getActive ());
        assertTrue (state.next ());
        assertEquals (Mode.SONG, state.getActive ());
        assertTrue (state.next ());
        assertEquals (Mode.CUSTOM, state.getActive ());
        assertTrue (state.next ());
        assertEquals (Mode.LOOP, state.getActive (), "wraps round");
    }


    @Test
    void startupModeCanFollowTheProjectOrPinAMode ()
    {
        assertEquals (Mode.SONG, StartupMode.REMEMBER.resolve (Mode.SONG), "follows the project");
        assertEquals (Mode.LOOP, StartupMode.REMEMBER.resolve (null), "a project that never said falls back to the looper");
        assertEquals (Mode.FX, StartupMode.FX.resolve (Mode.SONG), "an explicit choice wins");
        for (final StartupMode startup: StartupMode.values ())
            assertNotNull (startup.resolve (Mode.MIX), startup + " must resolve to something");
    }


    @Test
    void everyModeIsReachableFromTheStartupSetting ()
    {
        for (final Mode mode: Mode.values ())
        {
            boolean found = false;
            for (final StartupMode startup: StartupMode.values ())
                if (startup != StartupMode.REMEMBER && startup.resolve (null) == mode)
                    found = true;
            assertTrue (found, mode + " has no entry in Mode at startup");
        }
    }


    @Test
    void tapTogglesBetweenTheLastTwoModes ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        assertEquals (Mode.LOOP, state.getActive ());
        assertFalse (state.toggle (), "nothing to go back to yet");

        assertTrue (state.activate (Mode.FX));
        assertEquals (Mode.FX, state.getActive ());
        assertEquals (Mode.LOOP, state.getPrevious ());

        assertTrue (state.toggle ());
        assertEquals (Mode.LOOP, state.getActive (), "back to where we were");
        assertTrue (state.toggle ());
        assertEquals (Mode.FX, state.getActive (), "and forth again");
    }


    @Test
    void activatingTheCurrentModeChangesNothing ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        assertFalse (state.activate (Mode.LOOP));
        assertFalse (state.activate (null));
        assertEquals (Mode.LOOP, state.getActive ());
    }


    @Test
    void theMenuLatchesSoOneFootCanUseIt ()
    {
        // A foot cannot hold SW 6 and press SW 1 at the same time, so the menu stays open when the foot comes off
        final ModeState state = new ModeState (Mode.LOOP);
        assertFalse (state.isMenuOpen ());
        state.openMenu ();
        assertTrue (state.isMenuOpen ());
        assertTrue (state.isMenuOpen (), "releasing SW 6 does not close it - only a tap does");
    }


    @Test
    void tappingTheModeSwitchClosesTheMenuWithoutChangingMode ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        state.activate (Mode.FX);
        state.openMenu ();
        assertFalse (state.tapModeSwitch (), "closing is not a mode change");
        assertFalse (state.isMenuOpen ());
        assertEquals (Mode.FX, state.getActive (), "a look costs nothing");
    }


    @Test
    void tappingTheModeSwitchWithTheMenuShutTogglesInstead ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        state.activate (Mode.MIX);
        assertTrue (state.tapModeSwitch ());
        assertEquals (Mode.LOOP, state.getActive ());
        assertTrue (state.tapModeSwitch ());
        assertEquals (Mode.MIX, state.getActive ());
    }


    @Test
    void pickingAModeClosesTheMenu ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        state.openMenu ();
        assertTrue (state.select (2), "SW 3 picks MIX");
        assertEquals (Mode.MIX, state.getActive ());
        assertEquals (Mode.LOOP, state.getPrevious ());
        assertFalse (state.isMenuOpen (), "one press is the whole gesture");
    }


    @Test
    void pickingTheModeAlreadyActiveJustClosesTheMenu ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        state.openMenu ();
        assertFalse (state.select (0), "LOOP is already active, so nothing changed");
        assertFalse (state.isMenuOpen ());
        assertEquals (Mode.LOOP, state.getActive ());
    }


    @Test
    void everySlotPicksAModeAndClosesTheMenu ()
    {
        for (int slot = 0; slot < 5; slot++)
        {
            final ModeState state = new ModeState (Mode.LOOP);
            state.openMenu ();
            state.select (slot);
            assertEquals (ModeMenu.modeAt (slot), state.getActive (), "slot " + slot);
            assertFalse (state.isMenuOpen (), "one press is the whole gesture");
        }
    }


    @Test
    void aSlotWithNoModeWouldStillCloseTheMenuRatherThanLeavingItStuck ()
    {
        // Navigation is not a slot, so it must not be mistaken for one
        final ModeState state = new ModeState (Mode.LOOP);
        state.openMenu ();
        assertFalse (state.select (6), "SW A navigates");
        assertFalse (state.isMenuOpen ());
    }


    @Test
    void theCustomBoardIsLaidOutInTheSettingsNotInCode ()
    {
        // Mode.CUSTOM carries placeholders; the controller swaps in the board built from the settings
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            if (Mode.isModeSwitch (i))
                continue;
            assertEquals (Action.NONE, Mode.CUSTOM.getLayout (i).tap (), "SW " + i + " is a placeholder");
        }
        assertEquals (0, Mode.CUSTOM.getLoopSwitches ());
        assertEquals ("CUST", Mode.CUSTOM.getDisplayName ());
    }


    @Test
    void automaticColoursGiveEveryActionSomethingSensible ()
    {
        assertEquals (PacerColour.OFF, SwitchColour.automatic (Action.NONE), "nothing assigned stays dark");
        assertEquals (PacerColour.WHITE, SwitchColour.automatic (Action.MODE_NEXT), "mode actions are white");
        assertEquals (PacerColour.GREEN, SwitchColour.automatic (Action.FX_1), "FX actions are green");
        assertEquals (PacerColour.RED, SwitchColour.automatic (Action.CLEAR_ROW), "destructive actions are red");
        assertEquals (PacerColour.LAVENDER, SwitchColour.automatic (Action.ROW_NEXT), "navigation is lavender");
        for (final Action action: Action.values ())
        {
            final PacerColour colour = SwitchColour.automatic (action);
            if (action != Action.NONE)
                assertNotEquals (PacerColour.OFF, colour, action + " needs a colour");
        }
    }


    @Test
    void anExplicitColourWinsOverTheAutomaticOne ()
    {
        assertEquals (PacerColour.CYAN, SwitchColour.CYAN.resolve (Action.UNDO));
        assertEquals (PacerColour.OFF, SwitchColour.OFF.resolve (Action.UNDO));
        assertEquals (SwitchColour.automatic (Action.UNDO), SwitchColour.AUTO.resolve (Action.UNDO));
    }


    @Test
    void theWordRowFallsBackToTheStripOnSwitchesThatHaveNone ()
    {
        // SW A-D have only two LEDs, so a custom layout asking for the word row must not go dark
        assertEquals (LedRow.WORD, LedRow.WORD.orStripOn (0));
        assertEquals (LedRow.WORD, LedRow.WORD.orStripOn (5));
        assertEquals (LedRow.STRIP, LedRow.WORD.orStripOn (6), "SW A");
        assertEquals (LedRow.STRIP, LedRow.WORD.orStripOn (9), "SW D");
        assertEquals (LedRow.ICON, LedRow.ICON.orStripOn (9), "the icon row exists on SW A-D");
    }


    @Test
    void afterUsingTheMenuTheTapStillGoesBack ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        state.openMenu ();
        state.select (1);
        assertEquals (Mode.FX, state.getActive ());
        assertTrue (state.tapModeSwitch ());
        assertEquals (Mode.LOOP, state.getActive ());
    }
}
