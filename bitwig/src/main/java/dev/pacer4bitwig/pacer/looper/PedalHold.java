// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import java.util.function.IntUnaryOperator;


/**
 * What one expression pedal is currently holding on a receiver, so it can be handed back.
 * <p>
 * The extension used to compute a MIDI message and fire it, remembering nothing. So when the pedal stopped
 * addressing that controller - a mode change, a preset selected on the Pacer, an edited setting, exit - whatever it
 * had last sent stayed on the instrument for good. On a synth whose mod matrix routes the mod wheel to the filter,
 * that reads as "the cutoff knob stopped working", and only a power cycle or a factory reset clears it.
 * <p>
 * Two halves, and both are needed. {@link #release()} hands the controller back at its neutral, on the channel it
 * actually went out on. {@link #reassert} then re-states where the foot really is once the pedal picks up a new
 * MIDI target, because a release on its own leaves the receiver at neutral while the pedal sits at the toe - and
 * the next nudge would jump. Nothing is ever invented: only a position that was really observed, or the documented
 * neutral of a controller being handed back.
 * <p>
 * Controller thread only, one instance per jack.
 */
public final class PedalHold
{
    private ExpressionTarget held     = ExpressionTarget.NONE;
    private int              channel  = -1;
    /** The last raw pedal position seen, 0-127; -1 until the pedal has actually moved this session. */
    private int              position = -1;


    /**
     * Record a send and build its message.
     *
     * @param target What the pedal drives
     * @param midiChannel The channel to send on, 0-15
     * @param rawPosition The pedal position before the response curve, 0-127
     * @param mappedValue The value after the response curve, 0-127
     * @return Status, data 1, data 2; null if this target sends no MIDI
     */
    public int [] press (final ExpressionTarget target, final int midiChannel, final int rawPosition, final int mappedValue)
    {
        this.position = rawPosition;
        final int [] message = target.toMidi (mappedValue, midiChannel);
        if (message == null)
        {
            // A parameter or FX remote holds nothing on any receiver
            this.held = ExpressionTarget.NONE;
            this.channel = -1;
            return null;
        }
        this.held = target;
        this.channel = midiChannel;
        return message;
    }


    /**
     * @return What is held, {@link ExpressionTarget#NONE} if nothing
     */
    public ExpressionTarget getTarget ()
    {
        return this.held;
    }


    /**
     * Hand back whatever is held, at the target's resting value and on the channel it was sent on. Idempotent: a
     * second call returns null, so it is safe to call from several teardown paths.
     *
     * @return Status, data 1, data 2; null if nothing is held or the target has no neutral to hand back
     */
    public int [] release ()
    {
        final ExpressionTarget target = this.held;
        final int midiChannel = this.channel;
        this.held = ExpressionTarget.NONE;
        this.channel = -1;
        if (midiChannel < 0)
            return null;
        final int rest = target.restingValue ();
        if (rest == ExpressionTarget.NO_REST)
            return null;
        return target.toMidi (rest, midiChannel);
    }


    /**
     * State where the pedal physically is, under a target it has just picked up. Only ever re-sends a position that
     * was really observed - a pedal that has not moved this session asserts nothing.
     *
     * @param target What the pedal now drives
     * @param midiChannel The channel to send on, 0-15
     * @param map The new target's response curve, raw position to output value
     * @return Status, data 1, data 2; null if the pedal has never moved or this target sends no MIDI
     */
    public int [] reassert (final ExpressionTarget target, final int midiChannel, final IntUnaryOperator map)
    {
        if (this.position < 0)
            return null;
        return this.press (target, midiChannel, this.position, map.applyAsInt (this.position));
    }


    /**
     * Is what is held no longer what this pedal should be driving?
     *
     * @param target What the pedal drives now
     * @param midiChannel The channel it would send on
     * @return True if the hold needs releasing first
     */
    public boolean isStale (final ExpressionTarget target, final int midiChannel)
    {
        return this.channel >= 0 && (this.held != target || this.channel != midiChannel);
    }


    /**
     * @return True if a controller is currently being held on some receiver
     */
    public boolean isHolding ()
    {
        return this.channel >= 0;
    }
}
