// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.pacer.looper.Action;

/**
 * What a switch stands for at this moment. Everything that decides where a press goes lives here, so the controller
 * only has to dispatch.
 */
public enum SwitchRole
{
    /** SW 6: a tap toggles between the last two modes, a hold opens the menu. */
    MODE_SWITCH,
    /** The menu is open and this switch picks a mode. */
    MODE_SLOT,
    /** The menu is open and this switch navigates. */
    NAVIGATION,
    /** A loop track of the active mode. */
    LOOP_TRACK,
    /** Runs the mode's assigned action. */
    ACTION,
    /** Nothing is assigned to this switch right now. */
    NONE;


    /**
     * Work out what a switch does.
     *
     * @param switchIndex 0-9
     * @param board The active mode's board
     * @param menuOpen True while SW 6 is held
     * @param loopTrackCount How many loop tracks the project has - a mode cannot have more loop switches than that
     * @return The role
     */
    public static SwitchRole of (final int switchIndex, final ModeBoard board, final boolean menuOpen, final int loopTrackCount)
    {
        if (Mode.isModeSwitch (switchIndex))
            return MODE_SWITCH;

        if (menuOpen)
        {
            if (ModeMenu.modeAt (switchIndex) != null)
                return MODE_SLOT;
            return ModeMenu.actionAt (switchIndex) == Action.NONE ? NONE : NAVIGATION;
        }

        if (board.isLoopSwitch (switchIndex))
            // A mode may lay out more loop switches than the project has tracks; the extra ones do nothing
            return switchIndex < loopTrackCount ? LOOP_TRACK : NONE;

        final SwitchLayout layout = board.getLayout (switchIndex);
        final boolean assigned = layout.tap () != Action.NONE || layout.doubleTap () != Action.NONE || layout.hold () != Action.NONE;
        return assigned ? ACTION : NONE;
    }
}
