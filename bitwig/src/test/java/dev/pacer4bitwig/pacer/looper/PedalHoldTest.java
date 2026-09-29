// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


/**
 * A pedal that stops driving a MIDI controller has to hand it back, or its last value stays on the instrument until
 * the instrument is power-cycled. Found on a real rig: EXP1 on the mod wheel, swept, then SW 6 tapped - the synth
 * kept CC 1 at that value for good, and because its patches route the mod wheel to the filter, the cutoff knob
 * appeared to stop working. A factory reset was the only thing that cleared it.
 * <p>
 * The resting values are the whole subtlety. Handing everything back at 0 would have silenced the same rig on every
 * mode change, because its other pedal is on CC 11.
 */
class PedalHoldTest
{
    private static final int CHANNEL = 0;


    private static int [] cc (final int controller, final int value)
    {
        return new int []
        {
            0xB0 | CHANNEL,
            controller,
            value
        };
    }


    /** Identity response: the pedal position is the value. */
    private static int map (final int position)
    {
        return position;
    }


    @Test
    void holdsWhatItSentAndHandsItBack ()
    {
        final PedalHold hold = new PedalHold ();

        assertArrayEquals (cc (1, 100), hold.press (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, 100, 100));
        assertTrue (hold.isHolding ());
        assertArrayEquals (cc (1, 0), hold.release (), "the mod wheel rests at zero");
        assertFalse (hold.isHolding ());
    }


    @Test
    void expressionRestsAtFullNotZero ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_EXPRESSION, CHANNEL, 40, 40);

        // CC 11 attenuates. Handing it back at 0 would silence the instrument - worse than the bug being fixed.
        assertArrayEquals (cc (11, 127), hold.release ());
    }


    @Test
    void channelVolumeIsNotHandedBack ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_VOLUME, CHANNEL, 20, 20);

        // An absolute level with no neutral: any value we chose would be a guess at the player's mix
        assertNull (hold.release ());
    }


    @Test
    void brightnessIsNotHandedBack ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_BRIGHTNESS, CHANNEL, 90, 90);

        // On a non-MPE synth CC 74 is the cutoff itself, so any value is an override rather than a release
        assertNull (hold.release ());
    }


    @Test
    void pitchBendRestsAtCentre ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_PITCH_BEND_UP, CHANNEL, 127, 127);

        assertArrayEquals (new int []
        {
            0xE0,
            0x00,
            0x40
        }, hold.release (), "8192 - a parked bend leaves the whole instrument detuned");
    }


    @Test
    void channelPressureRestsAtZero ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_CHANNEL_PRESSURE, CHANNEL, 80, 80);

        assertArrayEquals (new int []
        {
            0xD0,
            0,
            0
        }, hold.release ());
    }


    @Test
    void releaseIsIdempotent ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, 100, 100);

        hold.release ();
        assertNull (hold.release (), "several teardown paths may call this");
    }


    @Test
    void releasesOnTheChannelItSentOn ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_MOD_WHEEL, 3, 100, 100);

        // The channel setting can move between the press and the release; the parked value is on the old channel
        assertArrayEquals (new int []
        {
            0xB3,
            1,
            0
        }, hold.release ());
    }


    @Test
    void parameterAndFxTargetsHoldNothing ()
    {
        final PedalHold hold = new PedalHold ();

        assertNull (hold.press (ExpressionTarget.SELECTED_VOLUME, CHANNEL, 60, 60));
        assertFalse (hold.isHolding ());
        assertNull (hold.release ());

        assertNull (hold.press (ExpressionTarget.FOCUSED_REMOTE_1, CHANNEL, 60, 60));
        assertFalse (hold.isHolding ());
        assertNull (hold.press (ExpressionTarget.NONE, CHANNEL, 60, 60));
        assertFalse (hold.isHolding ());
    }


    @Test
    void aPedalThatNeverMovedAssertsNothing ()
    {
        final PedalHold hold = new PedalHold ();

        // Opening a project must not invent a position the foot was never at
        assertNull (hold.reassert (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, PedalHoldTest::map));
    }


    @Test
    void reassertRestatesTheRememberedPosition ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, 100, 100);
        hold.release ();

        // Without this the receiver sits at neutral under a pedal that is still at the toe, and the next nudge jumps
        assertArrayEquals (cc (1, 100), hold.reassert (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, PedalHoldTest::map));
        assertTrue (hold.isHolding (), "re-asserting takes the hold again");
    }


    @Test
    void reassertHonoursTheNewMapping ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, 127, 127);

        // A reversed pedal (heel 100, toe 0) must re-state the mapped value, not the raw position
        final PedalResponse reversed = new PedalResponse (PedalCurve.LINEAR, 100, 0);
        assertArrayEquals (cc (1, 0), hold.reassert (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, reversed::map));
    }


    @Test
    void stalenessTracksBothTargetAndChannel ()
    {
        final PedalHold hold = new PedalHold ();
        assertFalse (hold.isStale (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL), "nothing held yet");

        hold.press (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, 100, 100);
        assertFalse (hold.isStale (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL));
        assertTrue (hold.isStale (ExpressionTarget.MIDI_EXPRESSION, CHANNEL), "target moved");
        assertTrue (hold.isStale (ExpressionTarget.MIDI_MOD_WHEEL, 9), "channel moved");
    }


    @Test
    void changingTargetHandsBackTheOldControllerNotTheNewOne ()
    {
        final PedalHold hold = new PedalHold ();
        hold.press (ExpressionTarget.MIDI_MOD_WHEEL, CHANNEL, 100, 100);

        // The settings panel moved this pedal from mod wheel to expression: CC 1 is what is parked
        assertTrue (hold.isStale (ExpressionTarget.MIDI_EXPRESSION, CHANNEL));
        assertArrayEquals (cc (1, 0), hold.release ());
    }
}
