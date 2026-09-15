// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

/**
 * What a tap on a loop switch does, decided from the slot state alone.
 */
public enum LoopAction
{
    /** Arm (optional) and record into the slot. */
    RECORD,
    /** Launch the slot: plays a stopped clip, closes a recording, cancels a queued stop. */
    PLAY,
    /** Stop the track: stops playback, cancels queued recording or playback. */
    STOP,
    /** Mute or unmute the track. */
    TOGGLE_MUTE,
    /** Toggle the launcher overdub (note clips). */
    OVERDUB,
    /** Nothing. */
    NONE;


    /**
     * Decide the tap action.
     *
     * @param state The slot state
     * @param playingTapAction What to do with a playing loop
     * @return The action
     */
    public static LoopAction onTap (final LoopState state, final PlayingTapAction playingTapAction)
    {
        return switch (state)
        {
            case EMPTY -> RECORD;
            case RECORDING, STOP_QUEUED, STOPPED -> PLAY;
            case RECORD_QUEUED, PLAY_QUEUED -> STOP;
            case PLAYING -> switch (playingTapAction)
            {
                case STOP -> STOP;
                case MUTE -> TOGGLE_MUTE;
                case OVERDUB -> OVERDUB;
                case NOTHING -> NONE;
            };
        };
    }
}
