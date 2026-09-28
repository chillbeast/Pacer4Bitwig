// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

/**
 * Decides whether {@link LiveBoard} may write to the Pacer right now. A write held back is not remembered as done, so
 * the next paint tries again once the gate opens.
 */
public interface WriteGate
{
    /** Always open: every write goes out. */
    WriteGate OPEN = new WriteGate ()
    {
        /** {@inheritDoc} */
        @Override
        public boolean mayWrite ()
        {
            return true;
        }
    };


    /**
     * @return True if a write may go out now
     */
    boolean mayWrite ();


    /**
     * The display name is what tells presets apart, so it may ask for more than a colour does.
     *
     * @return True if the display name may be written now
     */
    default boolean mayWriteName ()
    {
        return this.mayWrite ();
    }


    /**
     * A write was held back.
     */
    default void blocked ()
    {
        // Nobody waiting
    }


    /**
     * The display name was written.
     *
     * @param name The name, five characters
     */
    default void nameWritten (final String name)
    {
        // Nobody listening
    }
}
