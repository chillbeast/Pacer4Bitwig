// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


class ExpressionTargetTest
{
    @Test
    void controlChanges ()
    {
        assertArrayEquals (new int [] {0xB0, 1, 64}, ExpressionTarget.MIDI_MOD_WHEEL.toMidi (64, 0));
        assertArrayEquals (new int [] {0xB9, 11, 127}, ExpressionTarget.MIDI_EXPRESSION.toMidi (200, 9));
        assertArrayEquals (new int [] {0xBF, 74, 0}, ExpressionTarget.MIDI_BRIGHTNESS.toMidi (-5, 15));
        assertArrayEquals (new int [] {0xD2, 100, 0}, ExpressionTarget.MIDI_CHANNEL_PRESSURE.toMidi (100, 2));
    }


    @Test
    void pitchBendGoesFromCentreToTop ()
    {
        // 8192 = centre
        assertArrayEquals (new int [] {0xE0, 0x00, 0x40}, ExpressionTarget.MIDI_PITCH_BEND_UP.toMidi (0, 0));
        // 16383 = full up
        assertArrayEquals (new int [] {0xE0, 0x7F, 0x7F}, ExpressionTarget.MIDI_PITCH_BEND_UP.toMidi (127, 0));
    }


    @Test
    void parameterTargetsSendNoMidi ()
    {
        assertNull (ExpressionTarget.SELECTED_VOLUME.toMidi (64, 0));
        assertNull (ExpressionTarget.NONE.toMidi (64, 0));
        assertFalse (ExpressionTarget.MASTER_VOLUME.isMidi ());
        assertTrue (ExpressionTarget.MIDI_PITCH_BEND_UP.isMidi ());
    }
}
