// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.pacer4bitwig.pacer.controller.PacerMap;

import org.junit.jupiter.api.Test;


class SwitchRoleTest
{
    private static final int SIX_TRACKS = 6;


    private static SwitchRole role (final int switchIndex, final Mode mode, final boolean menuOpen)
    {
        return SwitchRole.of (switchIndex, mode, menuOpen, SIX_TRACKS);
    }


    @Test
    void sw6IsTheModeSwitchInEveryModeAndEvenWhileTheMenuIsOpen ()
    {
        for (final Mode mode: Mode.values ())
        {
            assertEquals (SwitchRole.MODE_SWITCH, role (Mode.MODE_SWITCH_INDEX, mode, false));
            assertEquals (SwitchRole.MODE_SWITCH, role (Mode.MODE_SWITCH_INDEX, mode, true));
        }
    }


    @Test
    void loopModeGivesTheBottomRowToTheLooper ()
    {
        for (int i = 0; i < 5; i++)
            assertEquals (SwitchRole.LOOP_TRACK, role (i, Mode.LOOP, false), "SW " + (i + 1));
        for (int i = PacerMap.FIRST_TOP_ROW_SWITCH; i < PacerMap.NUM_SWITCHES; i++)
            assertEquals (SwitchRole.ACTION, role (i, Mode.LOOP, false), "the top row runs actions");
    }


    @Test
    void aModeNeverHasMoreLoopSwitchesThanTheProjectHasTracks ()
    {
        // Three loop tracks: SW 4 and SW 5 have nothing to control
        assertEquals (SwitchRole.LOOP_TRACK, SwitchRole.of (2, Mode.LOOP, false, 3));
        assertEquals (SwitchRole.NONE, SwitchRole.of (3, Mode.LOOP, false, 3));
        assertEquals (SwitchRole.NONE, SwitchRole.of (4, Mode.LOOP, false, 3));
        // And with none at all, no switch loops
        for (int i = 0; i < 5; i++)
            assertEquals (SwitchRole.NONE, SwitchRole.of (i, Mode.LOOP, false, 0));
    }


    @Test
    void modesWithoutLoopSwitchesRunActionsEverywhere ()
    {
        for (final Mode mode: new Mode []
        {
            Mode.FX,
            Mode.MIX
        })
            for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
            {
                if (Mode.isModeSwitch (i))
                    continue;
                assertEquals (SwitchRole.ACTION, role (i, mode, false), mode + " SW " + i);
            }
    }


    @Test
    void theMenuTakesOverEverySwitchWhileSw6IsHeld ()
    {
        for (final Mode mode: Mode.values ())
        {
            assertEquals (SwitchRole.MODE_SLOT, role (0, mode, true), "SW 1 picks LOOP");
            assertEquals (SwitchRole.MODE_SLOT, role (1, mode, true), "SW 2 picks FX");
            assertEquals (SwitchRole.MODE_SLOT, role (2, mode, true), "SW 3 picks MIX");
            assertEquals (SwitchRole.MODE_SLOT, role (3, mode, true), "SW 4 picks SONG");
            assertEquals (SwitchRole.NAVIGATION, role (6, mode, true), "SW A");
            assertEquals (SwitchRole.NAVIGATION, role (9, mode, true), "SW D");
        }
    }


    @Test
    void everyBottomSwitchIsAModeSlotWhileTheMenuIsOpen ()
    {
        // None of them may fall back to LOOP's loop switches while the menu has the board
        for (int i = 0; i < 5; i++)
        {
            assertEquals (SwitchRole.MODE_SLOT, role (i, Mode.LOOP, true), "SW " + (i + 1));
            assertNotEquals (SwitchRole.LOOP_TRACK, role (i, Mode.LOOP, true));
        }
    }


    @Test
    void openingTheMenuNeverLeavesASwitchRunningItsModeAction ()
    {
        for (final Mode mode: Mode.values ())
            for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
            {
                final SwitchRole menuRole = role (i, mode, true);
                assertNotEquals (SwitchRole.ACTION, menuRole, mode + " SW " + i + " must not act while the menu is open");
                assertNotEquals (SwitchRole.LOOP_TRACK, menuRole, mode + " SW " + i + " must not loop while the menu is open");
            }
    }


    @Test
    void everySwitchHasARoleInEveryModeAndState ()
    {
        for (final Mode mode: Mode.values ())
            for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
                for (final boolean menuOpen: new boolean []
                {
                    false,
                    true
                })
                    assertNotEquals (null, role (i, mode, menuOpen), mode + " SW " + i);
    }
}
