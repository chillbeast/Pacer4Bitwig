// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

/**
 * Whether the shift layer is up, and why. While it is, every switch with something on its shift layer does that
 * instead ({@link ShiftedBoard}); the others keep their normal job.
 * <p>
 * A foot cannot hold one switch and press another, so besides "while held" the layer can be latched on and off, or
 * raised for the next press only. Pure: the controller calls it and repaints when it answers that something changed.
 */
public final class ShiftLayer
{
    /** How the layer is up. */
    public enum State
    {
        /** Not up. */
        OFF,
        /** Latched on until switched off again. */
        ON,
        /** Up for the next switch press only. */
        ONCE,
        /** Up while a switch or jack is held down. */
        HELD
    }


    private State state  = State.OFF;
    /** The control holding the layer up in {@link State#HELD}, -1 otherwise. */
    private int   heldBy = -1;


    /**
     * @return True while the layer is up
     */
    public boolean isOn ()
    {
        return this.state != State.OFF;
    }


    /**
     * @return How the layer is up
     */
    public State getState ()
    {
        return this.state;
    }


    /**
     * Latch the layer on, or switch it off whichever way it was up.
     */
    public void toggle ()
    {
        this.state = this.state == State.OFF ? State.ON : State.OFF;
        this.heldBy = -1;
    }


    /**
     * Raise the layer for the next press. Asking again before that press cancels it, and while it is latched or held
     * this switches it off - the same switch always gets you out.
     */
    public void once ()
    {
        this.state = this.state == State.OFF ? State.ONCE : State.OFF;
        this.heldBy = -1;
    }


    /**
     * Raise the layer while a control is held down.
     *
     * @param control The control holding it, see {@link #release(int)}
     */
    public void hold (final int control)
    {
        this.state = State.HELD;
        this.heldBy = control;
    }


    /**
     * A control was released. If it was the one holding the layer up, the layer goes down.
     *
     * @param control The control
     * @return True if the layer went down
     */
    public boolean release (final int control)
    {
        if (this.state != State.HELD || this.heldBy != control)
            return false;
        this.state = State.OFF;
        this.heldBy = -1;
        return true;
    }


    /**
     * A switch was pressed on the shift layer: a layer raised for one press has done its job.
     *
     * @return True if the layer went down
     */
    public boolean usedByPress ()
    {
        if (this.state != State.ONCE)
            return false;
        this.state = State.OFF;
        return true;
    }


    /**
     * Take the layer down, e.g. because the mode changed.
     *
     * @return True if it was up
     */
    public boolean reset ()
    {
        final boolean wasOn = this.isOn ();
        this.state = State.OFF;
        this.heldBy = -1;
        return wasOn;
    }
}
