// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.command;

import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;


/**
 * A switch with tap, double-tap and hold actions.
 * <ul>
 * <li>The tap fires either on press (tight timing; a later hold then fires as well) or on release (only if the switch
 * was not held).</li>
 * <li>A double-tap never delays the tap: the first tap runs as usual, and a second tap within the window runs the
 * double-tap action instead of a second tap.</li>
 * <li>A hold can require the switch to stay down for longer than the framework's hold time, which protects
 * destructive actions. An optional release action runs on every release.</li>
 * </ul>
 */
public class TapHoldCommand implements TriggerCommand
{
    /** Runs a task later. */
    @FunctionalInterface
    public interface Scheduler
    {
        /**
         * @param task The task
         * @param delayMillis The delay
         */
        void schedule (Runnable task, long delayMillis);
    }


    private static final long     NO_TAP           = Long.MIN_VALUE;

    private final BooleanSupplier tapOnPress;
    private final Runnable        tap;
    private final Runnable        hold;
    private final Runnable        release;
    private final Runnable        afterEvent;
    private final LongSupplier    extraHoldMillis;
    private final Scheduler       scheduler;

    private Runnable              doubleTap;
    private BooleanSupplier       doubleTapEnabled = () -> false;
    private LongSupplier          doubleTapWindow  = () -> 0;
    private LongSupplier          clock            = System::currentTimeMillis;

    private boolean               holdSeen;
    private boolean               pressed;
    private int                   pressGeneration;
    private long                  lastTapAt        = NO_TAP;


    /**
     * Constructor for holds without extra delay.
     *
     * @param tapOnPress True to fire the tap on press, false on release
     * @param tap The tap action
     * @param hold The hold action, may be null
     * @param afterEvent Called after every press/release/hold, may be null
     */
    public TapHoldCommand (final BooleanSupplier tapOnPress, final Runnable tap, final Runnable hold, final Runnable afterEvent)
    {
        this (tapOnPress, tap, hold, null, afterEvent, () -> 0, (task, delay) -> task.run ());
    }


    /**
     * Constructor.
     *
     * @param tapOnPress True to fire the tap on press, false on release
     * @param tap The tap action
     * @param hold The hold action, may be null
     * @param release Runs on every release before the tap logic, may be null
     * @param afterEvent Called after every press/release/hold, may be null
     * @param extraHoldMillis How much longer than the framework's hold the switch must stay down
     * @param scheduler Runs the delayed hold check
     */
    public TapHoldCommand (final BooleanSupplier tapOnPress, final Runnable tap, final Runnable hold, final Runnable release, final Runnable afterEvent, final LongSupplier extraHoldMillis, final Scheduler scheduler)
    {
        this.tapOnPress = tapOnPress;
        this.tap = tap;
        this.hold = hold;
        this.release = release;
        this.afterEvent = afterEvent;
        this.extraHoldMillis = extraHoldMillis;
        this.scheduler = scheduler;
    }


    /**
     * Add a double-tap action.
     *
     * @param action Runs instead of the second of two quick taps
     * @param enabled True while a double-tap action is assigned
     * @param windowMillis How quickly the second tap must follow
     * @param clockMillis The time source
     * @return This command
     */
    public TapHoldCommand withDoubleTap (final Runnable action, final BooleanSupplier enabled, final LongSupplier windowMillis, final LongSupplier clockMillis)
    {
        this.doubleTap = action;
        this.doubleTapEnabled = enabled;
        this.doubleTapWindow = windowMillis;
        this.clock = clockMillis;
        return this;
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        if (event == ButtonEvent.DOWN)
        {
            this.pressed = true;
            this.pressGeneration++;
            this.holdSeen = false;
            if (this.tapOnPress.getAsBoolean ())
                this.fireTap ();
        }
        else if (event == ButtonEvent.LONG)
        {
            // A hold is never the first half of a double-tap
            this.lastTapAt = NO_TAP;
            if (this.hold != null)
            {
                // A hold, even one released before its extra time, never also fires a tap on release
                this.holdSeen = true;
                final long extra = this.extraHoldMillis.getAsLong ();
                if (extra <= 0)
                    this.hold.run ();
                else
                {
                    final int generation = this.pressGeneration;
                    this.scheduler.schedule ( () -> {
                        if (this.pressed && this.pressGeneration == generation)
                        {
                            this.hold.run ();
                            if (this.afterEvent != null)
                                this.afterEvent.run ();
                        }
                    }, extra);
                }
            }
        }
        else if (event == ButtonEvent.UP)
        {
            this.pressed = false;
            if (this.release != null)
                this.release.run ();
            if (!this.holdSeen && !this.tapOnPress.getAsBoolean ())
                this.fireTap ();
            this.holdSeen = false;
        }

        if (this.afterEvent != null)
            this.afterEvent.run ();
    }


    private void fireTap ()
    {
        if (this.doubleTap != null && this.doubleTapEnabled.getAsBoolean ())
        {
            final long now = this.clock.getAsLong ();
            if (this.lastTapAt != NO_TAP && now - this.lastTapAt <= this.doubleTapWindow.getAsLong ())
            {
                this.lastTapAt = NO_TAP;
                this.doubleTap.run ();
                return;
            }
            this.lastTapAt = now;
        }
        this.tap.run ();
    }
}
