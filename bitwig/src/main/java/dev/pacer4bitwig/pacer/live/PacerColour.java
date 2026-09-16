// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import de.mossgrabers.framework.controller.color.ColorEx;


/**
 * The twelve colours a Pacer LED can show (user guide p. 12, verified on hardware 2026-09-16). Each has a full (A)
 * and a dimmed (b) variant in consecutive byte values, so the dim value is always the full value plus one.
 */
public enum PacerColour
{
    /** Dark. */
    OFF (0x00, -1, ColorEx.BLACK),
    /** Colour 1. */
    MAGENTA (0x01, 300, ColorEx.PINK),
    /** Colour 2. */
    RED (0x03, 0, ColorEx.RED),
    /** Colour 3. */
    ORANGE (0x05, 20, ColorEx.ORANGE),
    /** Colour 4. */
    GOLD (0x07, 40, ColorEx.DARK_ORANGE),
    /** Colour 5. */
    YELLOW (0x09, 55, ColorEx.YELLOW),
    /** Colour 6. */
    GREEN (0x0B, 120, ColorEx.GREEN),
    /** Colour 7. Never chosen by {@link #nearest(double, double, double)} - it is reserved for dim states. */
    DARK_GREEN (0x0D, -1, ColorEx.DARK_GREEN),
    /** Colour 8. */
    CYAN (0x0F, 180, ColorEx.CYAN),
    /** Colour 9. */
    BLUE (0x11, 225, ColorEx.BLUE),
    /** Colour 10. */
    LAVENDER (0x13, 260, ColorEx.DARK_PURPLE),
    /** Colour 11. */
    PURPLE (0x15, 280, ColorEx.PURPLE),
    /** Colour 12. */
    WHITE (0x17, -1, ColorEx.WHITE);


    /** Colours with less saturation than this count as grey. */
    private static final double GREY_SATURATION = 0.2;
    /** Colours darker than this count as grey. */
    private static final double DARK            = 0.08;

    private final int           full;
    /** Hue in degrees, -1 for colours {@link #nearest(double, double, double)} never picks. */
    private final double        hue;
    private final ColorEx       colorEx;


    PacerColour (final int full, final double hue, final ColorEx colorEx)
    {
        this.full = full;
        this.hue = hue;
        this.colorEx = colorEx;
    }


    /**
     * @return The byte value of the full brightness variant
     */
    public int getFull ()
    {
        return this.full;
    }


    /**
     * @return The byte value of the dimmed variant, 0 for {@link #OFF}
     */
    public int getDim ()
    {
        return this.full == 0 ? 0 : this.full + 1;
    }


    /**
     * @param dimmed True for the dimmed variant
     * @return The byte value
     */
    public int getValue (final boolean dimmed)
    {
        return dimmed ? this.getDim () : this.full;
    }


    /**
     * @return The colour used by Bitwig's hardware simulator
     */
    public ColorEx getColorEx ()
    {
        return this.colorEx;
    }


    /**
     * The Pacer colour closest to an RGB colour, e.g. a track colour: white for greys, otherwise the nearest hue.
     *
     * @param red Red, 0-1
     * @param green Green, 0-1
     * @param blue Blue, 0-1
     * @return The colour, never OFF
     */
    public static PacerColour nearest (final double red, final double green, final double blue)
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

        PacerColour best = WHITE;
        double bestDistance = Double.MAX_VALUE;
        for (final PacerColour colour: values ())
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
