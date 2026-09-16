// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * Actions that can be assigned to switches (on both presets), the footswitch jacks and hold gestures. To add one: add
 * the constant here, then handle it in {@code LooperController.perform} and {@code LooperController.actionLed} - or,
 * for FX actions, in {@code FxController.perform} and {@code FxController.actionLed}. Settings store the label, so
 * renaming a label resets that choice for existing users.
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
    /** Start the loop tracks at the track selected in Bitwig. */
    TRACKS_HERE ("Loop tracks start at the selected track", false, false),
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
    /** Pop up a one-line overview of the row and every loop. */
    SHOW_STATUS ("Show looper status", false, false),
    /** Cycle every switch LED through all colours. */
    LED_TEST ("Test the LEDs", false, false),

    /** Hold only: runs the switch's tap action again on release, which turns a toggle into a momentary switch. */
    MOMENTARY ("Momentary: tap again on release (hold)", false, false),

    /** FX switch 1 of the focused instrument. */
    FX_1 ("FX 1 on/off (focused instrument)", true, false, true),
    /** FX switch 2 of the focused instrument. */
    FX_2 ("FX 2 on/off (focused instrument)", true, false, true),
    /** FX switch 3 of the focused instrument. */
    FX_3 ("FX 3 on/off (focused instrument)", true, false, true),
    /** FX switch 4 of the focused instrument. */
    FX_4 ("FX 4 on/off (focused instrument)", true, false, true),
    /** FX switch 5 of the focused instrument. */
    FX_5 ("FX 5 on/off (focused instrument)", true, false, true),
    /** FX switch 6 of the focused instrument. */
    FX_6 ("FX 6 on/off (focused instrument)", true, false, true),
    /** Focus instrument A. */
    FOCUS_A ("Focus instrument A", true, false, true),
    /** Focus instrument B. */
    FOCUS_B ("Focus instrument B", true, false, true),
    /** Focus instrument C. */
    FOCUS_C ("Focus instrument C", true, false, true),
    /** Focus instrument D. */
    FOCUS_D ("Focus instrument D", true, false, true),
    /** Focus the next assigned instrument. */
    FOCUS_NEXT ("Focus the next instrument", true, false, true),
    /** Focus the previous assigned instrument. */
    FOCUS_PREVIOUS ("Focus the previous instrument", true, false, true),
    /** Mute/unmute the track of instrument A. */
    MUTE_A ("Mute/unmute instrument A", false, false, true),
    /** Mute/unmute the track of instrument B. */
    MUTE_B ("Mute/unmute instrument B", false, false, true),
    /** Mute/unmute the track of instrument C. */
    MUTE_C ("Mute/unmute instrument C", false, false, true),
    /** Mute/unmute the track of instrument D. */
    MUTE_D ("Mute/unmute instrument D", false, false, true),
    /** Mute/unmute the track of the focused instrument. */
    MUTE_FOCUSED ("Mute/unmute the focused instrument", false, false, true),
    /** Make the track selected in Bitwig instrument A. */
    ASSIGN_A ("Assign the track selected in Bitwig to instrument A", false, true, true),
    /** Make the track selected in Bitwig instrument B. */
    ASSIGN_B ("Assign the track selected in Bitwig to instrument B", false, true, true),
    /** Make the track selected in Bitwig instrument C. */
    ASSIGN_C ("Assign the track selected in Bitwig to instrument C", false, true, true),
    /** Make the track selected in Bitwig instrument D. */
    ASSIGN_D ("Assign the track selected in Bitwig to instrument D", false, true, true),
    /** Move to the next snapshot of the focused instrument and recall it. */
    SNAPSHOT_NEXT ("Next snapshot of the focused instrument", false, false, true),
    /** Recall snapshot 1 of the focused instrument. */
    SNAPSHOT_FIRST ("Back to snapshot 1 of the focused instrument", false, false, true),
    /** Store the FX switches of the focused instrument in its current snapshot. */
    SNAPSHOT_STORE ("Store the current snapshot of the focused instrument", false, true, true),

    /** Move to the next mode that has a menu slot. */
    MODE_NEXT ("Next mode", true, false, false, true),
    /** Go back to the mode before this one, the same as tapping SW 6. */
    MODE_TOGGLE ("Previous mode (toggle)", true, false, false, true),
    /** Go to the looper mode. */
    MODE_LOOP ("Go to the Looper mode", true, false, false, true),
    /** Go to the FX mode. */
    MODE_FX ("Go to the FX mode", true, false, false, true),
    /** Go to the mixer mode. */
    MODE_MIX ("Go to the Mixer mode", true, false, false, true),
    /** Go to the song mode. */
    MODE_SONG ("Go to the Song mode", true, false, false, true);


    private final String  label;
    private final boolean timingCritical;
    private final boolean destructive;
    private final boolean fx;
    private final boolean mode;


    Action (final String label, final boolean timingCritical, final boolean destructive)
    {
        this (label, timingCritical, destructive, false);
    }


    Action (final String label, final boolean timingCritical, final boolean destructive, final boolean fx)
    {
        this (label, timingCritical, destructive, fx, false);
    }


    Action (final String label, final boolean timingCritical, final boolean destructive, final boolean fx, final boolean mode)
    {
        this.label = label;
        this.timingCritical = timingCritical;
        this.destructive = destructive;
        this.fx = fx;
        this.mode = mode;
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
     * Destructive actions delete loops or overwrite assignments; as hold actions they honour the "hold time for
     * clearing" setting.
     *
     * @return True if destructive
     */
    public boolean isDestructive ()
    {
        return this.destructive;
    }


    /**
     * FX actions are run by the FX preset's controller, whichever preset is active.
     *
     * @return True for FX actions
     */
    public boolean isFx ()
    {
        return this.fx;
    }


    /**
     * Mode actions change the whole board, so they are run by the controller rather than the looper or the FX
     * preset. Assigning one to a footswitch jack gives a second way in, next to SW 6.
     *
     * @return True for mode actions
     */
    public boolean isMode ()
    {
        return this.mode;
    }
}
