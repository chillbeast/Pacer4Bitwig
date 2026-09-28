// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.utils.ButtonEvent;

import dev.pacer4bitwig.pacer.command.TapHoldCommand;
import dev.pacer4bitwig.pacer.led.LedClock;
import dev.pacer4bitwig.pacer.led.LedState;
import dev.pacer4bitwig.pacer.live.LiveBoard;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.mode.Mode;
import dev.pacer4bitwig.pacer.mode.ShiftLayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The controller wired to real {@link TapHoldCommand}s, the way the setup wires it, with the looper and the FX side
 * recording what they were asked to do.
 */
class PacerControllerTest
{
    private static final int         SW_1   = 0;
    private static final int         SW_4   = 3;
    private static final int         SW_6   = Mode.MODE_SWITCH_INDEX;
    private static final int         SW_5   = 4;
    private static final int         SW_A   = 6;
    private static final int         SW_D   = 9;

    private final List<String>       ran    = new ArrayList<> ();
    private final List<Runnable>     later  = new ArrayList<> ();
    private final List<String>       sysex  = new ArrayList<> ();
    private boolean                  loopTapOnPress;
    private PacerConfiguration       configuration;
    private PacerController          controller;


    @BeforeEach
    void setUp ()
    {
        final IHost host = (IHost) Proxy.newProxyInstance (IHost.class.getClassLoader (), new Class<?> []
        {
            IHost.class
        }, (proxy, method, args) -> defaultValue (method.getReturnType ()));
        final PacerConfiguration configuration = new PacerConfiguration (host, null, Collections.emptyList ());
        this.configuration = configuration;

        final LooperController looper = new LooperController (host, null, configuration, null)
        {
            /** {@inheritDoc} */
            @Override
            public boolean isLoopSwitchTapOnPress ()
            {
                return PacerControllerTest.this.loopTapOnPress;
            }


            /** {@inheritDoc} */
            @Override
            public long getLoopSwitchExtraHoldMillis ()
            {
                return 0;
            }


            /** {@inheritDoc} */
            @Override
            public boolean isLoopSwitchDoubleTapEnabled ()
            {
                return false;
            }


            /** {@inheritDoc} */
            @Override
            public void loopSwitchTap (final int switchIndex)
            {
                PacerControllerTest.this.ran.add ("loop tap " + (switchIndex + 1));
            }


            /** {@inheritDoc} */
            @Override
            public void loopSwitchHold (final int switchIndex)
            {
                PacerControllerTest.this.ran.add ("loop hold " + (switchIndex + 1));
            }


            /** {@inheritDoc} */
            @Override
            public void loopSwitchRelease (final int switchIndex)
            {
                PacerControllerTest.this.ran.add ("loop release " + (switchIndex + 1));
            }


            /** {@inheritDoc} */
            @Override
            public void perform (final Action action)
            {
                PacerControllerTest.this.ran.add (action.name ());
            }


            /** {@inheritDoc} */
            @Override
            public LedClock getLedClock (final long now)
            {
                return LedClock.unsynced (now);
            }


            /** {@inheritDoc} */
            @Override
            public int getBeatCounterCode (final int topRowIndex, final long now)
            {
                return -1;
            }


            /** {@inheritDoc} */
            @Override
            public LedState loopSwitchLed (final int switchIndex)
            {
                return LedState.DARK;
            }


            /** {@inheritDoc} */
            @Override
            public LedState actionLed (final Action action, final LedClock ledClock)
            {
                return LedState.DARK;
            }


            /** {@inheritDoc} */
            @Override
            public String getRowDisplayName ()
            {
                return "";
            }
        };

        final FxController fx = new FxController (host, configuration, null, () -> "")
        {
            /** {@inheritDoc} */
            @Override
            public void perform (final Action action)
            {
                PacerControllerTest.this.ran.add (action.name ());
            }


            /** {@inheritDoc} */
            @Override
            public String getFocusedInstrumentName ()
            {
                return "";
            }


            /** {@inheritDoc} */
            @Override
            public LedState actionLed (final Action action)
            {
                return LedState.DARK;
            }
        };

        this.controller = new PacerController (host, configuration, looper, fx, new LiveBoard (this.sysex::add));
    }


