// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import java.util.Locale;


/**
 * The live-edit SysEx messages (docs/PACER-MAP.md, "Live edits"). Every message is a SET to preset index 0
 * ("current"), which changes the <i>loaded</i> preset in RAM only: selecting any preset restores the stored one, so
 * this never wears the EEPROM. Messages are hex strings for {@code IMidiOutput.sendSysex}.
 * <p>
 * Two rules from the hardware are baked in here. Only <b>one object per message</b> is possible - bytes after the
 * first object's elements are parsed as more elements of that same object - so a switch always gets its own message.
 * And the name carries <b>no trailing element terminator</b>; appending one corrupts it.
 */
public final class PacerSysex
{
    /** Preset names are always five characters, space padded, like every name the device stores. */
    public static final int     NAME_LENGTH     = 5;

    /** SysEx objects of the ten stomp switches: SW 1-6, then SW A-D. */
    private static final int [] SWITCH_OBJECTS  =
    {
        0x0D,
        0x0E,
        0x0F,
        0x10,
        0x11,
        0x12,
        0x14,
        0x15,
        0x16,
        0x17
    };

    private static final int    CMD_SET         = 0x01;
    private static final int    CMD_GET         = 0x02;
    private static final int    TGT_PRESET      = 0x01;
    /** Preset index 0 is the loaded preset's RAM copy. Never write to a stored slot from here. */
    private static final int    IDX_CURRENT     = 0x00;
    private static final int    OBJ_NAME        = 0x01;
    private static final int    ELM_NAME        = 0x01;

    /** Step 1's LED block: MIDI control, on colour, off colour, LED number. */
    private static final int    ELM_LED_CONTROL = 0x40;
    private static final int    ELM_ON_COLOUR   = 0x41;
    private static final int    ELM_OFF_COLOUR  = 0x42;
    private static final int    ELM_LED_NUMBER  = 0x43;

    /** Hands the LED to the host: on and off colours are then chosen by the CC the step listens to. */
    private static final int    MIDI_CONTROLLED = 1;
    private static final int    CHECKSUM_BASE   = 128;


    private PacerSysex ()
    {
        // Utility
    }


    /**
     * Write the preset name, which the Pacer's display shows immediately. A switch press replaces it with that
     * switch's CC readout, so it has to be written again to stay up.
     *
     * @param text The name; padded with spaces or cut to {@link #NAME_LENGTH}
     * @return The message as a hex string
     */
    public static String name (final String text)
    {
        final String padded = pad (text);
        final int [] body = new int [6 + NAME_LENGTH];
        body[0] = CMD_SET;
        body[1] = TGT_PRESET;
        body[2] = IDX_CURRENT;
        body[3] = OBJ_NAME;
        body[4] = ELM_NAME;
        body[5] = NAME_LENGTH;
        for (int i = 0; i < NAME_LENGTH; i++)
            body[6 + i] = padded.charAt (i) & 0x7F;
        return message (body);
    }


    /**
     * Ask for the loaded preset's name (a GET of preset index 0 reads RAM, so it shows live edits). Read-only.
     *
     * @return The message as a hex string
     */
    public static String requestName ()
    {
        return message (new int []
        {
            CMD_GET,
            TGT_PRESET,
            IDX_CURRENT,
            OBJ_NAME
        });
    }


    /**
     * Read the loaded preset's name out of a message from the Pacer: the answer to {@link #requestName()}, or the name
     * message of a dump of preset index 0. Answers come in the SET layout, like a dump.
     *
     * @param hex The message, as hex with or without spaces
     * @return The name, null if the message is anything else
     */
    public static String parseName (final String hex)
    {
        if (hex == null)
            return null;
        final String digits = hex.replace (" ", "");
        if (digits.length () % 2 != 0)
            return null;
        final int [] bytes = new int [digits.length () / 2];
        try
        {
            for (int i = 0; i < bytes.length; i++)
                bytes[i] = Integer.parseInt (digits.substring (2 * i, 2 * i + 2), 16);
        }
        catch (final NumberFormatException ex)
        {
            return null;
        }
        // F0 00 01 77 7F | 01 01 00 01 | 01 len chars.. | checksum F7
        final int [] header =
        {
            0xF0,
            0x00,
            0x01,
            0x77,
            0x7F,
            CMD_SET,
            TGT_PRESET,
            IDX_CURRENT,
            OBJ_NAME
        };
        if (bytes.length < header.length + 4 || bytes[bytes.length - 1] != 0xF7)
            return null;
        for (int i = 0; i < header.length; i++)
            if (bytes[i] != header[i])
                return null;
        final int length = bytes[10];
        if (11 + length > bytes.length - 2)
            return null;
        final StringBuilder name = new StringBuilder ();
        for (int i = 0; i < length; i++)
            name.append ((char) bytes[11 + i]);
        return name.toString ();
    }


