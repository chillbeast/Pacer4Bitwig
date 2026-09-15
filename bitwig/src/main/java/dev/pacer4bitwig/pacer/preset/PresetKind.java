// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.preset;

import dev.pacer4bitwig.util.Labelled;


/**
 * The Pacer4Bitwig preset selected on the Pacer. It decides what the switches, pedals and LEDs do.
 */
public enum PresetKind implements Labelled
{
    /** The looper preset (D1). */
    LOOPER ("Looper preset"),
    /** The FX preset (D2): a pedalboard for live instruments. */
    FX ("FX preset");


    private final String label;


    PresetKind (final String label)
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
