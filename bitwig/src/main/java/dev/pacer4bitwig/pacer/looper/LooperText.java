// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

/**
 * Texts the looper shows in notifications and on the Pacer's five-character display.
 */
public final class LooperText
{
    private LooperText ()
    {
        // Utility
    }


    /**
     * The name for a row from the "names for new rows" setting.
     *
     * @param names Comma separated names, may be null or empty
     * @param rowIndex The row, 0-based
     * @return The name, empty if the list has none for this row
     */
    public static String rowName (final String names, final int rowIndex)
    {
        if (names == null || names.isBlank () || rowIndex < 0)
            return "";
        final String [] parts = names.split (",");
        return rowIndex < parts.length ? parts[rowIndex].trim () : "";
    }


    /**
     * The row for the Pacer's five-character display, when the scene has no name: "ROW 3", "ROW12", "R128".
     *
     * @param rowIndex The row, 0-based
     * @return The text, at most five characters
     */
    public static String displayRow (final int rowIndex)
    {
        final int row = rowIndex + 1;
        if (row < 10)
            return "ROW " + row;
        return row < 100 ? "ROW" + row : "R" + row;
    }


    /**
     * An event about one loop for the display: "REC 3", "MUTE2", "CLR 1".
     *
     * @param prefix Four characters
     * @param trackIndex The loop track, 0-based
     * @return The text, five characters for loops 1-9
     */
    public static String loopWord (final String prefix, final int trackIndex)
    {
        return prefix + (trackIndex + 1);
    }


    /**
     * A loop's length for the display: "4 BAR", "12BAR", "128B".
     *
     * @param bars The length in bars, at least 1
     * @return The text, at most five characters
     */
    public static String barsWord (final int bars)
    {
        if (bars < 10)
            return bars + " BAR";
        return bars < 100 ? bars + "BAR" : bars + "B";
    }


    /**
     * "Row 3" or "Row 3: Chorus".
     *
     * @param rowIndex The row, 0-based
     * @param sceneName The scene name, may be null or empty
     * @return The label
     */
    public static String rowLabel (final int rowIndex, final String sceneName)
    {
        final String label = "Row " + (rowIndex + 1);
        return sceneName == null || sceneName.isBlank () ? label : label + ": " + sceneName.trim ();
    }


    /**
     * A one-line overview of the row, e.g. {@code Row 2: Chorus | 1 ▶  2 ●  3 ■ (muted)  4 –}.
     *
     * @param rowIndex The row, 0-based
     * @param sceneName The scene name, may be null
     * @param states The state of each loop track, null for missing tracks
     * @param muted The mute state of each loop track
     * @return The text
     */
    public static String status (final int rowIndex, final String sceneName, final LoopState [] states, final boolean [] muted)
    {
        final StringBuilder sb = new StringBuilder (rowLabel (rowIndex, sceneName)).append (" |");
        for (int i = 0; i < states.length; i++)
        {
            if (states[i] == null)
                continue;
            sb.append ("  ").append (i + 1).append (' ').append (symbol (states[i]));
            if (i < muted.length && muted[i] && states[i] != LoopState.EMPTY)
                sb.append (" (muted)");
        }
        return sb.toString ();
    }


    /**
     * @param state A loop state
     * @return Its symbol
     */
    public static String symbol (final LoopState state)
    {
        return switch (state)
        {
            case EMPTY -> "–";
            case STOPPED -> "■";
            case PLAYING -> "▶";
            case PLAY_QUEUED -> "▷";
            case STOP_QUEUED -> "□";
            case RECORD_QUEUED -> "○";
            case RECORDING -> "●";
        };
    }
}
