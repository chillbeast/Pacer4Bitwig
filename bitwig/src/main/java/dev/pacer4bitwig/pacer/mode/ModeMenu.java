// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;


/**
 * The layer that appears while SW 6 is held: the nine other switches become mode slots and navigation.
 * <p>
 * Navigation lives here rather than in every mode because it is wanted from all of them - paying for it once leaves
 * each mode its full nine switches. SW C and SW D carry it because the panel already prints {@code ▼} and {@code ▲}
 * on exactly those two switches.
 */
public final class ModeMenu
{
    /** Shown on the display while the menu is open. */
    public static final String        NAME        = "MODE";

    /** Which mode each of SW 1-5 selects. */
    private static final Mode []      SLOTS       =
    {
        Mode.LOOP,
        Mode.FX,
        Mode.MIX,
        Mode.SONG,
        Mode.CUSTOM
    };

    /** The colour of each mode slot, in the same order. */
    private static final PacerColour [] SLOT_COLOURS =
    {
        PacerColour.GREEN,
        PacerColour.MAGENTA,
        PacerColour.BLUE,
        PacerColour.GOLD,
        PacerColour.CYAN
    };

    /** SW A-D: the navigation available from every mode. */
    private static final Action []    NAVIGATION  =
    {
        Action.TRACKS_LEFT,
        Action.TRACKS_RIGHT,
        Action.ROW_NEXT,
        Action.ROW_PREVIOUS
    };

    /** Navigation is lavender so the menu reads as two groups. */
    private static final PacerColour  NAV_COLOUR  = PacerColour.LAVENDER;


    private ModeMenu ()
    {
        // Constants only
    }


    /**
     * The mode a switch selects while the menu is open.
     *
     * @param switchIndex 0-9
     * @return The mode, or null if the switch is not a mode slot
     */
    public static Mode modeAt (final int switchIndex)
    {
        return switchIndex >= 0 && switchIndex < SLOTS.length ? SLOTS[switchIndex] : null;
    }


    /**
     * Which slot a mode sits in.
     *
     * @param mode The mode
     * @return The switch index, -1 if the mode has no slot
     */
    public static int slotOf (final Mode mode)
    {
        for (int i = 0; i < SLOTS.length; i++)
            if (SLOTS[i] == mode)
                return i;
        return -1;
    }


    /**
     * The navigation action a switch runs while the menu is open.
     *
     * @param switchIndex 0-9
     * @return The action, {@link Action#NONE} if the switch is not a navigation slot
     */
    public static Action actionAt (final int switchIndex)
    {
        final int nav = switchIndex - PacerMap.FIRST_TOP_ROW_SWITCH;
        return nav >= 0 && nav < NAVIGATION.length ? NAVIGATION[nav] : Action.NONE;
    }


    /**
     * The colour a switch shows while the menu is open.
     *
     * @param switchIndex 0-9
     * @param active The mode that is currently active
     * @return The colour
     */
    public static PacerColour colourAt (final int switchIndex, final Mode active)
    {
        if (Mode.isModeSwitch (switchIndex))
            return PacerColour.WHITE;
        if (switchIndex < SLOTS.length)
            return SLOT_COLOURS[switchIndex];
        return actionAt (switchIndex) == Action.NONE ? PacerColour.OFF : NAV_COLOUR;
    }


    /**
     * Is this switch the slot of the active mode? That slot is shown at full brightness, the others dimmed.
     *
     * @param switchIndex 0-9
     * @param active The mode that is currently active
     * @return True if the slot holds the active mode
     */
    public static boolean isActiveSlot (final int switchIndex, final Mode active)
    {
        return modeAt (switchIndex) == active;
    }
}
