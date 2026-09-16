// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * The message vectors here are the exact bytes the real Pacer accepted during the hardware session on 2026-09-16.
 */
class PacerSysexTest
{
    /** The name write the display showed as "MODE1". */
    private static final String REAL_NAME_MODE1 = "F0 00 01 77 7F 01 01 00 01 01 05 4D 4F 44 45 31 21 F7";
    /** The colour write that recoloured SW 1 magenta. */
    private static final String REAL_COLOUR_SW1 = "F0 00 01 77 7F 01 01 00 0D 41 01 01 00 42 01 02 69 F7";
    /** The two-object attempt, kept as a second independent checksum vector. */
    private static final String REAL_TWO_OBJECT = "F0 00 01 77 7F 01 01 00 0E 41 01 0F 00 42 01 10 00 0F 41 01 09 00 42 01 0A 25 F7";


    private static int [] bytes (final String hex)
    {
        final String [] parts = hex.split (" ");
        final int [] values = new int [parts.length];
        for (int i = 0; i < parts.length; i++)
            values[i] = Integer.parseInt (parts[i], 16);
        return values;
    }


    /** Strip the 5 header bytes and the trailing checksum + F7, leaving the body the checksum covers. */
    private static int [] body (final String hex)
    {
        final int [] all = bytes (hex);
        final int [] result = new int [all.length - 7];
        System.arraycopy (all, 5, result, 0, result.length);
        return result;
    }


    private static void assertWellFormed (final String hex)
    {
        final int [] all = bytes (hex);
        assertEquals (0xF0, all[0], "starts with F0");
        assertEquals (0xF7, all[all.length - 1], "ends with F7");
        assertEquals (PacerSysex.checksum (body (hex)), all[all.length - 2], "checksum of " + hex);
        for (int i = 1; i < all.length - 1; i++)
            assertTrue (all[i] <= 0x7F, "data bytes are 7-bit in " + hex);
    }


    @Test
    void nameMatchesTheMessageTheDeviceAccepted ()
    {
        assertEquals (REAL_NAME_MODE1, PacerSysex.name ("MODE1"));
    }


    @Test
    void checksumMatchesRealDeviceMessages ()
    {
        assertEquals (0x69, PacerSysex.checksum (body (REAL_COLOUR_SW1)));
        assertEquals (0x25, PacerSysex.checksum (body (REAL_TWO_OBJECT)));
    }


    @Test
    void namesArePaddedAndCutToFiveCharacters ()
    {
        assertEquals ("FX   ", PacerSysex.pad ("FX"));
        assertEquals ("LOOPS", PacerSysex.pad ("LOOPS"));
        assertEquals ("TOOLO", PacerSysex.pad ("TOOLONG"));
        assertEquals ("     ", PacerSysex.pad (""));
        assertEquals ("     ", PacerSysex.pad (null));
    }


    @Test
    void shortNameIsSentAsFiveCharactersSoNoOldCharactersRemain ()
    {
        final String hex = PacerSysex.name ("FX");
        final int [] all = bytes (hex);
        assertEquals (PacerSysex.NAME_LENGTH, all[10], "length byte");
        assertEquals ('F', all[11]);
        assertEquals ('X', all[12]);
        assertEquals (' ', all[13]);
        assertEquals (' ', all[14]);
        assertEquals (' ', all[15]);
        assertWellFormed (hex);
    }


    @Test
    void nameHasNoTrailingTerminator ()
    {
        // A trailing 0x00 after the characters corrupts the name on the device
        final int [] all = bytes (PacerSysex.name ("MODE1"));
        assertEquals ('1', all[all.length - 3], "last character sits right before the checksum");
    }


