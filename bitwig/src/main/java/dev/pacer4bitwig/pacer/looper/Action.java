// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * Actions that can be assigned to the non-loop switches, the footswitch jacks and hold gestures. To add one: add
 * the constant here, then handle it in {@code LooperController.perform} and {@code LooperController.actionLed}.
 */
public enum Action implements Labelled
{
    /** Nothing. */
    NONE ("Nothing", false),

    /** Close the recording loop, or record on the first empty loop track. */
    RECORD_NEXT_LAYER ("One-button looper: record the next layer", true),
    /** Stop and delete the most recently recorded loop of the row. */
    CLEAR_LAST_LOOP ("Clear the last recorded loop", false),
    /** Smart loop on the selected track. */
    LOOP_SELECTED ("Smart loop: selected track", true),
    /** Stop the selected track. */
    STOP_SELECTED ("Stop selected loop", true),
    /** Mute/unmute the selected track. */
    MUTE_SELECTED ("Mute/unmute selected loop", true),
    /** Stop and delete the selected track's loop. */
    CLEAR_SELECTED ("Clear selected loop", false),
    /** Select the previous loop track. */
    SELECT_PREVIOUS_LOOP ("Select previous loop track", false),
    /** Select the next loop track. */
    SELECT_NEXT_LOOP ("Select next loop track", false),

    /** Stop all loops if any plays, otherwise play the row. */
    PLAY_STOP_ALL ("Play row / stop all loops", true),
    /** Stop all loops. */
    STOP_ALL ("Stop all loops", true),
    /** Launch the scene of the current row. */
    PLAY_ROW ("Play row", true),
    /** Stop and delete every loop of the row. */
    CLEAR_ROW ("Clear all loops in the row", false),
    /** Previous scene row. */
    ROW_PREVIOUS ("Previous row", false),
    /** Next scene row, adding a scene past the last one. */
    ROW_NEXT ("Next row (adds one at the end)", false),
    /** Scroll the loop track window left. */
    TRACKS_LEFT ("Move loop tracks left", false),
    /** Scroll the loop track window right. */
    TRACKS_RIGHT ("Move loop tracks right", false),

    /** Undo. */
    UNDO ("Undo", false),
    /** Redo. */
    REDO ("Redo", false),
    /** Toggle the launcher overdub. */
    LAUNCHER_OVERDUB ("Launcher overdub on/off", false),
    /** Toggle the metronome. */
    METRONOME ("Metronome on/off", false),
    /** Tap tempo. */
    TAP_TEMPO ("Tap tempo", true),
    /** Start or stop the transport. */
    TRANSPORT_PLAY_STOP ("Transport play/stop", true),
    /** Cycle every switch LED through all colours. */
    LED_TEST ("Test the LEDs", false);


    private final String  label;
    private final boolean timingCritical;


    Action (final String label, final boolean timingCritical)
    {
        this.label = label;
        this.timingCritical = timingCritical;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * Timing-critical actions fire on press even if the switch also has a hold action.
     *
     * @return True if timing-critical
     */
    public boolean isTimingCritical ()
    {
        return this.timingCritical;
    }
}
