// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.util.Labelled;


/**
 * The colour choices offered for a switch in the settings: the Pacer's twelve, plus {@link #AUTO}, which picks one
 * from what the switch does.
 */
public enum SwitchColour implements Labelled
{
    /** Pick a colour from the switch's action. */
    AUTO ("Automatic (from the action)", null),
    /** Dark until the switch has something to show. */
    OFF ("Off", PacerColour.OFF),
    /** Magenta. */
    MAGENTA ("Magenta", PacerColour.MAGENTA),
    /** Red. */
    RED ("Red", PacerColour.RED),
    /** Orange. */
    ORANGE ("Orange", PacerColour.ORANGE),
    /** Gold. */
    GOLD ("Gold", PacerColour.GOLD),
    /** Yellow. */
    YELLOW ("Yellow", PacerColour.YELLOW),
    /** Green. */
    GREEN ("Green", PacerColour.GREEN),
    /** Dark green. */
    DARK_GREEN ("Dark green", PacerColour.DARK_GREEN),
    /** Cyan. */
    CYAN ("Cyan", PacerColour.CYAN),
    /** Blue. */
    BLUE ("Blue", PacerColour.BLUE),
    /** Lavender. */
    LAVENDER ("Lavender", PacerColour.LAVENDER),
    /** Purple. */
    PURPLE ("Purple", PacerColour.PURPLE),
    /** White. */
    WHITE ("White", PacerColour.WHITE);


    private final String      label;
    private final PacerColour colour;


    SwitchColour (final String label, final PacerColour colour)
    {
        this.label = label;
        this.colour = colour;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * The colour to paint a switch that runs this action.
     *
     * @param action The switch's tap action, used when this is {@link #AUTO}
     * @return The colour
     */
    public PacerColour resolve (final Action action)
    {
        return this.colour == null ? automatic (action) : this.colour;
    }


    /**
     * A sensible colour for an action, so a custom board looks deliberate without picking twelve colours by hand.
     *
     * @param action The action
     * @return The colour
     */
    public static PacerColour automatic (final Action action)
    {
        if (action == Action.NONE)
            return PacerColour.OFF;
        if (action.isMode ())
            return PacerColour.WHITE;
        if (action.isFx ())
            return PacerColour.GREEN;
        if (action.isDestructive ())
            return PacerColour.RED;
        return switch (action)
        {
            case TRACKS_LEFT, TRACKS_RIGHT, ROW_PREVIOUS, ROW_NEXT, TRACKS_HERE, DUPLICATE_ROW -> PacerColour.LAVENDER;
            case PLAY_STOP_ALL, PLAY_ROW, TRANSPORT_PLAY_STOP -> PacerColour.GOLD;
            case STOP_ALL, RESET -> PacerColour.ORANGE;
            case MUTE_SELECTED, MUTE_ALL_TOGGLE -> PacerColour.BLUE;
            case SOLO_SELECTED -> PacerColour.YELLOW;
            case RECORD_NEXT_LAYER, LOOP_SELECTED, LAUNCHER_OVERDUB, MONITOR_SELECTED -> PacerColour.RED;
            case TAP_TEMPO -> PacerColour.CYAN;
            default -> PacerColour.WHITE;
        };
    }
}
