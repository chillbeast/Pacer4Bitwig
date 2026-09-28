// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.util.Labelled;


/**
 * Which mode the custom layout changes. By default it is a fifth mode of its own, starting empty; pointed at a
 * built-in mode it starts from that mode's board and changes it in place - its menu slot, its name and its pedals -
 * so the Looper can be made yours without laying out all ten switches.
 */
public enum CustomTarget implements Labelled
{
    /** A fifth mode, CUST, on its own menu slot. */
    OWN ("Its own mode (CUST, starts empty)", Mode.CUSTOM),
    /** Changes the Looper mode. */
    LOOP ("The Looper mode (changes it)", Mode.LOOP),
    /** Changes the FX mode. */
    FX ("The FX mode (changes it)", Mode.FX),
    /** Changes the Mixer mode. */
    MIX ("The Mixer mode (changes it)", Mode.MIX),
    /** Changes the Song mode. */
    SONG ("The Song mode (changes it)", Mode.SONG);


    private final String label;
    private final Mode   mode;


    CustomTarget (final String label, final Mode mode)
    {
        this.label = label;
        this.mode = mode;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return The mode whose board the custom layout is - {@link Mode#CUSTOM} for a mode of its own
     */
    public Mode getMode ()
    {
        return this.mode;
    }
}
