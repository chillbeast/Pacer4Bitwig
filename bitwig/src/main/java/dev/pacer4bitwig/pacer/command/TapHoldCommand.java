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
 * double-tap action instead of a second tap. The one exception is opt-in ({@link #withDelayedTap(BooleanSupplier)}):
 * a switch whose tap must not run at all when it turns out to be a double-tap - SW 6, whose tap changes the mode -
 * waits out the window first.</li>
 * <li>A hold can require the switch to stay down for longer than the framework's hold time, which protects
 * destructive actions. An optional release action runs on every release.</li>
 * <li>Whether the tap fires on press or on release is decided once, when the switch goes down: what the switch does
 * can change while it is held (a mode change), and the release must finish the press that started.</li>
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

    private Runnable              press;
    private Runnable              doubleTap;
    private BooleanSupplier       doubleTapEnabled = () -> false;
    private LongSupplier          doubleTapWindow  = () -> 0;
    private LongSupplier          clock            = System::currentTimeMillis;
    private BooleanSupplier       delayTap         = () -> false;

    private boolean               holdSeen;
    private boolean               pressed;
    private boolean               tapFiredOnPress;
    private int                   pressGeneration;
    private long                  lastTapAt        = NO_TAP;
    /** Bumped whenever a waiting tap is settled, so the scheduled one knows it is stale. */
    private int                   tapGeneration;
    private boolean               tapWaiting;


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
     * Add a press action, which runs first on every press - before the tap logic asks anything - so the caller can
     * latch what this press is for.
     *
     * @param action The action
     * @return This command
     */
    public TapHoldCommand withPress (final Runnable action)
    {
        this.press = action;
        return this;
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


    /**
     * Let the tap wait for the double-tap window, so a double-tap runs only the double-tap action. Only while a
     * double-tap is enabled; costs the tap the length of the window.
     *
     * @param delay True while the tap should wait
     * @return This command
     */
    public TapHoldCommand withDelayedTap (final BooleanSupplier delay)
    {
        this.delayTap = delay;
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
            if (this.press != null)
                this.press.run ();
            this.tapFiredOnPress = this.tapOnPress.getAsBoolean ();
            if (this.tapFiredOnPress)
                this.fireTap ();
        }
        else if (event == ButtonEvent.LONG)
        {
            // A hold is never the first half of a double-tap. A tap still waiting for one runs now, before the
            // hold, as it would have without the wait.
            this.lastTapAt = NO_TAP;
            this.runWaitingTap ();
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
            if (!this.holdSeen && !this.tapFiredOnPress)
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
            final long window = this.doubleTapWindow.getAsLong ();
            if (this.lastTapAt != NO_TAP && now - this.lastTapAt <= window)
            {
                this.lastTapAt = NO_TAP;
                // The first tap never runs
                this.tapWaiting = false;
                this.tapGeneration++;
                this.doubleTap.run ();
                return;
            }
            // A tap still waiting (its timer is late) is not lost to the next one
            this.runWaitingTap ();
            this.lastTapAt = now;
            if (this.delayTap.getAsBoolean ())
            {
                this.tapWaiting = true;
                final int generation = ++this.tapGeneration;
                this.scheduler.schedule ( () -> {
                    if (this.tapGeneration == generation)
                        this.runWaitingTap ();
                }, window);
                return;
            }
        }
        this.tap.run ();
    }


    private void runWaitingTap ()
    {
        if (!this.tapWaiting)
            return;
        this.tapWaiting = false;
        this.tapGeneration++;
        this.tap.run ();
        if (this.afterEvent != null)
            this.afterEvent.run ();
    }
}