    @Test
    void holdingTheSongSlotTooLongDoesNotFadeOutEveryLoop ()
    {
        this.openMenu ();
        // The foot lands on SW 4 (Song) and rests there: the slot switches on press, and the hold that follows used
        // to run Song's own SW 4 hold - fade out and stop every loop
        this.hold (SW_4);

        assertEquals (Mode.SONG, this.controller.getMode ());
        assertTrue (this.ran.isEmpty (), "the menu slot ran nothing but the mode change: " + this.ran);
    }


    @Test
    void pickingTheLooperDoesNotStartALoopOnRelease ()
    {
        // With "Loop switch fires on press = Off" the loop tap waits for the release - which used to arrive after the
        // menu had turned SW 1 into a loop switch, and started recording
        this.loopTapOnPress = false;
        this.controller.perform (Action.MODE_MIX);
        this.openMenu ();
        this.tap (SW_1);

        assertEquals (Mode.LOOP, this.controller.getMode ());
        assertTrue (this.ran.isEmpty (), "no loop was touched: " + this.ran);
    }


    @Test
    void aLoopPressFinishesAsALoopPressWhenTheModeChangesUnderIt ()
    {
        // Hold to record: the release closes the loop, even if a jack switched the mode meanwhile
        this.loopTapOnPress = true;
        final TapHoldCommand sw1 = this.command (SW_1);
        sw1.execute (ButtonEvent.DOWN, 127);
        this.controller.perform (Action.MODE_MIX);
        sw1.execute (ButtonEvent.UP, 0);

        assertEquals (List.of ("loop tap 1", "loop release 1"), this.ran);
    }


    @Test
    void aMomentaryHoldEndsEvenWhenTheModeChangesUnderIt ()
    {
        this.controller.perform (Action.MODE_FX);
        final TapHoldCommand swA = this.command (SW_A);
        swA.execute (ButtonEvent.DOWN, 127);
        swA.execute (ButtonEvent.LONG, 127);
        this.controller.perform (Action.MODE_LOOP);
        swA.execute (ButtonEvent.UP, 0);

        assertEquals (List.of ("FX_1", "FX_1"), this.ran, "FX 1 went on for the hold and off again on release");
    }


    @Test
    void aSwitchPressedAfterTheModeChangeBelongsToTheNewMode ()
    {
        this.controller.perform (Action.MODE_MIX);
        this.openMenu ();
        this.tap (SW_1);
        this.ran.clear ();

        // The next press is a fresh one: SW 1 is a loop switch now
        this.loopTapOnPress = true;
        this.tap (SW_1);
        assertEquals (List.of ("loop tap 1", "loop release 1"), this.ran);
        assertFalse (this.controller.isMenuOpen ());
    }


    @Test
    void comingBackToTheBitwigPresetKeepsTheMode ()
    {
        // CC 119 = 127 arrives whenever D1 is selected or the Pacer starts: repaint, but stay where you were
        this.controller.perform (Action.MODE_SONG);
        this.controller.presetAnnounced (127);
        assertEquals (Mode.SONG, this.controller.getMode ());
    }


    @Test
    void theRetiredFxPresetStillSelectsTheFxMode ()
    {
        final int [] listened = new int [1];
        this.controller.setModeListener ( () -> listened[0]++);
        this.controller.presetAnnounced (17);
        assertEquals (Mode.FX, this.controller.getMode ());
        assertEquals (1, listened[0], "a real mode change re-points the pedals");
    }


    @Test
    void startingUpPointsThePedalsAtTheStartupMode ()
    {
        // The pedals are bound during init, while the mode is still the default
        final int [] listened = new int [1];
        this.controller.setModeListener ( () -> listened[0]++);
        this.controller.applyStartupMode ();
        assertEquals (1, listened[0]);
    }


