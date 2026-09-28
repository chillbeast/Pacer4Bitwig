// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import dev.pacer4bitwig.util.Labelled;


/**
 * How the extension makes sure the Pacer is on its preset before painting it ({@link PresetGuard}).
 */
public enum PresetCheck implements Labelled
{
    /** Ask before painting when the last answer is old, and every few seconds, which also spots a replugged Pacer. */
    REGULAR ("Before painting, and every 5 s (spots a Pacer plugged back in)"),
    /** Ask only before painting. */
    BEFORE_PAINTING ("Only before painting"),
    /** Never ask: paint whatever preset is loaded. */
    OFF ("Off: paint whatever preset is loaded");


    private final String label;


    PresetCheck (final String label)
    {
        this.label = label;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }
}
