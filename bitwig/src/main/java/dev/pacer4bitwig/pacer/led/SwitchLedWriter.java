// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

import dev.pacer4bitwig.pacer.controller.PacerMap;

import java.util.function.IntConsumer;


/**
 * Turns a light code (see {@link LedState#code(LedClock)}) into the CC that lights one Pacer switch: 127 shows the
 * switch's on colour, 0 its off colour. Which colours those are is written live as SysEx by
 * {@code live.LiveBoard}, so this only decides bright or dim.
 * <p>
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


    private final int      actionCC;
    private final CcSender sender;


    /**
     * Constructor.
     *
     * @param switchIndex 0-9
     * @param sender Sends CCs to the Pacer
     */
    public SwitchLedWriter (final int switchIndex, final CcSender sender)
    {
        this.actionCC = PacerMap.switchCC (switchIndex);
        this.sender = sender;
    }


    /** {@inheritDoc} */
    @Override
    public void accept (final int code)
    {
        this.sender.send (this.actionCC, code > 0 ? 127 : 0);
    }
}
