// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

/**
 * A board seen through its shift layer: a switch with anything on its shift layer does that, every other switch
 * keeps its normal job, and SW 6 - like any switch whose tap is a shift action - stays what it is. Everything that
 * reads a layout - roles, loop switches, colours, the beat counter - works on this view unchanged, which is what makes
 * the shift layer cheap.
 */
public final class ShiftedBoard implements ModeBoard
{
    private final ModeBoard base;


    /**
     * Constructor.
     *
     * @param base The board whose shift layer to show
     */
    public ShiftedBoard (final ModeBoard base)
    {
        this.base = base;
    }


    /**
     * @return The board this is the shift layer of
     */
    public ModeBoard getBase ()
    {
        return this.base;
    }


    /** {@inheritDoc} */
    @Override
    public String getDisplayName ()
    {
        return this.base.getDisplayName ();
    }


    /** {@inheritDoc} */
    @Override
    public SwitchLayout getLayout (final int switchIndex)
    {
        final SwitchLayout normal = this.base.getLayout (switchIndex);
        // A shift key stays a shift key on its own layer, so the same switch takes the layer down again
        if (Mode.isModeSwitch (switchIndex) || normal.tap ().isShift ())
            return normal;
        final SwitchLayout shifted = this.base.getShiftLayout (switchIndex);
        return shifted.isUnassigned () ? normal : shifted;
    }


    /** {@inheritDoc} */
    @Override
    public SwitchLayout getShiftLayout (final int switchIndex)
    {
        return this.base.getShiftLayout (switchIndex);
    }


    /** {@inheritDoc} */
    @Override
    public boolean countsBeats ()
    {
        return this.base.countsBeats ();
    }
}