    /**
     * Pad or cut a name to what the Pacer stores.
     *
     * @param text The name
     * @return Exactly {@link #NAME_LENGTH} characters
     */
    public static String pad (final String text)
    {
        final String value = text == null ? "" : text;
        if (value.length () >= NAME_LENGTH)
            return value.substring (0, NAME_LENGTH);
        return value + " ".repeat (NAME_LENGTH - value.length ());
    }


    /**
     * Put a switch's LED under host control and set the colour pair it shows. The CC echo then picks the on colour
     * for 127 and the off colour for 0.
     *
     * @param switchIndex 0-9
     * @param on The colour for 127
     * @param off The colour for 0
     * @param row Which of the switch's LEDs lights
     * @return The message as a hex string
     */
    public static String led (final int switchIndex, final PacerColour on, final PacerColour off, final LedRow row)
    {
        return led (switchIndex, on.getFull (), off.getFull (), row);
    }


    /**
     * Put a switch's LED under host control and set the colour pair it shows, choosing the dimmed variants.
     *
     * @param switchIndex 0-9
     * @param on The colour for 127
     * @param onDimmed True to use the dimmed variant of the on colour
     * @param off The colour for 0
     * @param offDimmed True to use the dimmed variant of the off colour
     * @param row Which of the switch's LEDs lights
     * @return The message as a hex string
     */
    public static String led (final int switchIndex, final PacerColour on, final boolean onDimmed, final PacerColour off, final boolean offDimmed, final LedRow row)
    {
        return led (switchIndex, on.getValue (onDimmed), off.getValue (offDimmed), row);
    }


    private static String led (final int switchIndex, final int on, final int off, final LedRow row)
    {
        return message (new int []
        {
            CMD_SET,
            TGT_PRESET,
            IDX_CURRENT,
            switchObject (switchIndex),
            ELM_LED_CONTROL,
            1,
            MIDI_CONTROLLED,
            0x00,
            ELM_ON_COLOUR,
            1,
            on & 0x7F,
            0x00,
            ELM_OFF_COLOUR,
            1,
            off & 0x7F,
            0x00,
            ELM_LED_NUMBER,
            1,
            row.getNumber ()
        });
    }


    /**
     * Get the SysEx object of a stomp switch.
     *
     * @param switchIndex 0-9
     * @return The object byte
     */
    public static int switchObject (final int switchIndex)
    {
        if (switchIndex < 0 || switchIndex >= SWITCH_OBJECTS.length)
            throw new IllegalArgumentException ("There are 10 switches, got index " + switchIndex);
        return SWITCH_OBJECTS[switchIndex];
    }


    /**
     * The Pacer's checksum: the complement of the sum of the body, modulo 128.
     *
     * @param body The message body, from the command byte to the last data byte
     * @return The checksum byte
     */
    public static int checksum (final int [] body)
    {
        int sum = 0;
        for (final int value: body)
            sum += value;
        return (CHECKSUM_BASE - sum % CHECKSUM_BASE) % CHECKSUM_BASE;
    }


    private static String message (final int [] body)
    {
        final StringBuilder sb = new StringBuilder ("F0 00 01 77 7F");
        for (final int value: body)
            append (sb, value);
        append (sb, checksum (body));
        sb.append (" F7");
        return sb.toString ();
    }


    private static void append (final StringBuilder sb, final int value)
    {
        sb.append (' ').append (String.format (Locale.ROOT, "%02X", Integer.valueOf (value)));
    }
}
