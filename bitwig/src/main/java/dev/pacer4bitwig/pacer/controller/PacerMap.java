// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.controller;

/**
 * The MIDI contract with the Pacer preset. Mirrors docs/PACER-MAP.md - change both together.
 */
public final class PacerMap
{
    /** MIDI channel 16 (0-based), the default of the "Looper MIDI channel" setting. */
    public static final int      DEFAULT_MIDI_CHANNEL     = 15;

    /** The ten stomp switches: SW 1-6 (indices 0-5), SW A-D (indices 6-9). */
    public static final int      NUM_SWITCHES             = 10;
    /** Index of SW A, the first switch of the top row. */
    public static final int      FIRST_TOP_ROW_SWITCH     = 6;
    /** The four footswitch jacks. */
    public static final int      NUM_FOOTSWITCHES         = 4;
    /** The most loop tracks any layout uses. */
    public static final int      MAX_LOOP_TRACKS          = 6;

    /** Switch names in index order. */
    public static final String [] SWITCH_NAMES            =
    {
        "SW 1",
        "SW 2",
        "SW 3",
        "SW 4",
        "SW 5",
        "SW 6",
        "SW A",
        "SW B",
        "SW C",
        "SW D"
    };

    /** CC of SW 1 (step 1, CC trigger 127/0); the other switches follow in index order. */
    public static final int      SWITCH_CC_BASE           = 102;
    /** CC of footswitch jack FS 1; FS 2-4 follow. */
    public static final int      FOOTSWITCH_CC_BASE       = 112;
    /** Expression pedal 1. */
    public static final int      EXP1_CC                  = 116;
    /** Expression pedal 2. */
    public static final int      EXP2_CC                  = 117;
    /** Sent by the preset's "on load" MIDI setting; the extension re-sends all LEDs. */
    public static final int      PRESET_LOADED_CC         = 119;


    private PacerMap ()
    {
        // Constants only
    }


    /**
     * Get the action CC of a switch.
     *
     * @param switchIndex 0-9
     * @return The CC
     */
    public static int switchCC (final int switchIndex)
    {
        return SWITCH_CC_BASE + switchIndex;
    }

}
