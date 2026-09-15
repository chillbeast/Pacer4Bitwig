// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.daw;

import java.util.Locale;


/**
 * The SysEx side of the Pacer's DAW mode, reproducing exactly what Nektar's Bitwig script sends (see
 * docs/ROADMAP.md). None of these messages touch stored presets.
 */
public final class DawModeSysex
{
    /** Tells the Pacer a DAW is connected (target 0x09). */
    public static final String ENABLE         = "F0 00 01 77 7F 01 09 01 00 00 01 3E 36 F7";
    /** Tells the Pacer the DAW is gone. */
    public static final String DISABLE        = "F0 00 01 77 7F 01 09 00 00 00 01 00 75 F7";

    /** Number of switch slots in a DAW preset. */
    public static final int    NUM_SLOTS      = 10;

    private static final int   CMD_SET        = 0x01;
    private static final int   TGT_DISPLAY    = 0x06;
    private static final int   IDX_DAW_FUNC   = 0x18;
    private static final int   TGT_FUNCTIONS  = 0x10;


    private DawModeSysex ()
    {
        // Utility
    }


    /**
     * Is this the report the Pacer sends when a DAW preset becomes active?
     *
     * @param hex The SysEx message as hex, as delivered by Bitwig (case and spaces do not matter)
     * @return True for a DAW function report
     */
    public static boolean isFunctionReport (final String hex)
    {
        final String data = normalize (hex);
        return data.startsWith ("f0000177") && data.length () >= 14 && Integer.parseInt (data.substring (12, 14), 16) == TGT_FUNCTIONS;
    }


    /**
     * The LED colours of the 10 DAW slots.
     * <p>
     * Nektar's script ends this message with the sum of the command, target and index bytes (0x1F) instead of the
     * usual complement checksum over the whole payload. It is reproduced unchanged because that is what the Pacer is
     * known to accept.
     *
     * @param offColours Colour index per slot while the function is off
     * @param onColours Colour index per slot while the function is on
     * @return The message as a hex string
     */
    public static String slotColours (final int [] offColours, final int [] onColours)
    {
        final StringBuilder sb = new StringBuilder ("F0 00 01 77 7F");
        append (sb, CMD_SET);
        append (sb, TGT_DISPLAY);
        append (sb, IDX_DAW_FUNC);
        for (int slot = 1; slot <= NUM_SLOTS; slot++)
        {
            append (sb, 0x00);
            append (sb, slot);
            append (sb, 0x02);
            append (sb, offColours[slot - 1] & 0x7F);
            append (sb, onColours[slot - 1] & 0x7F);
        }
        append (sb, (CMD_SET + TGT_DISPLAY + IDX_DAW_FUNC) % 128);
        sb.append (" F7");
        return sb.toString ();
    }


    private static void append (final StringBuilder sb, final int value)
    {
        sb.append (' ').append (String.format (Locale.ROOT, "%02X", Integer.valueOf (value)));
    }


    private static String normalize (final String hex)
    {
        return hex == null ? "" : hex.replace (" ", "").toLowerCase (Locale.ROOT);
    }
}
