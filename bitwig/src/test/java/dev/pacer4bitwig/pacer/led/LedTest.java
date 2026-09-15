// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.command.TapHoldCommand;
import dev.pacer4bitwig.pacer.controller.MidiFilters;
import dev.pacer4bitwig.pacer.controller.PacerMap;

import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


class LedTest
{
    private final List<String>             sent = new ArrayList<> ();
    private final AtomicReference<LedMode> mode = new AtomicReference<> (LedMode.MULTI_COLOUR);


    private SwitchLedWriter writer (final int switchIndex)
    {
        return new SwitchLedWriter (switchIndex, this.mode::get, (cc, value) -> this.sent.add (cc + "=" + value));
    }


    private static LedClock synced (final double beats)
    {
        return new LedClock (0, true, beats, 4);
    }


    @Test
    void mapMatchesContract ()
    {
        assertEquals (102, PacerMap.switchCC (0));
        assertEquals (111, PacerMap.switchCC (9));
        assertEquals (20, PacerMap.colourSlotCC (0, 2));
        assertEquals (24, PacerMap.colourSlotCC (0, 6));
        assertEquals (45, PacerMap.colourSlotCC (5, 2));
        assertEquals (69, PacerMap.colourSlotCC (9, 6));
        assertThrows (IllegalArgumentException.class, () -> PacerMap.colourSlotCC (0, 1));
    }


    @Test
    void passThroughFiltersHoldBackTheLooperChannel ()
    {
        final List<String> filters = Arrays.asList (MidiFilters.allChannelsExcept (PacerMap.DEFAULT_MIDI_CHANNEL));
        assertEquals (7 * 15, filters.size ());
        assertTrue (filters.contains ("90????"));
        assertTrue (filters.contains ("BE????"));
        assertFalse (filters.contains ("BF????"));
        assertFalse (filters.contains ("9F????"));
    }


    @Test
    void multiColourClearsPreviousSlotBeforeLightingTheNext ()
    {
        final SwitchLedWriter w = this.writer (1); // SW 2: action 103, slots 25-29

        w.accept (LedColour.RED.ordinal ());
        assertEquals (List.of ("25=127"), this.sent);

        this.sent.clear ();
        w.accept (LedColour.GREEN.ordinal ());
        assertEquals (List.of ("25=0", "26=127"), this.sent);

        this.sent.clear ();
        w.accept (LedColour.WHITE.ordinal ());
        assertEquals (List.of ("26=0", "103=127"), this.sent);

        this.sent.clear ();
        w.accept (0);
        assertEquals (List.of ("103=0"), this.sent);
    }


    @Test
    void forcedFlushRepaintsWithoutClearing ()
    {
        final SwitchLedWriter w = this.writer (0);
        w.accept (LedColour.AMBER.ordinal ());
        this.sent.clear ();

        w.accept (LedColour.AMBER.ordinal ());
        assertEquals (List.of ("22=127"), this.sent);

        w.accept (0);
        this.sent.clear ();
        w.accept (0);
        assertEquals (List.of ("102=0"), this.sent);
    }


    @Test
    void twoColourOnlyUsesTheActionCC ()
    {
        this.mode.set (LedMode.TWO_COLOUR);
        final SwitchLedWriter w = this.writer (6); // SW A: 108

        w.accept (LedColour.RED.ordinal ());
        w.accept (LedColour.GREEN.ordinal ());
        w.accept (0);
        assertEquals (List.of ("108=127", "108=127", "108=0"), this.sent);
    }


    @Test
    void resetTurnsOffEveryCC ()
    {
        this.writer (9).reset ();
        assertEquals (List.of ("111=0", "65=0", "66=0", "67=0", "68=0", "69=0"), this.sent);
    }


    @Test
    void wallClockPatterns ()
    {
        assertTrue (LedPattern.BLIP.isOn (LedClock.unsynced (1_000)));
        assertFalse (LedPattern.BLIP.isOn (LedClock.unsynced (1_500)));
        assertTrue (LedPattern.BLINK_FAST.isOn (LedClock.unsynced (0)));
        assertFalse (LedPattern.BLINK_FAST.isOn (LedClock.unsynced (125)));
        assertTrue (LedPattern.SOLID_DIP.isOn (LedClock.unsynced (0)));
        assertFalse (LedPattern.BEAT_FLASH.isOn (LedClock.unsynced (0)));
    }


    @Test
    void beatSyncedPatterns ()
    {
        // Quarter notes
        assertTrue (LedPattern.BLINK_MEDIUM.isOn (synced (5.1)));
        assertFalse (LedPattern.BLINK_MEDIUM.isOn (synced (5.6)));
        // Eighth notes
        assertTrue (LedPattern.BLINK_FAST.isOn (synced (5.1)));
        assertFalse (LedPattern.BLINK_FAST.isOn (synced (5.3)));
        assertTrue (LedPattern.BLINK_FAST.isOn (synced (5.6)));
        // Downbeat of bar 2 (beat 4 in quarter notes)
        assertFalse (LedPattern.SOLID_DIP.isOn (synced (4.1)));
        assertTrue (LedPattern.SOLID_DIP.isOn (synced (4.5)));
        assertTrue (LedPattern.BLIP.isOn (synced (8.2)));
        assertFalse (LedPattern.BLIP.isOn (synced (9.2)));
        assertTrue (LedPattern.BEAT_FLASH.isOn (synced (9.1)));
        assertFalse (LedPattern.BEAT_FLASH.isOn (synced (9.4)));
        // Pre-roll positions are negative
        assertEquals (3, synced (-0.5).beatInBar ());
        assertEquals (1, new LedClock (0, true, 7.5, 3).beatInBar ());
    }


    @Test
    void stateCodes ()
    {
        assertEquals (0, LedState.DARK.code (LedClock.unsynced (0)));
        assertEquals (LedColour.GREEN.ordinal (), LedState.solid (LedColour.GREEN).code (LedClock.unsynced (123_456)));
        assertEquals (0, new LedState (LedColour.RED, LedPattern.BLINK_MEDIUM).code (LedClock.unsynced (250)));
        assertEquals (LedColour.OFF, LedColour.fromCode (42));
    }


    @Test
    void tapHoldOnRelease ()
    {
        final List<String> events = new ArrayList<> ();
        final TapHoldCommand command = new TapHoldCommand ( () -> false, () -> events.add ("tap"), () -> events.add ("hold"), null);

        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("tap"), events);

        events.clear ();
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        command.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("hold"), events);
    }


    @Test
    void tapHoldOnPress ()
    {
        final List<String> events = new ArrayList<> ();
        final TapHoldCommand command = new TapHoldCommand ( () -> true, () -> events.add ("tap"), () -> events.add ("hold"), () -> events.add ("after"));

        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        command.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("tap", "after", "hold", "after", "after"), events);
    }
}
