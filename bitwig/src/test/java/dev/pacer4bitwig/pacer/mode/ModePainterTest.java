// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.led.LedClock;
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedPattern;
import dev.pacer4bitwig.pacer.led.LedState;
import dev.pacer4bitwig.pacer.live.LedRow;
import dev.pacer4bitwig.pacer.live.LiveBoard;
import dev.pacer4bitwig.pacer.live.PacerColour;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;


class ModePainterTest
{
    private final List<String> sent  = new ArrayList<> ();
    private final LiveBoard    board = new LiveBoard (this.sent::add);


    /** The name message: object 0x01. Everything else is a switch. */
    private long nameMessages ()
    {
        return this.sent.stream ().filter (m -> m.startsWith ("F0 00 01 77 7F 01 01 00 01 01")).count ();
    }


    @Test
    void paintingAModeWritesTenSwitchesAndTheName ()
    {
        ModePainter.paintMode (this.board, Mode.LOOP);
        assertEquals (PacerMap.NUM_SWITCHES + 1, this.sent.size (), "ten LEDs and one name");
        assertEquals (1, this.nameMessages ());
    }


    @Test
    void paintingTheSameModeAgainSendsNothing ()
    {
        ModePainter.paintMode (this.board, Mode.LOOP);
        this.sent.clear ();
        ModePainter.paintMode (this.board, Mode.LOOP);
        assertTrue (this.sent.isEmpty (), "no LOAD SYS flash for a repaint that changes nothing");
    }


    @Test
    void switchingModeOnlyWritesWhatDiffers ()
    {
        ModePainter.paintMode (this.board, Mode.LOOP);
        this.sent.clear ();
        ModePainter.paintMode (this.board, Mode.FX);

        // SW 6 is white in every mode, so it is the one switch that never needs rewriting
        assertEquals (10, this.sent.size (), "nine changed switches and the name");
        final String sw6 = String.format ("F0 00 01 77 7F 01 01 00 %02X", Integer.valueOf (0x12));
        assertFalse (this.sent.stream ().anyMatch (m -> m.startsWith (sw6)), "the mode switch keeps its colour");
    }


    @Test
    void awholeBoardIsAtMostElevenMessages ()
    {
        for (final Mode mode: Mode.values ())
        {
            this.board.invalidate ();
            this.sent.clear ();
            ModePainter.paintMode (this.board, mode);
            assertTrue (this.sent.size () <= PacerMap.NUM_SWITCHES + 1, mode + " fits in one burst");
        }
    }


    @Test
    void everyMessageIsALiveEditOfTheLoadedPresetOnly ()
    {
        ModePainter.paintMode (this.board, Mode.MIX);
        ModePainter.paintMenu (this.board, Mode.MIX);
        assertFalse (this.sent.isEmpty ());
        for (final String message: this.sent)
            assertTrue (message.startsWith ("F0 00 01 77 7F 01 01 00"), "preset index 00 is the RAM copy: " + message);
    }


    @Test
    void theMenuNamesItselfAndLightsTheActiveSlotBrightest ()
    {
        ModePainter.paintMenu (this.board, Mode.FX);
        assertEquals (1, this.nameMessages ());
        assertTrue (this.sent.stream ().anyMatch (m -> m.contains (" 4D 4F 44 45 20")), "display reads MODE");

        // SW 2 holds FX, the active mode, so its on colour is the full value rather than the dimmed one
        final String sw2 = this.sent.stream ().filter (m -> m.startsWith ("F0 00 01 77 7F 01 01 00 0E")).findFirst ().orElseThrow ();
        final String sw1 = this.sent.stream ().filter (m -> m.startsWith ("F0 00 01 77 7F 01 01 00 0D")).findFirst ().orElseThrow ();
        assertTrue (sw2.contains (String.format ("41 01 %02X", Integer.valueOf (PacerColour.MAGENTA.getFull ()))), "active slot is bright");
        assertTrue (sw1.contains (String.format ("41 01 %02X", Integer.valueOf (PacerColour.GREEN.getDim ()))), "inactive slot is dimmed");
    }


