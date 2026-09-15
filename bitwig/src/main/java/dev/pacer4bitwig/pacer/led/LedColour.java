// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

import de.mossgrabers.framework.controller.color.ColorEx;


/**
 * The colours the extension can show. The ordinal is the light "code" handed through the DrivenByMoss light
 * cache and, in multi-colour mode, also the Pacer step whose LED shows the colour (see docs/PACER-MAP.md).
 */
public enum LedColour
{
    /** Dark. */
    OFF (ColorEx.BLACK),
    /** Step 1. */
    WHITE (ColorEx.WHITE),
    /** Step 2. */
    RED (ColorEx.RED),
    /** Step 3. */
    GREEN (ColorEx.GREEN),
    /** Step 4. */
    AMBER (ColorEx.ORANGE),
    /** Step 5. */
    BLUE (ColorEx.BLUE),
    /** Step 6. */
    PURPLE (ColorEx.PURPLE);


    private final ColorEx colorEx;


    LedColour (final ColorEx colorEx)
    {
        this.colorEx = colorEx;
    }


    /**
     * Get the colour used by Bitwig's hardware simulator.
     *
     * @return The colour
     */
    public ColorEx getColorEx ()
    {
        return this.colorEx;
    }


    /**
     * Get the Pacer step which carries this colour in multi-colour mode.
     *
     * @return 1-6, 0 for OFF
     */
    public int getStep ()
    {
        return this.ordinal ();
    }


    /**
     * Look up a colour by its code.
     *
     * @param code The code (ordinal)
     * @return The colour, OFF for unknown codes
     */
    public static LedColour fromCode (final int code)
    {
        final LedColour [] values = values ();
        return code > 0 && code < values.length ? values[code] : OFF;
    }
}
