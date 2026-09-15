// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

import dev.pacer4bitwig.util.Labelled;


/**
 * How LED feedback is sent to the Pacer (see docs/PACER-MAP.md).
 */
public enum LedMode implements Labelled
{
    /** Step 1 only: 127 = on colour, 0 = off colour. State is shown with blink patterns. */
    TWO_COLOUR ("Two-colour (safe)"),
    /** Steps 2-6 act as colour slots on the same LED. Needs the multi-colour preset. */
    MULTI_COLOUR ("Multi-colour (experimental)");


    private final String label;


    LedMode (final String label)
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
