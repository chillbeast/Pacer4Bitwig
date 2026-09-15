// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.command;

import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.util.function.BooleanSupplier;


/**
 * A switch with a tap and a hold action. The tap fires either on press (tight timing; a later hold then fires as
 * well) or on release (only if the switch was not held).
 */
public class TapHoldCommand implements TriggerCommand
{
    private final BooleanSupplier tapOnPress;
    private final Runnable        tap;
    private final Runnable        hold;
    private final Runnable        afterEvent;
    private boolean               holdFired;


    /**
     * Constructor.
     *
     * @param tapOnPress True to fire the tap on press, false on release
     * @param tap The tap action
     * @param hold The hold action, may be null
     * @param afterEvent Called after every press/release/hold, may be null
     */
    public TapHoldCommand (final BooleanSupplier tapOnPress, final Runnable tap, final Runnable hold, final Runnable afterEvent)
    {
        this.tapOnPress = tapOnPress;
        this.tap = tap;
        this.hold = hold;
        this.afterEvent = afterEvent;
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        if (event == ButtonEvent.DOWN)
        {
            this.holdFired = false;
            if (this.tapOnPress.getAsBoolean ())
                this.tap.run ();
        }
        else if (event == ButtonEvent.LONG)
        {
            if (this.hold != null)
            {
                this.holdFired = true;
                this.hold.run ();
            }
        }
        else if (event == ButtonEvent.UP)
        {
            if (!this.holdFired && !this.tapOnPress.getAsBoolean ())
                this.tap.run ();
            this.holdFired = false;
        }

        if (this.afterEvent != null)
            this.afterEvent.run ();
    }
}
