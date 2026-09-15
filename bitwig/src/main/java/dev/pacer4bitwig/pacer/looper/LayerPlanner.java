// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

/**
 * The one-button looper: a single switch walks through the loop tracks of the row. It cancels a queued recording,
 * otherwise closes the one that is recording, otherwise starts recording on the first empty loop track.
 */
public final class LayerPlanner
{
    /** What the next press does. */
    public enum Kind
    {
        /** Start recording on {@link Step#index()}. */
        RECORD,
        /** Close the loop recording on {@link Step#index()}. */
        CLOSE,
        /** Cancel the recording queued on {@link Step#index()}. */
        CANCEL,
        /** Every loop track holds a loop. */
        FULL
    }


    /**
     * The planned step.
     *
     * @param kind What happens
     * @param index The loop track, -1 for FULL
     */
    public record Step (Kind kind, int index)
    {
        // Record
    }


    private LayerPlanner ()
    {
        // Utility
    }


    /**
     * Plan the next press.
     *
     * @param states The state of each loop track in the row, null for tracks that do not exist
     * @return The step
     */
    public static Step next (final LoopState [] states)
    {
        final int queued = indexOf (states, LoopState.RECORD_QUEUED);
        if (queued >= 0)
            return new Step (Kind.CANCEL, queued);
        final int recording = indexOf (states, LoopState.RECORDING);
        if (recording >= 0)
            return new Step (Kind.CLOSE, recording);
        final int empty = indexOf (states, LoopState.EMPTY);
        if (empty >= 0)
            return new Step (Kind.RECORD, empty);
        return new Step (Kind.FULL, -1);
    }


    /**
     * The loop the one-button looper is busy with.
     *
     * @param states The state of each loop track in the row, null for tracks that do not exist
     * @return The index of the loop recording or waiting to record, -1 if none
     */
    public static int focus (final LoopState [] states)
    {
        final int queued = indexOf (states, LoopState.RECORD_QUEUED);
        return queued >= 0 ? queued : indexOf (states, LoopState.RECORDING);
    }


    private static int indexOf (final LoopState [] states, final LoopState wanted)
    {
        for (int i = 0; i < states.length; i++)
            if (states[i] == wanted)
                return i;
        return -1;
    }
}
