// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.live.LiveBoard;
import dev.pacer4bitwig.pacer.live.PacerColour;

import java.util.function.IntFunction;


/**
 * Paints the Pacer for the active mode, or for the menu while SW 6 is held. A switch shows its colour at full
 * brightness while it is on and dimmed while it is off, so the board stays readable when nothing is running.
 * <p>
 * The whole board is one burst of at most eleven messages - ten switches and the name - which the Pacer shows as a
 * single brief {@code LOAD SYS}. {@link LiveBoard} drops the ones that would not change anything.
 */
public final class ModePainter
{
    private ModePainter ()
    {
        // Utility
    }


    /**
     * Repaint everything for the current state.
     *
     * @param board The board to write to
     * @param state Which mode is active and whether the menu is open
     */
    public static void paint (final LiveBoard board, final ModeState state)
    {
        paint (board, state, index -> PacerColour.OFF);
    }


    /**
     * Repaint everything for the current state.
     *
     * @param board The board to write to
     * @param state Which mode is active and whether the menu is open
     * @param stateColour The colour a switch shows while it is on, or {@link PacerColour#OFF} to use the mode's own
     */
    public static void paint (final LiveBoard board, final ModeState state, final IntFunction<PacerColour> stateColour)
    {
        paint (board, state, state.getActive (), stateColour);
    }


    /**
     * Repaint everything, using a board that may not be the mode's own one - {@link Mode#CUSTOM} is laid out in the
     * settings.
     *
     * @param board The board to write to
     * @param state Which mode is active and whether the menu is open
     * @param active The active mode's board
     * @param stateColour The colour a switch shows while it is on, or {@link PacerColour#OFF} to use the mode's own
     */
    public static void paint (final LiveBoard board, final ModeState state, final ModeBoard active, final IntFunction<PacerColour> stateColour)
    {
        paint (board, state, active, active.getDisplayName (), stateColour);
    }


    /**
     * Repaint everything, with a display name the caller chooses - a mode can show what it is doing rather than its
     * own name.
     *
     * @param board The board to write to
     * @param state Which mode is active and whether the menu is open
     * @param active The active mode's board
     * @param name What to put on the display
     * @param stateColour The colour a switch shows while it is on, or {@link PacerColour#OFF} to use the mode's own
     */
    public static void paint (final LiveBoard board, final ModeState state, final ModeBoard active, final String name, final IntFunction<PacerColour> stateColour)
    {
        if (state.isMenuOpen ())
        {
            paintMenu (board, state.getActive ());
            return;
        }
        // The name is written once: going through paintMode would set the mode's own name first and then replace
        // it, which is two SysEx messages every single paint
        paintSwitches (board, active, stateColour);
        board.setName (name);
    }


    /**
     * Paint the switches of a mode and put its name on the display.
     *
     * @param board The board to write to
     * @param mode The mode
     */
    public static void paintMode (final LiveBoard board, final ModeBoard mode)
    {
        paintMode (board, mode, index -> PacerColour.OFF);
    }


    /**
     * Paint the switches of a mode and put its name on the display. A switch rests at its mode colour, dimmed, and
     * lights in whatever colour its state asks for - so the board reads even when nothing is running.
     *
     * @param board The board to write to
     * @param mode The mode
     * @param stateColour The colour a switch shows while it is on, or {@link PacerColour#OFF} to use the mode's own
     */
    public static void paintMode (final LiveBoard board, final ModeBoard mode, final IntFunction<PacerColour> stateColour)
    {
        paintSwitches (board, mode, stateColour);
        board.setName (mode.getDisplayName ());
    }


    private static void paintSwitches (final LiveBoard board, final ModeBoard mode, final IntFunction<PacerColour> stateColour)
    {
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            final SwitchLayout layout = mode.getLayout (i);
            final PacerColour state = stateColour.apply (i);
            final PacerColour on = state == PacerColour.OFF ? layout.colour () : state;
            board.setLed (i, on, false, layout.colour (), true, layout.row ().orStripOn (i));
        }
    }


    /**
     * Paint the mode menu: the modes on SW 1-5 with the active one at full brightness, navigation on SW A-D.
     *
     * @param board The board to write to
     * @param active The mode that is currently active
     */
    public static void paintMenu (final LiveBoard board, final Mode active)
    {
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            final PacerColour colour = ModeMenu.colourAt (i, active);
            // The active mode's slot is the one that is already "on", so it is the one shown bright
            final boolean dim = !ModeMenu.isActiveSlot (i, active) && !Mode.isModeSwitch (i);
            board.setLed (i, colour, dim, colour, true, LedRow.STRIP);
        }
        board.setName (ModeMenu.NAME);
    }
}
