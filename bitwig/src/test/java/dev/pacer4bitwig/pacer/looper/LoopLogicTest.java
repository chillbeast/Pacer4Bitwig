// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedMode;
import dev.pacer4bitwig.pacer.led.LedPattern;
import dev.pacer4bitwig.pacer.led.LedState;
import dev.pacer4bitwig.util.Labelled;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;


class LoopLogicTest
{
    //                          exists hasContent playing playQ  stopQ  rec    recQ
    @Test
    void stateFromSlotFlags ()
    {
        assertEquals (LoopState.EMPTY, LoopState.of (false, true, true, false, false, false, false));
        assertEquals (LoopState.EMPTY, LoopState.of (true, false, false, false, false, false, false));
        assertEquals (LoopState.STOPPED, LoopState.of (true, true, false, false, false, false, false));
        assertEquals (LoopState.PLAYING, LoopState.of (true, true, true, false, false, false, false));
        assertEquals (LoopState.PLAY_QUEUED, LoopState.of (true, true, false, true, false, false, false));
        assertEquals (LoopState.STOP_QUEUED, LoopState.of (true, true, true, false, true, false, false));
        assertEquals (LoopState.RECORD_QUEUED, LoopState.of (true, false, false, false, false, false, true));
        // Recording wins over the playback flags Bitwig also reports while recording
        assertEquals (LoopState.RECORDING, LoopState.of (true, true, true, false, false, true, false));
        assertEquals (LoopState.RECORDING, LoopState.of (true, true, true, false, true, true, false));
    }


    @Test
    void tapActions ()
    {
        assertEquals (LoopAction.RECORD, LoopAction.onTap (LoopState.EMPTY, PlayingTapAction.STOP));
        assertEquals (LoopAction.PLAY, LoopAction.onTap (LoopState.RECORDING, PlayingTapAction.STOP));
        assertEquals (LoopAction.STOP, LoopAction.onTap (LoopState.RECORD_QUEUED, PlayingTapAction.STOP));
        assertEquals (LoopAction.STOP, LoopAction.onTap (LoopState.PLAY_QUEUED, PlayingTapAction.STOP));
        assertEquals (LoopAction.PLAY, LoopAction.onTap (LoopState.STOP_QUEUED, PlayingTapAction.STOP));
        assertEquals (LoopAction.PLAY, LoopAction.onTap (LoopState.STOPPED, PlayingTapAction.NOTHING));

        assertEquals (LoopAction.STOP, LoopAction.onTap (LoopState.PLAYING, PlayingTapAction.STOP));
        assertEquals (LoopAction.TOGGLE_MUTE, LoopAction.onTap (LoopState.PLAYING, PlayingTapAction.MUTE));
        assertEquals (LoopAction.OVERDUB, LoopAction.onTap (LoopState.PLAYING, PlayingTapAction.OVERDUB));
        assertEquals (LoopAction.NONE, LoopAction.onTap (LoopState.PLAYING, PlayingTapAction.NOTHING));
    }


    @Test
    void layouts ()
    {
        assertTrue (SwitchLayout.FOUR_LOOPS.isLoopSwitch (3));
        assertFalse (SwitchLayout.FOUR_LOOPS.isLoopSwitch (4));
        assertTrue (SwitchLayout.SIX_LOOPS.isLoopSwitch (5));
        assertFalse (SwitchLayout.SIX_LOOPS.isLoopSwitch (6));
    }


    @Test
    void tapTiming ()
    {
        assertTrue (TapTiming.loopTapOnPress (true, HoldAction.DELETE));
        assertFalse (TapTiming.loopTapOnPress (false, HoldAction.DELETE));
        // Nothing to disambiguate without a hold action
        assertTrue (TapTiming.loopTapOnPress (false, HoldAction.NOTHING));

        assertFalse (TapTiming.actionTapOnPress (Action.UNDO, Action.REDO));
        assertTrue (TapTiming.actionTapOnPress (Action.UNDO, Action.NONE));
        assertTrue (TapTiming.actionTapOnPress (Action.TAP_TEMPO, Action.TRANSPORT_PLAY_STOP));
    }


    @Test
    void settingLabelsAreUnique ()
    {
        for (final Labelled [] values: new Labelled [] []
        {
            Action.values (),
            ExpressionTarget.values (),
            PlayingTapAction.values (),
            HoldAction.values (),
            LoopLength.values (),
            QuantizationChoice.values (),
            SwitchLayout.values (),
            LedMode.values ()
        })
        {
            final String [] labels = Labelled.labels (values);
            assertEquals (labels.length, new HashSet<> (Arrays.asList (labels)).size ());
        }
        assertEquals (Action.REDO, Labelled.fromLabel (Action.values (), "Redo", Action.NONE));
        assertEquals (Action.NONE, Labelled.fromLabel (Action.values (), "renamed in a later version", Action.NONE));
    }


    @Test
    void loopLedsMultiColour ()
    {
        assertEquals (LedState.DARK, LoopLeds.forLoop (LoopState.EMPTY, false, false, LedMode.MULTI_COLOUR));
        assertEquals (LedState.solid (LedColour.AMBER), LoopLeds.forLoop (LoopState.STOPPED, false, false, LedMode.MULTI_COLOUR));
        assertEquals (new LedState (LedColour.GREEN, LedPattern.SOLID_DIP), LoopLeds.forLoop (LoopState.PLAYING, false, false, LedMode.MULTI_COLOUR));
        assertEquals (new LedState (LedColour.RED, LedPattern.SOLID_DIP), LoopLeds.forLoop (LoopState.PLAYING, true, false, LedMode.MULTI_COLOUR));
        assertEquals (LedState.solid (LedColour.BLUE), LoopLeds.forLoop (LoopState.PLAYING, false, true, LedMode.MULTI_COLOUR));
        assertEquals (new LedState (LedColour.RED, LedPattern.BLINK_FAST), LoopLeds.forLoop (LoopState.RECORD_QUEUED, false, false, LedMode.MULTI_COLOUR));
    }


    @Test
    void loopLedsTwoColourUsePatterns ()
    {
        assertEquals (LedPattern.SOLID_DIP, LoopLeds.forLoop (LoopState.PLAYING, false, false, LedMode.TWO_COLOUR).pattern ());
        assertEquals (LedPattern.BLIP, LoopLeds.forLoop (LoopState.PLAYING, false, true, LedMode.TWO_COLOUR).pattern ());
        assertEquals (LedPattern.BLINK_MEDIUM, LoopLeds.forLoop (LoopState.RECORDING, false, false, LedMode.TWO_COLOUR).pattern ());
        assertEquals (LedPattern.BLIP, LoopLeds.forLoop (LoopState.STOPPED, false, false, LedMode.TWO_COLOUR).pattern ());
        assertEquals (LedPattern.BLINK_FAST, LoopLeds.forLoop (LoopState.STOP_QUEUED, false, false, LedMode.TWO_COLOUR).pattern ());
    }
}
