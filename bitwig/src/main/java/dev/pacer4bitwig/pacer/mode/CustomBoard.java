// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;

import java.util.Arrays;


/**
 * The board laid out in the settings: a mode of its own, or a built-in mode with some switches changed. Every switch
 * setting can say "as in the mode", so only what differs has to be set - a Looper with overdub where tap tempo was
 * is one setting, not ten switches.
 * <p>
 * Pure: the configuration feeds it the settings and calls {@link #rebuild()}; painting reads the prebuilt layouts.
 */
public final class CustomBoard implements ModeBoard
{
    /** The display name of the custom mode when it is a mode of its own and has no name set. */
    public static final String   DEFAULT_NAME = "CUST";

    private CustomTarget          target      = CustomTarget.OWN;
    private String                name        = "";
    /** Null means "as in the mode". */
    private final Action []       tap         = new Action [PacerMap.NUM_SWITCHES];
    private final Action []       doubleTap   = new Action [PacerMap.NUM_SWITCHES];
    private final Action []       hold        = new Action [PacerMap.NUM_SWITCHES];
    private final LedRow []       row         = new LedRow [PacerMap.NUM_SWITCHES];
    private final SwitchColour [] colour      = new SwitchColour [PacerMap.NUM_SWITCHES];
    private final SwitchLayout [] layouts     = new SwitchLayout [PacerMap.NUM_SWITCHES];
    /** Worked out in {@link #rebuild()}: the LED flush asks for it on every switch, many times a second. */
    private boolean               countsBeats;


    /**
     * Constructor: a mode of its own with every switch as in the (empty) mode.
     */
    public CustomBoard ()
    {
        Arrays.fill (this.colour, SwitchColour.AUTO);
        this.rebuild ();
    }


    /**
     * @param target Which mode the layout changes
     */
    public void setTarget (final CustomTarget target)
    {
        this.target = target == null ? CustomTarget.OWN : target;
    }


    /**
     * @return Which mode the layout changes
     */
    public CustomTarget getTarget ()
    {
        return this.target;
    }


    /**
     * @param name The display name, blank for the mode's own
     */
    public void setName (final String name)
    {
        this.name = name == null ? "" : name.trim ();
    }


    /**
     * @param switchIndex 0-9
     * @param action The tap action, null for "as in the mode"
     */
    public void setTap (final int switchIndex, final Action action)
    {
        this.tap[switchIndex] = action;
    }


    /**
     * @param switchIndex 0-9
     * @param action The double-tap action, null for "as in the mode"
     */
    public void setDoubleTap (final int switchIndex, final Action action)
    {
        this.doubleTap[switchIndex] = action;
    }


    /**
     * @param switchIndex 0-9
     * @param action The hold action, null for "as in the mode"
     */
    public void setHold (final int switchIndex, final Action action)
    {
        this.hold[switchIndex] = action;
    }


    /**
     * @param switchIndex 0-9
     * @param ledRow Which LED lights, null for "as in the mode"
     */
    public void setRow (final int switchIndex, final LedRow ledRow)
    {
        this.row[switchIndex] = ledRow;
    }


    /**
     * @param switchIndex 0-9
     * @param switchColour The colour choice
     */
    public void setColour (final int switchIndex, final SwitchColour switchColour)
    {
        this.colour[switchIndex] = switchColour == null ? SwitchColour.AUTO : switchColour;
    }


    /**
     * Work the layouts out again after settings changed.
     */
    public void rebuild ()
    {
        final Mode base = this.target.getMode ();
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            if (Mode.isModeSwitch (i))
            {
                this.layouts[i] = SwitchLayout.MODE_SWITCH;
                continue;
            }
            // The built-in CUSTOM board is all empty switches, so a mode of its own starts empty
            final SwitchLayout from = base.getLayout (i);
            final Action tapAction = this.tap[i] == null ? from.tap () : this.tap[i];
            final Action doubleTapAction = this.doubleTap[i] == null ? from.doubleTap () : this.doubleTap[i];
            final Action holdAction = this.hold[i] == null ? from.hold () : this.hold[i];
            final LedRow ledRow = (this.row[i] == null ? from.row () : this.row[i]).orStripOn (i);
            final boolean unchanged = this.tap[i] == null && this.doubleTap[i] == null && this.hold[i] == null;
            this.layouts[i] = new SwitchLayout (tapAction, doubleTapAction, holdAction, this.colourOf (i, from, unchanged, tapAction, doubleTapAction, holdAction), ledRow);
        }

        // The Looper keeps its beat counter when it is changed; a mode of its own counts once it has loop switches
        boolean hasLoops = false;
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
            hasLoops |= this.isLoopSwitch (i);
        this.countsBeats = this.target == CustomTarget.LOOP || this.target == CustomTarget.OWN && hasLoops;
    }


    private PacerColour colourOf (final int switchIndex, final SwitchLayout from, final boolean unchanged, final Action tapAction, final Action doubleTapAction, final Action holdAction)
    {
        final SwitchColour choice = this.colour[switchIndex];
        // A switch that does what the mode made it do keeps the mode's colour, unless one was picked for it
        if (choice == SwitchColour.AUTO && unchanged)
            return from.colour ();
        return choice.resolve (tapAction.getLoopTrack () >= 0, tapAction, doubleTapAction, holdAction);
    }


    /** {@inheritDoc} */
    @Override
    public String getDisplayName ()
    {
        if (!this.name.isEmpty ())
            return this.name;
        return this.target == CustomTarget.OWN ? DEFAULT_NAME : this.target.getMode ().getDisplayName ();
    }


    /** {@inheritDoc} */
    @Override
    public SwitchLayout getLayout (final int switchIndex)
    {
        return this.layouts[switchIndex];
    }


    /** {@inheritDoc} */
    @Override
    public boolean countsBeats ()
    {
        return this.countsBeats;
    }
}
