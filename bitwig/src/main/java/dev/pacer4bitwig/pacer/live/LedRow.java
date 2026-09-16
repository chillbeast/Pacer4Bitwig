// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import dev.pacer4bitwig.util.Labelled;


/**
 * Which of a switch's LEDs lights - the Pacer's "LED Number" (verified on hardware 2026-09-16, docs/PACER-MAP.md).
 * Only one LED per switch lights at a time; the row is part of a mode's layout, so a switch can show its state on
 * the printed word when the mode's function matches it.
 */
public enum LedRow implements Labelled
{
    /** The colour strip on the switch itself. Always available. */
    STRIP (1, "Colour strip"),
    /** The transport icon row above the switch; on SW A-D their single label row. */
    ICON (2, "Icon row (above the switch)"),
    /** The word row - {@code Solo}, {@code Mute}, {@code Rec Arm}, {@code Click}, {@code Patch}. Not on SW A-D. */
    WORD (3, "Word row (SW 1-6 only)");


    private final int    number;
    private final String label;


    LedRow (final int number, final String label)
    {
        this.number = number;
        this.label = label;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return The Pacer's LED number, 1-3
     */
    public int getNumber ()
    {
        return this.number;
    }


    /**
     * SW A-D have only two LEDs, so they cannot light a word row.
     *
     * @param switchIndex 0-9
     * @return This row, or {@link #STRIP} when this switch has no such LED
     */
    public LedRow orStripOn (final int switchIndex)
    {
        return this == WORD && switchIndex >= 6 ? STRIP : this;
    }
}
