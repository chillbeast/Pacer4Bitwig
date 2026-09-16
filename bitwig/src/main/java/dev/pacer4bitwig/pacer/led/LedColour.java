// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

import de.mossgrabers.framework.controller.color.ColorEx;

import dev.pacer4bitwig.pacer.live.PacerColour;


/**
 * The colours the extension can show. The ordinal is the light "code" handed through the DrivenByMoss light cache,
 * which only carries an int; {@link #toPacer()} turns it into the colour actually written to the hardware.
 */
public enum LedColour
{
    /** Dark. */
    OFF (ColorEx.BLACK, -1),
    /** White. */
    WHITE (ColorEx.WHITE, -1),
    /** Red. */
    RED (ColorEx.RED, 0),
    /** Green. */
    GREEN (ColorEx.GREEN, 120),
    /** Amber. */
    AMBER (ColorEx.ORANGE, 35),
    /** Blue. */
    BLUE (ColorEx.BLUE, 225),
    /** Purple. */
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
     * The hardware colour this state colour is written as. The light cache only carries an int, so state still
     * travels as a {@link LedColour}; the real colour reaches the Pacer as a live SysEx write.
     *
     * @return The Pacer colour
     */
    public PacerColour toPacer ()
    {
        return switch (this)
        {
            case OFF -> PacerColour.OFF;
            case WHITE -> PacerColour.WHITE;
            case RED -> PacerColour.RED;
            case GREEN -> PacerColour.GREEN;
            case AMBER -> PacerColour.GOLD;
            case BLUE -> PacerColour.BLUE;
            case PURPLE -> PacerColour.PURPLE;
        };
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
