// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

import dev.pacer4bitwig.pacer.controller.PacerMap;

import java.util.function.IntConsumer;
import java.util.function.Supplier;


/**
 * Turns a light code (see {@link LedState#code(long)}) into the CC messages which light one Pacer switch.
 * The DrivenByMoss light only calls this when the code changes, or when it is force-flushed.
 */
public final class SwitchLedWriter implements IntConsumer
{
    /** Sends one CC on the Pacer channel. */
    @FunctionalInterface
    public interface CcSender
    {
        /**
         * Send a CC.
         *
         * @param cc The controller number
         * @param value The value
         */
        void send (int cc, int value);
    }


    private final int                switchIndex;
    private final int                actionCC;
    private final Supplier<LedMode>  modeSupplier;
    private final CcSender           sender;
    private int                      litCC = -1;


    /**
     * Constructor.
     *
     * @param switchIndex 0-9
     * @param modeSupplier The current LED mode
     * @param sender Sends CCs to the Pacer
     */
    public SwitchLedWriter (final int switchIndex, final Supplier<LedMode> modeSupplier, final CcSender sender)
    {
        this.switchIndex = switchIndex;
        this.actionCC = PacerMap.switchCC (switchIndex);
        this.modeSupplier = modeSupplier;
        this.sender = sender;
    }


    /** {@inheritDoc} */
    @Override
    public void accept (final int code)
    {
        final int target = this.targetCC (code);

        // Clear the previously lit slot first, so the new colour is also the last message received
        if (this.litCC >= 0 && this.litCC != target)
            this.sender.send (this.litCC, 0);

        if (target >= 0)
            this.sender.send (target, 127);
        else if (this.litCC < 0)
            // Forced flush of a dark LED: repaint the off colour
            this.sender.send (this.actionCC, 0);

        this.litCC = target;
    }


    /**
     * Turn every CC this switch may have lit off, e.g. after the LED mode changed.
     */
    public void reset ()
    {
        this.sender.send (this.actionCC, 0);
        for (int step = PacerMap.FIRST_COLOUR_SLOT_STEP; step <= PacerMap.LAST_COLOUR_SLOT_STEP; step++)
            this.sender.send (PacerMap.colourSlotCC (this.switchIndex, step), 0);
        this.litCC = -1;
    }


    private int targetCC (final int code)
    {
        final LedColour colour = LedColour.fromCode (code);
        if (colour == LedColour.OFF)
            return -1;
        if (this.modeSupplier.get () != LedMode.MULTI_COLOUR || colour.getStep () < PacerMap.FIRST_COLOUR_SLOT_STEP)
            return this.actionCC;
        return PacerMap.colourSlotCC (this.switchIndex, colour.getStep ());
    }
}
