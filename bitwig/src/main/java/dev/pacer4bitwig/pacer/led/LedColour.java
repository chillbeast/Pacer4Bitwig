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
    OFF (ColorEx.BLACK, -1),
    /** Step 1. */
    WHITE (ColorEx.WHITE, -1),
    /** Step 2. */
    RED (ColorEx.RED, 0),
    /** Step 3. */
    GREEN (ColorEx.GREEN, 120),
    /** Step 4. */
    AMBER (ColorEx.ORANGE, 35),
    /** Step 5. */
    BLUE (ColorEx.BLUE, 225),
    /** Step 6. */
    PURPLE (ColorEx.PURPLE, 280);


    /** Colours with less saturation than this count as grey. */
    private static final double GREY_SATURATION = 0.2;
    /** Colours darker than this count as grey. */
    private static final double DARK            = 0.08;

    private final ColorEx       colorEx;
    /** Hue in degrees, -1 for no hue. */
    private final double        hue;


    LedColour (final ColorEx colorEx, final double hue)
    {
        this.colorEx = colorEx;
        this.hue = hue;
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


    /**
     * The Pacer colour closest to an RGB colour, e.g. a track colour: white for greys, otherwise the nearest hue.
     *
     * @param red Red, 0-1
     * @param green Green, 0-1
     * @param blue Blue, 0-1
     * @return The colour, never OFF
     */
    public static LedColour nearest (final double red, final double green, final double blue)
    {
        final double max = Math.max (red, Math.max (green, blue));
        final double min = Math.min (red, Math.min (green, blue));
        final double delta = max - min;
        if (max < DARK || delta / max < GREY_SATURATION)
            return WHITE;

        double hue;
        if (max == red)
            hue = 60 * ((green - blue) / delta);
        else if (max == green)
            hue = 60 * ((blue - red) / delta + 2);
        else
            hue = 60 * ((red - green) / delta + 4);
        if (hue < 0)
            hue += 360;

        LedColour best = WHITE;
        double bestDistance = Double.MAX_VALUE;
        for (final LedColour colour: values ())
        {
            if (colour.hue < 0)
                continue;
            final double difference = Math.abs (hue - colour.hue);
            final double distance = Math.min (difference, 360 - difference);
            if (distance < bestDistance)
            {
                best = colour;
                bestDistance = distance;
            }
        }
        return best;
    }
}
