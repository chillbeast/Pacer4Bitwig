// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import dev.pacer4bitwig.util.Labelled;


/**
 * Which mode the extension starts in. {@link #REMEMBER} picks up where the project left off, which is saved with the
 * project - a looping project opens in the looper, a guitar project in the pedalboard.
 */
public enum StartupMode implements Labelled
{
    /** The mode this project was last left in. */
    REMEMBER ("Whatever this project used last", null),
    /** Always the looper. */
    LOOP ("Looper", Mode.LOOP),
    /** Always the pedalboard. */
    FX ("FX pedalboard", Mode.FX),
    /** Always the mixer. */
    MIX ("Mixer", Mode.MIX),
    /** Always the song board. */
    SONG ("Song", Mode.SONG),
    /** Always the custom board. */
    CUSTOM ("Custom", Mode.CUSTOM);


    private final String label;
    private final Mode   mode;


    StartupMode (final String label, final Mode mode)
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
     * The mode to start in.
     *
     * @param remembered The mode the project was last left in
     * @return The mode
     */
    public Mode resolve (final Mode remembered)
    {
        if (this.mode != null)
            return this.mode;
        return remembered == null ? Mode.LOOP : remembered;
    }
}
