// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedMode;
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
     * @param mode The LED mode
     * @return The LED state
     */
    public static LedState forLoop (final LoopState state, final boolean overdubbing, final boolean muted, final LedMode mode)
    {
        return forLoop (state, overdubbing, muted, mode, LoopColours.DEFAULT);
    }


    /**
     * Get the LED state of a loop switch.
     *
     * @param state The slot state
     * @param overdubbing The clip plays while the launcher overdub records into it
     * @param muted The track is muted
     * @param mode The LED mode
     * @param colours The multi-colour palette
     * @return The LED state
     */
    public static LedState forLoop (final LoopState state, final boolean overdubbing, final boolean muted, final LedMode mode, final LoopColours colours)
    {
        if (mode == LedMode.MULTI_COLOUR)
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

        // Two-colour: only on/off reaches the Pacer, the colour is for Bitwig's simulator
        return switch (state)
        {
            case EMPTY -> LedState.DARK;
            case STOPPED -> new LedState (LedColour.RED, LedPattern.BLIP);
            case PLAYING -> {
                if (muted)
                    yield new LedState (LedColour.RED, LedPattern.BLIP);
                yield new LedState (LedColour.RED, overdubbing ? LedPattern.BLINK_MEDIUM : LedPattern.SOLID_DIP);
            }
            case PLAY_QUEUED, STOP_QUEUED, RECORD_QUEUED -> new LedState (LedColour.RED, LedPattern.BLINK_FAST);
            case RECORDING -> new LedState (LedColour.RED, LedPattern.BLINK_MEDIUM);
        };
    }
}
