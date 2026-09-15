// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * Actions that can be assigned to the non-loop switches, the footswitch jacks and hold gestures. To add one: add
 * the constant here, then handle it in {@code LooperController.perform} and {@code LooperController.actionLed}.
 * Settings store the label, so renaming a label resets that choice for existing users.
 */
public enum Action implements Labelled
{
    /** Nothing. */
    NONE ("Nothing", false, false),

    /** Close the recording loop, or record on the first empty loop track. */
    RECORD_NEXT_LAYER ("One-button looper: record the next layer", true, false),
    /** Stop and delete the most recently recorded loop of the row. */
    CLEAR_LAST_LOOP ("Clear the last recorded loop", false, true),

    /** Smart loop on the selected track. */
    LOOP_SELECTED ("Smart loop: selected track", true, false),
    /** Stop the selected track. */
    STOP_SELECTED ("Stop selected loop", true, false),
    /** Mute/unmute the selected track. */
    MUTE_SELECTED ("Mute/unmute selected loop", true, false),
    /** Solo/unsolo the selected track. */
    SOLO_SELECTED ("Solo/unsolo selected loop", true, false),
    /** Input monitoring on the selected track. */
    MONITOR_SELECTED ("Input monitoring on/off (selected track)", false, false),
    /** Stop and delete the selected track's loop. */
    CLEAR_SELECTED ("Clear selected loop", false, true),
    /** Duplicate the content of the selected loop, doubling its length. */
    DOUBLE_SELECTED ("Double selected loop (duplicate its content)", false, false),
    /** Halve the loop length of the selected loop. */
    HALVE_SELECTED ("Halve selected loop", false, false),
    /** Select the previous loop track. */
    SELECT_PREVIOUS_LOOP ("Select previous loop track", false, false),
    /** Select the next loop track. */
    SELECT_NEXT_LOOP ("Select next loop track", false, false),

    /** Stop all loops if any plays, otherwise play the row. */
    PLAY_STOP_ALL ("Play row / stop all loops", true, false),
    /** Stop all loops. */
    STOP_ALL ("Stop all loops", true, false),
    /** Launch the scene of the current row. */
    PLAY_ROW ("Play row", true, false),
    /** Stop and delete every loop of the row. */
    CLEAR_ROW ("Clear all loops in the row", false, true),
    /** Mute every loop track, or unmute them all if all are muted. */
    MUTE_ALL_TOGGLE ("Mute/unmute all loops", true, false),
    /** Fade all loops out, stop them, restore their volumes. */
    FADE_OUT ("Fade out and stop all loops", false, false),
    /** Play the row, fading the loops in from silence. */
    FADE_IN ("Fade in the row", false, false),
    /** Stop everything and put the loop tracks back to a clean state. */
    RESET ("Reset the looper: stop, unmute, unsolo, disarm", true, false),

    /** Previous scene row. */
    ROW_PREVIOUS ("Previous row", false, false),
    /** Next scene row, adding a scene past the last one. */
    ROW_NEXT ("Next row (adds one at the end)", false, false),
    /** Copy the row with all its loops and move to the copy. */
    DUPLICATE_ROW ("Duplicate row and move to the copy", false, false),
    /** Scroll the loop track window left. */
    TRACKS_LEFT ("Move loop tracks left", false, false),
    /** Scroll the loop track window right. */
    TRACKS_RIGHT ("Move loop tracks right", false, false),

    /** Undo. */
    UNDO ("Undo", false, false),
    /** Redo. */
    REDO ("Redo", false, false),
    /** Toggle the launcher overdub. */
    LAUNCHER_OVERDUB ("Launcher overdub on/off", false, false),
    /** Toggle the metronome. */
    METRONOME ("Metronome on/off", false, false),
    /** Tap tempo. */
    TAP_TEMPO ("Tap tempo", true, false),
    /** Start or stop the transport. */
    TRANSPORT_PLAY_STOP ("Transport play/stop", true, false),
    /** Cycle every switch LED through all colours. */
    LED_TEST ("Test the LEDs", false, false);


    private final String  label;
    private final boolean timingCritical;
    private final boolean destructive;


    Action (final String label, final boolean timingCritical, final boolean destructive)
    {
        this.label = label;
        this.timingCritical = timingCritical;
        this.destructive = destructive;
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


    /**
     * Destructive actions delete loops; as hold actions they honour the "hold time for clearing" setting.
     *
     * @return True if destructive
     */
    public boolean isDestructive ()
    {
        return this.destructive;
    }
}