    @Test
    void aJackWhoseTapIsALoopTrackIsALoopSwitch ()
    {
        this.setJackTap (0, Action.LOOP_2);
        this.loopTapOnPress = true;
        final TapHoldCommand fs1 = this.jack (0);
        fs1.execute (ButtonEvent.DOWN, 127);
        fs1.execute (ButtonEvent.LONG, 127);
        fs1.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("loop tap 2", "loop hold 2", "loop release 2"), this.ran, "tap, the looper's hold, and the release that closes hold-to-record");
    }


    @Test
    void aJackForALoopTrackThereIsNoneOfDoesNothing ()
    {
        // Loop tracks defaults to 4
        this.setJackTap (0, Action.LOOP_6);
        final TapHoldCommand fs1 = this.jack (0);
        fs1.execute (ButtonEvent.DOWN, 127);
        fs1.execute (ButtonEvent.UP, 0);
        assertEquals (List.of ("LOOP_6"), this.ran, "it runs as a plain action, which the looper ignores for a track it lacks");
    }


    @Test
    void theCustomLayoutChangesTheLooperInPlace ()
    {
        final var custom = this.configuration.getCustomBoard ();
        custom.setTarget (dev.pacer4bitwig.pacer.mode.CustomTarget.LOOP);
        custom.setTap (9, Action.TAP_TEMPO);
        custom.rebuild ();

        this.loopTapOnPress = true;
        this.tap (9);
        this.tap (SW_1);
        assertEquals (List.of ("TAP_TEMPO", "loop tap 1", "loop release 1"), this.ran, "SW D changed, SW 1 is still a loop switch");
    }


    @Test
    void pointingTheCustomLayoutAtABuiltInModeLeavesTheCustomMode ()
    {
        this.openMenu ();
        this.tap (4);
        assertEquals (Mode.CUSTOM, this.controller.getMode ());

        final var custom = this.configuration.getCustomBoard ();
        custom.setTarget (dev.pacer4bitwig.pacer.mode.CustomTarget.FX);
        custom.rebuild ();
        this.controller.customLayoutChanged ();
        assertEquals (Mode.FX, this.controller.getMode (), "the custom slot is dark now, so its layout moved to FX");

        // And the menu no longer offers the custom slot
        this.openMenu ();
        this.tap (4);
        assertEquals (Mode.FX, this.controller.getMode ());
        assertTrue (this.controller.isMenuOpen (), "a dark slot does nothing, not even close the menu");
    }


    @Test
    void doubleTappingSw6LatchesTheShiftLayerWithoutChangingTheMode ()
    {
        this.controller.perform (Action.MODE_MIX);
        final TapHoldCommand sw6 = this.command (SW_6);
        tap (sw6);
        assertEquals (Mode.MIX, this.controller.getMode (), "the tap waits for a possible second one");
        tap (sw6);
        this.runLater ();

        assertEquals (Mode.MIX, this.controller.getMode (), "the first tap of a double-tap never ran");
        assertEquals (ShiftLayer.State.ON, this.controller.getShiftState ());
    }


    @Test
    void aSingleSw6TapStillTogglesTheModeOnceTheWindowHasPassed ()
    {
        this.controller.perform (Action.MODE_MIX);
        this.tap (SW_6);
        this.runLater ();
        assertEquals (Mode.LOOP, this.controller.getMode ());
        assertEquals (ShiftLayer.State.OFF, this.controller.getShiftState ());
    }


    @Test
    void withoutASw6DoubleTapItsTapIsInstant ()
    {
        this.setField ("modeSwitchDoubleTap", Action.NONE);
        this.controller.perform (Action.MODE_MIX);
        this.tap (SW_6);
        assertEquals (Mode.LOOP, this.controller.getMode (), "no waiting when there is nothing to wait for");
    }


    @Test
    void sw6DoubleTapCanRunAnyAction ()
    {
        this.setField ("modeSwitchDoubleTap", Action.MODE_SONG);
        final TapHoldCommand sw6 = this.command (SW_6);
        tap (sw6);
        tap (sw6);
        this.runLater ();
        assertEquals (Mode.SONG, this.controller.getMode ());
    }


    @Test
    void shiftedLoopSwitchesRunLoopTracks5To8 ()
    {
        this.setField ("loopTrackCount", Integer.valueOf (8));
        this.loopTapOnPress = true;
        this.controller.perform (Action.SHIFT_TOGGLE);
        this.tap (SW_1);
        this.controller.perform (Action.SHIFT_TOGGLE);
        this.tap (SW_1);
        assertEquals (List.of ("loop tap 5", "loop release 5", "loop tap 1", "loop release 1"), this.ran);
    }


    @Test
    void aShiftedLoopSwitchBeyondTheProjectsLoopTracksDoesNothing ()
    {
        // Loop tracks defaults to 4
        this.loopTapOnPress = true;
        this.controller.perform (Action.SHIFT_TOGGLE);
        this.tap (SW_1);
        assertTrue (this.ran.isEmpty (), "loop track 5 does not exist: " + this.ran);
    }


    @Test
    void aLayerForOnePressIsUsedUpByTheNextSwitch ()
    {
        this.controller.perform (Action.SHIFT_ONCE);
        this.tap (SW_D);
        assertEquals (ShiftLayer.State.OFF, this.controller.getShiftState ());
        this.tap (SW_D);
        assertEquals (List.of ("TRANSPORT_PLAY_STOP", "LAUNCHER_OVERDUB"), this.ran, "shifted once, then SW D's own tap");
    }


    @Test
    void aJackHoldsTheLayerUpWhileItIsDown ()
    {
        this.setJackTap (0, Action.SHIFT_HOLD);
        final TapHoldCommand fs1 = this.jack (0);
        fs1.execute (ButtonEvent.DOWN, 127);
        assertEquals (ShiftLayer.State.HELD, this.controller.getShiftState ());
        this.tap (SW_D);
        fs1.execute (ButtonEvent.UP, 0);
        assertEquals (ShiftLayer.State.OFF, this.controller.getShiftState ());
        this.tap (SW_D);
        assertEquals (List.of ("TRANSPORT_PLAY_STOP", "LAUNCHER_OVERDUB"), this.ran);
    }


    @Test
    void aShiftKeyInTheCustomLayoutTakesTheLayerDownAgain ()
    {
        final var custom = this.configuration.getCustomBoard ();
        custom.setTarget (dev.pacer4bitwig.pacer.mode.CustomTarget.LOOP);
        custom.setTap (SW_5, Action.SHIFT_TOGGLE);
        custom.rebuild ();
        this.tap (SW_5);
        assertEquals (ShiftLayer.State.ON, this.controller.getShiftState ());
        this.tap (SW_5);
        assertEquals (ShiftLayer.State.OFF, this.controller.getShiftState (), "not the Looper's shifted SW 5 (mute all)");
        assertTrue (this.ran.isEmpty (), "nothing else ran: " + this.ran);
    }


    @Test
    void aSwitchWithNothingOnItsShiftLayerKeepsItsJob ()
    {
        this.controller.perform (Action.MODE_MIX);
        this.controller.perform (Action.SHIFT_TOGGLE);
        this.tap (SW_5);
        assertEquals (List.of ("SELECT_NEXT_LOOP"), this.ran, "the Mixer has no shift layer");
    }


    @Test
    void changingTheModeTakesTheLayerDown ()
    {
        this.controller.perform (Action.SHIFT_TOGGLE);
        this.controller.perform (Action.MODE_FX);
        assertEquals (ShiftLayer.State.OFF, this.controller.getShiftState ());
    }


    @Test
    void sw6ShowsTheShiftLayer ()
    {
        final int white = dev.pacer4bitwig.pacer.led.LedColour.WHITE.ordinal ();
        final int amber = dev.pacer4bitwig.pacer.led.LedColour.AMBER.ordinal ();
        assertEquals (white, this.controller.getLedCode (SW_6));
        this.controller.perform (Action.SHIFT_TOGGLE);
        assertEquals (amber, this.controller.getLedCode (SW_6));
    }


    @Test
    void goingToTheCustomModeFollowsTheLayoutToTheModeItChanges ()
    {
        this.controller.perform (Action.MODE_CUSTOM);
        assertEquals (Mode.CUSTOM, this.controller.getMode ());

        final var custom = this.configuration.getCustomBoard ();
        custom.setTarget (dev.pacer4bitwig.pacer.mode.CustomTarget.SONG);
        custom.rebuild ();
        this.controller.perform (Action.MODE_LOOP);
        this.controller.perform (Action.MODE_CUSTOM);
        assertEquals (Mode.SONG, this.controller.getMode ());
    }


    @Test
    void anEventWordTakesTheDisplayForAMoment ()
    {
        this.controller.paint ();
        this.sysex.clear ();
        this.controller.showEvent ("REC 2");
        this.controller.paint ();
        assertEquals (List.of (dev.pacer4bitwig.pacer.live.PacerSysex.name ("REC 2")), this.sysex);
    }


    @Test
    void eventWordsCanBeSwitchedOff ()
    {
        this.setField ("showEvents", Boolean.FALSE);
        this.controller.paint ();
        this.sysex.clear ();
        this.controller.showEvent ("UNDO");
        this.controller.paint ();
        assertTrue (this.sysex.isEmpty (), "the display keeps the name: " + this.sysex);
    }


    @Test
    void raisingTheShiftLayerSaysSo ()
    {
        this.controller.paint ();
        this.sysex.clear ();
        this.controller.perform (Action.SHIFT_TOGGLE);
        this.controller.paint ();
        assertTrue (this.sysex.contains (dev.pacer4bitwig.pacer.live.PacerSysex.name ("SHIFT")), "SHIFT on the display");
    }


    @Test
    void onlyMovingLedsNeedRegularFlushes ()
    {
        for (int i = 0; i < 10; i++)
            this.controller.getLedCode (i);
        assertFalse (this.controller.isAnimating (), "a steady board");

        // A shift layer for one press blinks SW 6
        this.controller.perform (Action.SHIFT_ONCE);
        this.controller.getLedCode (SW_6);
        assertTrue (this.controller.isAnimating ());
    }


    @Test
    void pressesAndActionsCanBeLogged ()
    {
        final List<String> lines = new ArrayList<> ();
        this.controller.setDiagnostics (new dev.pacer4bitwig.util.Diagnostics (lines::add, () -> dev.pacer4bitwig.util.Diagnostics.Level.ACTIONS));
        this.tap (SW_5);
        assertEquals (List.of ("PACER SW 5 down: ACTION on LOOP", "PACER SW 5 up", "PACER SW 5 tap", "PACER action UNDO (SW 5)"), lines);
    }


    private static void tap (final TapHoldCommand command)
    {
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);
    }


    @Test
    void aPickedUpPedalLeavesItsMidiTargetAloneUntilItGetsThere ()
    {
        this.setField ("pedalTakeover", dev.pacer4bitwig.pacer.looper.PedalTakeover.PICKUP);
        this.setPedalTarget (Mode.LOOP, 0, dev.pacer4bitwig.pacer.looper.ExpressionTarget.MIDI_MOD_WHEEL);
        final List<Integer> sent = new ArrayList<> ();
        this.controller.setMidiSender ( (status, data1, data2) -> sent.add (Integer.valueOf (data2)));

        this.controller.pedalMoved (0, 64);
        assertEquals (List.of (Integer.valueOf (64)), sent, "nothing was sent before, so it follows at once");

        // Pointed somewhere else and back (a mode change): the heel no longer yanks the mod wheel down
        this.controller.pedalRetargeted (0);
        this.controller.pedalMoved (0, 10);
        this.controller.pedalMoved (0, 40);
        assertEquals (1, sent.size (), "still below where the mod wheel was left");
        this.controller.pedalMoved (0, 70);
        this.controller.pedalMoved (0, 30);
        assertEquals (List.of (Integer.valueOf (64), Integer.valueOf (70), Integer.valueOf (30)), sent, "passed 64, and follows from then on");
    }


    @Test
    void aJumpingPedalAlwaysFollows ()
    {
        this.setPedalTarget (Mode.LOOP, 0, dev.pacer4bitwig.pacer.looper.ExpressionTarget.MIDI_MOD_WHEEL);
        final List<Integer> sent = new ArrayList<> ();
        this.controller.setMidiSender ( (status, data1, data2) -> sent.add (Integer.valueOf (data2)));
        this.controller.pedalMoved (0, 64);
        this.controller.pedalRetargeted (0);
        this.controller.pedalMoved (0, 10);
        assertEquals (List.of (Integer.valueOf (64), Integer.valueOf (10)), sent);
    }


    private void setPedalTarget (final Mode mode, final int index, final dev.pacer4bitwig.pacer.looper.ExpressionTarget target)
    {
        try
        {
            final java.lang.reflect.Field field = PacerConfiguration.class.getDeclaredField ("expressionTargets");
            field.setAccessible (true);
            ((dev.pacer4bitwig.pacer.looper.ExpressionTarget [] []) field.get (this.configuration))[mode.ordinal ()][index] = target;
        }
        catch (final ReflectiveOperationException ex)
        {
            throw new IllegalStateException (ex);
        }
    }


    private void runLater ()
    {
        final List<Runnable> due = new ArrayList<> (this.later);
        this.later.clear ();
        due.forEach (Runnable::run);
    }


    private void setField (final String name, final Object value)
    {
        try
        {
            final java.lang.reflect.Field field = PacerConfiguration.class.getDeclaredField (name);
            field.setAccessible (true);
            field.set (this.configuration, value);
        }
        catch (final ReflectiveOperationException ex)
        {
            throw new IllegalStateException (ex);
        }
    }


    private void setJackTap (final int index, final Action action)
    {
        try
        {
            final java.lang.reflect.Field field = PacerConfiguration.class.getDeclaredField ("footswitchTap");
            field.setAccessible (true);
            ((Action []) field.get (this.configuration))[index] = action;
        }
        catch (final ReflectiveOperationException ex)
        {
            throw new IllegalStateException (ex);
        }
    }


    /** Wired exactly like the jacks in {@code PacerControllerSetup.registerTriggerCommands}. */
    private TapHoldCommand jack (final int index)
    {
        final PacerController c = this.controller;
        return new TapHoldCommand ( () -> c.isFootswitchTapOnPress (index), () -> c.footswitchTap (index), () -> c.footswitchHold (index), () -> c.footswitchRelease (index), null, () -> c.getFootswitchExtraHoldMillis (index), (task, delay) -> task.run ()).withPress ( () -> c.footswitchPress (index)).withDoubleTap ( () -> c.footswitchDoubleTap (index), () -> c.isFootswitchDoubleTapEnabled (index), () -> 350, System::currentTimeMillis);
    }


    private void openMenu ()
    {
        this.hold (SW_6);
        assertTrue (this.controller.isMenuOpen ());
    }


    private void tap (final int switchIndex)
    {
        final TapHoldCommand command = this.command (switchIndex);
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);
    }


    private void hold (final int switchIndex)
    {
        final TapHoldCommand command = this.command (switchIndex);
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.LONG, 127);
        // Let every delayed hold (clearing actions wait longer) come due while the foot is still down
        final List<Runnable> due = new ArrayList<> (this.later);
        this.later.clear ();
        due.forEach (Runnable::run);
        command.execute (ButtonEvent.UP, 0);
    }


    /** Wired exactly like {@code PacerControllerSetup.registerTriggerCommands}. */
    private TapHoldCommand command (final int index)
    {
        final PacerController c = this.controller;
        return new TapHoldCommand ( () -> c.isTapOnPress (index), () -> c.tap (index), () -> c.hold (index), () -> c.release (index), null, () -> c.getExtraHoldMillis (index), (task, delay) -> this.later.add (task)).withPress ( () -> c.press (index)).withDoubleTap ( () -> c.doubleTap (index), () -> c.isDoubleTapEnabled (index), () -> 350, System::currentTimeMillis).withDelayedTap ( () -> Mode.isModeSwitch (index));
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == int.class)
            return Integer.valueOf (0);
        if (type == long.class)
            return Long.valueOf (0);
        if (type == double.class)
            return Double.valueOf (0);
        if (type == float.class)
            return Float.valueOf (0);
        return null;
    }
}
