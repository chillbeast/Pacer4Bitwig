// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import de.mossgrabers.framework.daw.data.ISlot;


/**
 * The state of the clip launcher slot a loop switch controls.
 */
public enum LoopState
{
    /** No clip. */
    EMPTY,
    /** A clip which is not playing. */
    STOPPED,
    /** Playing. */
    PLAYING,
    /** Waiting for the launch quantization to start playing. */
    PLAY_QUEUED,
    /** Playing, waiting for the launch quantization to stop. */
    STOP_QUEUED,
    /** Waiting for the launch quantization to start recording. */
    RECORD_QUEUED,
    /** Recording. */
    RECORDING;


    /**
     * Get the state of a slot.
     *
     * @param slot The slot
     * @return The state
     */
    public static LoopState of (final ISlot slot)
    {
        return of (slot.doesExist (), slot.hasContent (), slot.isPlaying (), slot.isPlayingQueued (), slot.isStopQueued (), slot.isRecording (), slot.isRecordingQueued ());
    }


    /**
     * Derive the state from the slot flags Bitwig reports. Recording wins over playback flags, queued
     * transitions win over the steady state they leave.
     *
     * @param exists The slot exists
     * @param hasContent The slot holds a clip
     * @param isPlaying The clip plays
     * @param isPlayingQueued Playback is queued
     * @param isStopQueued Stop is queued
     * @param isRecording The slot records
     * @param isRecordingQueued Recording is queued
     * @return The state
     */
    public static LoopState of (final boolean exists, final boolean hasContent, final boolean isPlaying, final boolean isPlayingQueued, final boolean isStopQueued, final boolean isRecording, final boolean isRecordingQueued)
    {
        if (!exists)
            return EMPTY;
        if (isRecordingQueued)
            return RECORD_QUEUED;
        if (isRecording)
            return RECORDING;
        if (isStopQueued)
            return STOP_QUEUED;
        if (isPlayingQueued)
            return PLAY_QUEUED;
        if (isPlaying)
            return PLAYING;
        return hasContent ? STOPPED : EMPTY;
    }
}
