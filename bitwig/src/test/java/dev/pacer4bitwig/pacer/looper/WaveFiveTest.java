// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.command.TapHoldCommand;
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedMode;
import dev.pacer4bitwig.pacer.led.LedPattern;
import dev.pacer4bitwig.pacer.led.LedState;

import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


class WaveFiveTest
{
    private static final double EPSILON = 1e-9;

    private final List<String>  events  = new ArrayList<> ();
    private final AtomicLong    now     = new AtomicLong (10_000);
    private final AtomicBoolean enabled = new AtomicBoolean (true);


    private TapHoldCommand command (final boolean tapOnPress)
    {
        return new TapHoldCommand ( () -> tapOnPress, () -> this.events.add ("tap"), () -> this.events.add ("hold"), null).withDoubleTap ( () -> this.events.add ("double"), this.enabled::get, () -> 350, this.now::get);
    }


    private void press (final TapHoldCommand command, final long atMillis)
    {
        this.now.set (atMillis);
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);
    }


    @Test
    void doubleTapReplacesTheSecondTap ()
    {
        final TapHoldCommand command = this.command (true);
        this.press (command, 10_000);
        this.press (command, 10_200);
        assertEquals (List.of ("tap", "double"), this.events);

        // A third quick tap starts over
        this.press (command, 10_300);
        assertEquals (List.of ("tap", "double", "tap"), this.events);
    }


    @Test
    void slowTapsStayTaps ()
    {
        final TapHoldCommand command = this.command (false);
        this.press (command, 10_000);
        this.press (command, 10_400);
        assertEquals (List.of ("tap", "tap"), this.events);
    }


    @Test
    void doubleTapCanBeSwitchedOff ()
    {
        this.enabled.set (false);
        final TapHoldCommand command = this.command (true);
        this.press (command, 10_000);
        this.press (command, 10_100);
        assertEquals (List.of ("tap", "tap"), this.events);
    }


    @Test
    void aHoldIsNotHalfADoubleTap ()
    {
        final TapHoldCommand command = this.command (true);
        this.now.set (10_000);
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        command.execute (ButtonEvent.UP, 0);
        this.press (command, 10_100);
        assertEquals (List.of ("tap", "hold", "tap"), this.events);
    }


    @Test
    void loopSwitchCounts ()
    {
        assertEquals (0, LoopSwitchCount.NONE.getCount ());
        assertEquals (6, LoopSwitchCount.SIX.getCount ());
        assertEquals (LoopSwitchCount.values ().length, 7);
    }


    @Test
    void pedalRanges ()
    {
        assertTrue (PedalResponse.DEFAULT.isIdentity ());
        final PedalResponse half = new PedalResponse (PedalCurve.LINEAR, 20, 70);
        assertFalse (half.isIdentity ());
        assertEquals (0.2, half.map (0.0), EPSILON);
        assertEquals (0.7, half.map (1.0), EPSILON);
        assertEquals (0.45, half.map (0.5), EPSILON);
        // Reversed by the range
        final PedalResponse reversed = new PedalResponse (PedalCurve.LINEAR, 100, 0);
        assertEquals (127, reversed.map (0));
        assertEquals (0, reversed.map (127));
        // Curve first, then range
        assertEquals (0.5 + 0.5 * 0.25, new PedalResponse (PedalCurve.SLOW_START, 50, 100).map (0.5), EPSILON);
        // Out-of-range percentages are clamped
        assertEquals (1.0, new PedalResponse (PedalCurve.LINEAR, -20, 150).map (1.0), EPSILON);
    }


    @Test
    void customLoopColours ()
    {
        final LoopColours colours = new LoopColours (LedColour.WHITE, LedColour.BLUE, LedColour.PURPLE, LedColour.AMBER);
        assertEquals (LedState.solid (LedColour.WHITE), LoopLeds.forLoop (LoopState.STOPPED, false, false, LedMode.MULTI_COLOUR, colours));
        assertEquals (new LedState (LedColour.BLUE, LedPattern.SOLID_DIP), LoopLeds.forLoop (LoopState.PLAYING, false, false, LedMode.MULTI_COLOUR, colours));
        assertEquals (new LedState (LedColour.PURPLE, LedPattern.SOLID_DIP), LoopLeds.forLoop (LoopState.PLAYING, true, false, LedMode.MULTI_COLOUR, colours));
        assertEquals (LedState.solid (LedColour.AMBER), LoopLeds.forLoop (LoopState.PLAYING, false, true, LedMode.MULTI_COLOUR, colours));
        assertEquals (new LedState (LedColour.PURPLE, LedPattern.BLINK_FAST), LoopLeds.forLoop (LoopState.RECORD_QUEUED, false, false, LedMode.MULTI_COLOUR, colours));
        assertEquals (LoopColours.Choice.PURPLE, LoopColours.Choice.of (LedColour.PURPLE));
    }


    @Test
    void notificationLevels ()
    {
        assertTrue (NotificationLevel.ALL.shows (false));
        assertTrue (NotificationLevel.IMPORTANT.shows (true));
        assertFalse (NotificationLevel.IMPORTANT.shows (false));
        assertFalse (NotificationLevel.OFF.shows (true));
    }


    @Test
    void texts ()
    {
        assertEquals ("Chorus", LooperText.rowName ("Intro, Verse ,Chorus", 2));
        assertEquals ("", LooperText.rowName ("Intro, Verse", 2));
        assertEquals ("", LooperText.rowName (null, 0));
        assertEquals ("Row 3", LooperText.rowLabel (2, ""));
        assertEquals ("Row 3: Chorus", LooperText.rowLabel (2, " Chorus "));

        final String status = LooperText.status (1, "Verse", new LoopState [] {LoopState.PLAYING, LoopState.RECORDING, null, LoopState.EMPTY}, new boolean [] {true, false, false, false});
        assertEquals ("Row 2: Verse |  1 ▶ (muted)  2 ●  4 –", status);
    }
}
