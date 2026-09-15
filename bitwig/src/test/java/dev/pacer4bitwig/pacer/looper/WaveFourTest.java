// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.command.TapHoldCommand;
import dev.pacer4bitwig.pacer.daw.DawFunction;
import dev.pacer4bitwig.pacer.daw.DawModeSysex;

import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


class WaveFourTest
{
    private static final double EPSILON = 1e-9;


    @Test
    void muteBoundaries ()
    {
        assertEquals (5.3, MuteTiming.nextBoundary (5.3, MuteTiming.IMMEDIATE.getUnitBeats (4)), EPSILON);
        assertEquals (6, MuteTiming.nextBoundary (5.3, MuteTiming.NEXT_BEAT.getUnitBeats (4)), EPSILON);
        assertEquals (8, MuteTiming.nextBoundary (5.3, MuteTiming.NEXT_BAR.getUnitBeats (4)), EPSILON);
        // Just past a downbeat still counts as that downbeat
        assertEquals (8, MuteTiming.nextBoundary (8.03, MuteTiming.NEXT_BAR.getUnitBeats (4)), EPSILON);
        assertEquals (12, MuteTiming.nextBoundary (8.2, MuteTiming.NEXT_BAR.getUnitBeats (4)), EPSILON);
        // 3/4
        assertEquals (6, MuteTiming.nextBoundary (4, MuteTiming.NEXT_BAR.getUnitBeats (3)), EPSILON);
    }


    @Test
    void dawFunctionsByCC ()
    {
        assertEquals (DawFunction.PLAY, DawFunction.fromCC (84));
        assertEquals (DawFunction.Kind.HELD, DawFunction.fromCC (81).getKind ());
        assertEquals (DawFunction.Kind.RELATIVE, DawFunction.fromCC (9).getKind ());
        assertNull (DawFunction.fromCC (1));
        assertNull (DawFunction.fromCC (200));
        // Every CC is used once
        assertEquals (DawFunction.values ().length, Arrays.stream (DawFunction.values ()).mapToInt (DawFunction::getCC).distinct ().count ());
    }


    @Test
    void dawModeSysexMatchesNektarsScript ()
    {
        final int [] off = new int [10];
        final int [] on = new int [10];
        Arrays.fill (off, 0x18);
        Arrays.fill (on, 0x17);
        final String message = DawModeSysex.slotColours (off, on);
        assertTrue (message.startsWith ("F0 00 01 77 7F 01 06 18 00 01 02 18 17 00 02 02 18 17"));
        assertTrue (message.endsWith ("00 0A 02 18 17 1F F7"));
        assertEquals (8 + 10 * 5 + 2, message.split (" ").length);

        assertTrue (DawModeSysex.isFunctionReport ("f0000177 7f01 10 0102 03"));
        assertTrue (DawModeSysex.isFunctionReport ("F0 00 01 77 7F 01 10 00 00 00 00"));
        assertFalse (DawModeSysex.isFunctionReport ("f0000177 7f01 01 13"));
        assertFalse (DawModeSysex.isFunctionReport ("f07e7f0602000177"));
        assertFalse (DawModeSysex.isFunctionReport (null));
    }


    @Test
    void releaseRunsBeforeTheTapOnRelease ()
    {
        final List<String> events = new ArrayList<> ();
        final TapHoldCommand command = new TapHoldCommand ( () -> true, () -> events.add ("tap"), () -> events.add ("hold"), () -> events.add ("release"), null, () -> 0, (task, delay) -> task.run ());

        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        command.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("tap", "hold", "release"), events);
    }
}
