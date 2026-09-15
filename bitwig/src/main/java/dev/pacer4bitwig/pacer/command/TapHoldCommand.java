// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.command;

import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;


/**
 * A switch with a tap and a hold action. The tap fires either on press (tight timing; a later hold then fires as
 * well) or on release (only if the switch was not held). A hold can require the switch to stay down for longer than
 * the framework's hold time, which protects destructive actions.
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


    private final BooleanSupplier tapOnPress;
    private final Runnable        tap;
    private final Runnable        hold;
    private final Runnable        afterEvent;
    private final LongSupplier    extraHoldMillis;
    private final Scheduler       scheduler;

    private boolean               holdSeen;
    private boolean               pressed;
    private int                   pressGeneration;


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
        this (tapOnPress, tap, hold, afterEvent, () -> 0, (task, delay) -> task.run ());
    }


    /**
     * Constructor.
     *
     * @param tapOnPress True to fire the tap on press, false on release
     * @param tap The tap action
     * @param hold The hold action, may be null
     * @param afterEvent Called after every press/release/hold, may be null
     * @param extraHoldMillis How much longer than the framework's hold the switch must stay down
     * @param scheduler Runs the delayed hold check
     */
    public TapHoldCommand (final BooleanSupplier tapOnPress, final Runnable tap, final Runnable hold, final Runnable afterEvent, final LongSupplier extraHoldMillis, final Scheduler scheduler)
    {
        this.tapOnPress = tapOnPress;
        this.tap = tap;
        this.hold = hold;
        this.afterEvent = afterEvent;
        this.extraHoldMillis = extraHoldMillis;
        this.scheduler = scheduler;
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
                this.tap.run ();
        }
        else if (event == ButtonEvent.LONG)
        {
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
            if (!this.holdSeen && !this.tapOnPress.getAsBoolean ())
                this.tap.run ();
            this.holdSeen = false;
        }

        if (this.afterEvent != null)
            this.afterEvent.run ();
    }
}