    @Test
    void ledPutsTheSwitchUnderHostControl ()
    {
        final String hex = PacerSysex.led (0, PacerColour.GREEN, PacerColour.BLUE, LedRow.STRIP);
        assertWellFormed (hex);
        final int [] all = bytes (hex);
        assertEquals (0x0D, all[8], "SW 1 object");
        assertEquals (0x40, all[9], "LED control element");
        assertEquals (1, all[11], "MIDI controlled");
        assertEquals (0x41, all[13]);
        assertEquals (PacerColour.GREEN.getFull (), all[15], "on colour");
        assertEquals (0x42, all[17]);
        assertEquals (PacerColour.BLUE.getFull (), all[19], "off colour");
        assertEquals (0x43, all[21]);
        assertEquals (LedRow.STRIP.getNumber (), all[23], "LED number");
    }


    @Test
    void ledCanUseDimmedVariantsAndAnyRow ()
    {
        final String hex = PacerSysex.led (9, PacerColour.RED, false, PacerColour.RED, true, LedRow.WORD);
        assertWellFormed (hex);
        final int [] all = bytes (hex);
        assertEquals (0x17, all[8], "SW D object");
        assertEquals (PacerColour.RED.getFull (), all[15]);
        assertEquals (PacerColour.RED.getDim (), all[19]);
        assertEquals (3, all[23], "word row");
        assertEquals (PacerColour.RED.getFull () + 1, PacerColour.RED.getDim (), "dim is the next byte value");
    }


    @Test
    void everyMessageIsWellFormedForEverySwitchAndColour ()
    {
        for (int index = 0; index < 10; index++)
            for (final PacerColour colour: PacerColour.values ())
                for (final LedRow row: LedRow.values ())
                    assertWellFormed (PacerSysex.led (index, colour, PacerColour.OFF, row));
    }


    @Test
    void switchObjectsSkipTheGapBetweenTheRows ()
    {
        assertEquals (0x0D, PacerSysex.switchObject (0), "SW 1");
        assertEquals (0x12, PacerSysex.switchObject (5), "SW 6");
        assertEquals (0x14, PacerSysex.switchObject (6), "SW A - 0x13 is not a switch");
        assertEquals (0x17, PacerSysex.switchObject (9), "SW D");
        assertThrows (IllegalArgumentException.class, () -> PacerSysex.switchObject (10));
        assertThrows (IllegalArgumentException.class, () -> PacerSysex.switchObject (-1));
    }


    @Test
    void everyMessageTargetsTheRamCopyAndNeverAStoredSlot ()
    {
        assertEquals (0x00, bytes (PacerSysex.name ("X"))[7], "preset index of a name write");
        for (int index = 0; index < 10; index++)
            assertEquals (0x00, bytes (PacerSysex.led (index, PacerColour.WHITE, PacerColour.OFF, LedRow.STRIP))[7], "preset index of an LED write");
    }


    @Test
    void offIsDarkInBothVariants ()
    {
        assertEquals (0, PacerColour.OFF.getFull ());
        assertEquals (0, PacerColour.OFF.getDim ());
    }


    @Test
    void nearestMapsTrackColoursOntoThePalette ()
    {
        assertEquals (PacerColour.RED, PacerColour.nearest (1, 0, 0));
        assertEquals (PacerColour.GREEN, PacerColour.nearest (0, 1, 0));
        assertEquals (PacerColour.BLUE, PacerColour.nearest (0, 0, 1));
        assertEquals (PacerColour.CYAN, PacerColour.nearest (0, 1, 1));
        assertEquals (PacerColour.WHITE, PacerColour.nearest (0.5, 0.5, 0.5), "greys");
        assertEquals (PacerColour.WHITE, PacerColour.nearest (0, 0, 0), "black counts as grey, never OFF");

        final double [] [] samples =
        {
            {
                1,
                0,
                0
            },
            {
                0,
                1,
                0
            },
            {
                0.2,
                0.7,
                0.9
            }
        };
        for (final double [] rgb: samples)
        {
            final PacerColour colour = PacerColour.nearest (rgb[0], rgb[1], rgb[2]);
            assertNotEquals (PacerColour.OFF, colour, "nearest never returns OFF");
            assertNotEquals (PacerColour.DARK_GREEN, colour, "dark green is reserved for dim states");
        }
    }
}