    @Test
    void paintFollowsWhetherTheMenuIsOpen ()
    {
        final ModeState state = new ModeState (Mode.LOOP);
        ModePainter.paint (this.board, state);
        assertTrue (this.sent.stream ().anyMatch (m -> m.contains (" 4C 4F 4F 50 20")), "display reads LOOP");

        this.sent.clear ();
        state.openMenu ();
        ModePainter.paint (this.board, state);
        assertTrue (this.sent.stream ().anyMatch (m -> m.contains (" 4D 4F 44 45 20")), "display reads MODE");

        this.sent.clear ();
        state.closeMenu ();
        ModePainter.paint (this.board, state);
        assertTrue (this.sent.stream ().anyMatch (m -> m.contains (" 4C 4F 4F 50 20")), "back to LOOP");
    }


    @Test
    void invalidateForcesAFullRewriteAfterThePacerReloadsItsPreset ()
    {
        ModePainter.paintMode (this.board, Mode.LOOP);
        this.sent.clear ();
        this.board.invalidate ();
        ModePainter.paintMode (this.board, Mode.LOOP);
        assertEquals (PacerMap.NUM_SWITCHES + 1, this.sent.size (), "everything is written again");
    }


    @Test
    void repeatNamePutsTheModeBackOnTheDisplayAfterAPress ()
    {
        ModePainter.paintMode (this.board, Mode.LOOP);
        this.sent.clear ();
        assertTrue (this.board.repeatName (), "a switch press replaces the name with its CC readout");
        assertEquals (1, this.sent.size ());
        assertEquals (1, this.nameMessages ());
    }


    @Test
    void repeatNameDoesNothingBeforeAnythingWasPainted ()
    {
        assertFalse (this.board.repeatName ());
        assertTrue (this.sent.isEmpty ());
    }


    @Test
    void aSwitchRestsAtItsModeColourAndLightsInItsStateColour ()
    {
        // SW A is white in LOOP mode; while undo is available the looper asks for white, but say it went red
        ModePainter.paintMode (this.board, Mode.LOOP, index -> index == 6 ? PacerColour.RED : PacerColour.OFF);
        final String swA = this.sent.stream ().filter (m -> m.startsWith ("F0 00 01 77 7F 01 01 00 14")).findFirst ().orElseThrow ();
        assertTrue (swA.contains (String.format ("41 01 %02X", Integer.valueOf (PacerColour.RED.getFull ()))), "lit in the state colour");
        assertTrue (swA.contains (String.format ("42 01 %02X", Integer.valueOf (PacerColour.WHITE.getDim ()))), "at rest in the mode colour, dimmed");
    }


    @Test
    void aSwitchWithNoStateKeepsTheColourItsModeGaveIt ()
    {
        ModePainter.paintMode (this.board, Mode.LOOP, index -> PacerColour.OFF);
        final String swB = this.sent.stream ().filter (m -> m.startsWith ("F0 00 01 77 7F 01 01 00 15")).findFirst ().orElseThrow ();
        assertTrue (swB.contains (String.format ("41 01 %02X", Integer.valueOf (PacerColour.GOLD.getFull ()))), "SW B stays gold");
        assertTrue (swB.contains (String.format ("42 01 %02X", Integer.valueOf (PacerColour.GOLD.getDim ()))));
    }


    @Test
    void stateColoursOnlyWriteWhenTheStateActuallyChanges ()
    {
        final PacerColour [] state = {
            PacerColour.OFF
        };
        ModePainter.paintMode (this.board, Mode.LOOP, index -> index == 0 ? state[0] : PacerColour.OFF);
        this.sent.clear ();

        // Repainting every tick must stay silent, or the Pacer sits on LOAD SYS
        for (int tick = 0; tick < 50; tick++)
            ModePainter.paintMode (this.board, Mode.LOOP, index -> index == 0 ? state[0] : PacerColour.OFF);
        assertTrue (this.sent.isEmpty (), "nothing changed, so nothing was sent");

        state[0] = PacerColour.RED;
        ModePainter.paintMode (this.board, Mode.LOOP, index -> index == 0 ? state[0] : PacerColour.OFF);
        assertEquals (1, this.sent.size (), "one switch changed, one message");
    }


