// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * What a pedal does to its target on the first move after it got that target - a mode change, a new target in the
 * settings, startup.
 */
public enum PedalTakeover implements Labelled
{
    /** The target jumps to where the pedal is. */
    JUMP ("Jump to the pedal (absolute)"),
    /** The target stays put until the pedal passes its value, then follows. */
    PICKUP ("Pick up: wait until the pedal passes the current value");


    private final String label;


    PedalTakeover (final String label)
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
