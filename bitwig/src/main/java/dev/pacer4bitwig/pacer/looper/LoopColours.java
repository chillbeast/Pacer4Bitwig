// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.util.Labelled;


/**
 * The multi-colour palette of loop switches.
 *
 * @param stopped A loop that holds a clip but does not play
 * @param playing A playing loop
 * @param recording A recording or overdubbing loop
 * @param muted A playing but muted loop
 */
public record LoopColours (LedColour stopped, LedColour playing, LedColour recording, LedColour muted)
{
    /** Amber, green, red, blue. */
    public static final LoopColours DEFAULT = new LoopColours (LedColour.AMBER, LedColour.GREEN, LedColour.RED, LedColour.BLUE);


    /**
     * The colours offered in the settings.
     */
    public enum Choice implements Labelled
    {
        /** White. */
        WHITE ("White", LedColour.WHITE),
        /** Red. */
        RED ("Red", LedColour.RED),
        /** Green. */
        GREEN ("Green", LedColour.GREEN),
        /** Amber. */
        AMBER ("Amber", LedColour.AMBER),
        /** Blue. */
        BLUE ("Blue", LedColour.BLUE),
        /** Purple. */
        PURPLE ("Purple", LedColour.PURPLE);


        private final String    label;
        private final LedColour colour;


        Choice (final String label, final LedColour colour)
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
         * @return The colour
         */
        public LedColour getColour ()
        {
            return this.colour;
        }


        /**
         * @param colour A colour
         * @return The matching choice, WHITE if there is none
         */
        public static Choice of (final LedColour colour)
        {
            for (final Choice choice: values ())
                if (choice.colour == colour)
                    return choice;
            return WHITE;
        }
    }
}
