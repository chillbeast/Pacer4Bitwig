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
    private static final int         SW_A   = 6;

    private final List<String>       ran    = new ArrayList<> ();
    private final List<Runnable>     later  = new ArrayList<> ();
    private boolean                  loopTapOnPress;
    private PacerController          controller;


    @BeforeEach
    void setUp ()
    {
        final IHost host = (IHost) Proxy.newProxyInstance (IHost.class.getClassLoader (), new Class<?> []
        {
            IHost.class
        }, (proxy, method, args) -> defaultValue (method.getReturnType ()));
        final PacerConfiguration configuration = new PacerConfiguration (host, null, Collections.emptyList ());

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
        };

        this.controller = new PacerController (host, configuration, looper, fx, new LiveBoard (hex -> {
            // The Pacer is not here
        }));
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
        return new TapHoldCommand ( () -> c.isTapOnPress (index), () -> c.tap (index), () -> c.hold (index), () -> c.release (index), null, () -> c.getExtraHoldMillis (index), (task, delay) -> this.later.add (task)).withPress ( () -> c.press (index)).withDoubleTap ( () -> c.doubleTap (index), () -> c.isDoubleTapEnabled (index), () -> 350, System::currentTimeMillis);
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