    @Test
    void aBlinkingSwitchCostsNothingBecauseOnlyItsPatternMoves ()
    {
        // A blink is the CC echo going 127/0, which is free. The colour underneath it must stay put: every colour
        // change is a SysEx, and one per beat leaves the Pacer showing LOAD SYS instead of the mode name.
        final LedState blinking = new LedState (LedColour.RED, LedPattern.BLINK_FAST);
        ModePainter.paintMode (this.board, Mode.LOOP, index -> index == 0 ? blinking.colour ().toPacer () : PacerColour.OFF);
        this.sent.clear ();

        for (long now = 0; now < 8000; now += 40)
        {
            blinking.pattern ().isOn (LedClock.unsynced (now));
            ModePainter.paintMode (this.board, Mode.LOOP, index -> index == 0 ? blinking.colour ().toPacer () : PacerColour.OFF);
        }
        assertTrue (this.sent.isEmpty (), "eight seconds of blinking must not send a single message");
    }


    @Test
    void aColourThatFollowedTheBeatWouldFloodTheDevice ()
    {
        // Guards the bug this test was written for: tap tempo used to be white on the downbeat and green elsewhere,
        // and the beat counter cached its dark beats as a colour. Both wrote SysEx every beat.
        ModePainter.paintMode (this.board, Mode.LOOP, index -> PacerColour.GREEN);
        this.sent.clear ();

        int beats = 0;
        for (int beat = 0; beat < 16; beat++)
        {
            final PacerColour perBeat = beat % 4 == 0 ? PacerColour.WHITE : PacerColour.GREEN;
            ModePainter.paintMode (this.board, Mode.LOOP, index -> index == 9 ? perBeat : PacerColour.GREEN);
            beats = this.sent.size ();
        }
        assertEquals (8, beats, "a colour that follows the beat costs a message every time it changes");
    }


    @Test
    void blackoutDarkensEverySwitchAndSaysSo ()
    {
        ModePainter.paintMode (this.board, Mode.LOOP);
        this.sent.clear ();
        this.board.blackout ("OFF");

        assertEquals (PacerMap.NUM_SWITCHES + 1, this.sent.size (), "ten switches and the display");
        assertEquals (1, this.nameMessages ());
        for (final String message: this.sent)
            if (!message.startsWith ("F0 00 01 77 7F 01 01 00 01 01"))
                assertTrue (message.contains ("41 01 00") && message.contains ("42 01 00"), "both colours off: " + message);
    }


    @Test
    void theDisplayCanCarryContextRatherThanTheModeName ()
    {
        final ModeState state = new ModeState (Mode.FX);
        ModePainter.paint (this.board, state, Mode.FX, "Gtr", index -> PacerColour.OFF);
        // "Gtr" padded to five characters
        assertTrue (this.sent.stream ().anyMatch (m -> m.contains (" 47 74 72 20 20")), "display reads Gtr");
        // The mode's own name must never be written first and then replaced: that is two messages per paint
        assertEquals (1, this.nameMessages (), "the display is written once");

        this.sent.clear ();
        for (int tick = 0; tick < 50; tick++)
            ModePainter.paint (this.board, state, Mode.FX, "Gtr", index -> PacerColour.OFF);
        assertTrue (this.sent.isEmpty (), "an unchanged context sends nothing");
    }


    @Test
    void anUnchangedSwitchIsNotRewritten ()
    {
        assertTrue (this.board.setLed (0, PacerColour.RED, PacerColour.OFF, LedRow.STRIP));
        assertFalse (this.board.setLed (0, PacerColour.RED, PacerColour.OFF, LedRow.STRIP));
        assertTrue (this.board.setLed (0, PacerColour.RED, PacerColour.OFF, LedRow.WORD), "a different row is a change");
        assertEquals (2, this.board.getMessageCount ());
    }
}
