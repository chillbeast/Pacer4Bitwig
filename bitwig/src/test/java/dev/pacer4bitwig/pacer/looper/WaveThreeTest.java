// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.command.TapHoldCommand;
import dev.pacer4bitwig.pacer.looper.VolumeFade.Direction;

import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


class WaveThreeTest
{
    private static final double EPSILON = 1e-9;


    @Test
    void pedalCurves ()
    {
        assertEquals (64, PedalCurve.LINEAR.apply (64));
        assertEquals (127, PedalCurve.INVERTED.apply (0));
        assertEquals (0, PedalCurve.INVERTED.apply (127));
        assertEquals (0.25, PedalCurve.SLOW_START.apply (0.5), EPSILON);
        assertEquals (Math.sqrt (0.5), PedalCurve.FAST_START.apply (0.5), EPSILON);
        // Ends stay put for the non-inverting curves
        for (final PedalCurve curve: new PedalCurve [] {PedalCurve.LINEAR, PedalCurve.SLOW_START, PedalCurve.FAST_START})
        {
            assertEquals (0, curve.apply (0));
            assertEquals (127, curve.apply (127));
        }
        assertEquals (1.0, PedalCurve.LINEAR.apply (7.0), EPSILON);
    }


    @Test
    void fadeOutRampsToSilenceOverTheLength ()
    {
        final VolumeFade fade = new VolumeFade (Direction.OUT, 8, Map.of (0, 0.8, 2, 0.5));
        assertFalse (fade.isStarted ());
        assertEquals (0.8, fade.getVolume (0, 100), EPSILON);

        fade.startAt (16);
        fade.startAt (17);
        assertEquals (0.8, fade.getVolume (0, 16), EPSILON);
        assertEquals (0.4, fade.getVolume (0, 20), EPSILON);
        assertEquals (0.25, fade.getVolume (2, 20), EPSILON);
        assertEquals (-1, fade.getVolume (1, 20), EPSILON);
        assertFalse (fade.isComplete (23.9));
        assertTrue (fade.isComplete (24));
        assertEquals (0, fade.getVolume (0, 30), EPSILON);
    }


    @Test
    void fadeInRampsFromSilence ()
    {
        final VolumeFade fade = new VolumeFade (Direction.IN, 4, Map.of (1, 1.0));
        fade.startAt (0);
        assertEquals (0, fade.getVolume (1, 0), EPSILON);
        assertEquals (0.75, fade.getVolume (1, 3), EPSILON);
        assertEquals (1.0, fade.getVolume (1, 5), EPSILON);
    }


    @Test
    void destructiveActionsAreMarked ()
    {
        assertTrue (Action.CLEAR_ROW.isDestructive ());
        assertTrue (Action.CLEAR_LAST_LOOP.isDestructive ());
        assertTrue (Action.CLEAR_SELECTED.isDestructive ());
        assertFalse (Action.UNDO.isDestructive ());
        assertFalse (Action.FADE_OUT.isDestructive ());
    }


    @Test
    void longHoldNeedsTheSwitchToStayDown ()
    {
        final List<String> events = new ArrayList<> ();
        final List<Runnable> scheduled = new ArrayList<> ();
        final TapHoldCommand command = new TapHoldCommand ( () -> false, () -> events.add ("tap"), () -> events.add ("hold"), null, () -> 1000, (task, delay) -> scheduled.add (task));

        // Held through the extra time
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        assertEquals (List.of (), events);
        scheduled.remove (0).run ();
        command.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("hold"), events);

        // Released before the extra time: nothing at all
        events.clear ();
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        command.execute (ButtonEvent.UP, 0);
        scheduled.remove (0).run ();
        assertEquals (List.of (), events);

        // Released and pressed again before the check: the new press does not inherit the old hold
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        command.execute (ButtonEvent.UP, 0);
        command.execute (ButtonEvent.DOWN, 127);
        scheduled.remove (0).run ();
        command.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("tap"), events);
    }
}
