// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;


/**
 * The {@link WriteGate} of a {@link PresetGuard}: writes wait while the guard is unsure, and a held-back write is what
 * makes it ask. Switched off, every write goes out as before.
 */
public final class GuardedGate implements WriteGate
{
    private final PresetGuard     guard;
    private final BooleanSupplier enabled;
    private final LongSupplier    clock;
    private boolean               waiting;


    /**
     * Constructor.
     *
     * @param guard The guard
     * @param enabled True while the check is switched on
     * @param clock Milliseconds
     */
    public GuardedGate (final PresetGuard guard, final BooleanSupplier enabled, final LongSupplier clock)
    {
        this.guard = guard;
        this.enabled = enabled;
        this.clock = clock;
    }


    /** {@inheritDoc} */
    @Override
    public boolean mayWrite ()
    {
        return !this.enabled.getAsBoolean () || this.guard.mayWrite (this.clock.getAsLong ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean mayWriteName ()
    {
        return !this.enabled.getAsBoolean () || this.guard.mayWriteName (this.clock.getAsLong ());
    }


    /** {@inheritDoc} */
    @Override
    public void blocked ()
    {
        this.waiting = true;
    }


    /** {@inheritDoc} */
    @Override
    public void nameWritten (final String name)
    {
        this.guard.nameWritten (name, this.clock.getAsLong ());
    }


    /**
     * @return True if a write was held back since the last call
     */
    public boolean takeWaiting ()
    {
        final boolean wasWaiting = this.waiting;
        this.waiting = false;
        return wasWaiting;
    }
}
