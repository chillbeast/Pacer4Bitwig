// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.pacer.led.LedPattern;
import dev.pacer4bitwig.pacer.led.LedState;


/**
 * LED feedback of a loop switch (tables in docs/LOOPER.md).
 */
public final class LoopLeds
{
    private LoopLeds ()
    {
        // Utility
    }


    /**
     * Get the LED state of a loop switch with the default colours.
     *
     * @param state The slot state
     * @param overdubbing The clip plays while the launcher overdub records into it
     * @param muted The track is muted
     * @return The LED state
     */
    public static LedState forLoop (final LoopState state, final boolean overdubbing, final boolean muted)
    {
        return forLoop (state, overdubbing, muted, LoopColours.DEFAULT);
    }


    /**
     * Get the LED state of a loop switch. An empty slot has no colour of its own, so it falls back to the dimmed
     * colour its mode gives the switch.
     *
     * @param state The slot state
     * @param overdubbing The clip plays while the launcher overdub records into it
     * @param muted The track is muted
     * @param colours The palette
     * @return The LED state
     */
    public static LedState forLoop (final LoopState state, final boolean overdubbing, final boolean muted, final LoopColours colours)
    {
        return switch (state)
        {
            case EMPTY -> LedState.DARK;
            case STOPPED -> LedState.solid (colours.stopped ());
            case PLAYING -> {
                if (muted)
                    yield LedState.solid (colours.muted ());
                yield new LedState (overdubbing ? colours.recording () : colours.playing (), LedPattern.SOLID_DIP);
            }
            case PLAY_QUEUED -> new LedState (colours.playing (), LedPattern.BLINK_FAST);
            case STOP_QUEUED -> new LedState (colours.stopped (), LedPattern.BLINK_FAST);
            case RECORD_QUEUED -> new LedState (colours.recording (), LedPattern.BLINK_FAST);
            case RECORDING -> new LedState (colours.recording (), LedPattern.SOLID_DIP);
        };
    }
}
