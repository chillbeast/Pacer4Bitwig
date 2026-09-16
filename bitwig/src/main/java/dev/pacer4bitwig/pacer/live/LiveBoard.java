// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import dev.pacer4bitwig.pacer.controller.PacerMap;

import java.util.Arrays;


/**
 * What the Pacer is currently showing, and the only place that writes it. Every message is a RAM-only live edit
 * (see {@link PacerSysex}), so the device's stored presets are never touched.
 * <p>
 * The Pacer flashes {@code LOAD SYS} on its display for as long as SysEx keeps arriving: a burst reads as one brief
 * flash, but a trickle of a few messages per second holds it there and hides the mode name. So this class remembers
 * what it has already written and sends nothing when a switch would not change - callers may repaint as often as
 * they like. Throughput itself is not a constraint (measured at ~3300 messages/s on the hardware).
 */
public final class LiveBoard
{
    /** Sends one SysEx message, as a hex string. */
    @FunctionalInterface
    public interface SysexSender
    {
        /**
         * @param hex The message
         */
        void send (String hex);
    }


    /** No switch can produce this, so the first write of each switch always goes out. */
    private static final int  UNKNOWN = -1;

    private final SysexSender sender;
    /** What each switch was last told to show, as a cheap key - painting runs often, so it must not build strings. */
    private final int []      lastLed = new int [PacerMap.NUM_SWITCHES];
    private String            lastName;
    private int               written;


    /**
     * Constructor.
     *
     * @param sender Sends SysEx to the Pacer
     */
    public LiveBoard (final SysexSender sender)
    {
        this.sender = sender;
        Arrays.fill (this.lastLed, UNKNOWN);
    }


    /**
     * Show a colour pair on a switch, on the given row. The CC echo then picks the on colour for 127 and the off
     * colour for 0.
     *
     * @param switchIndex 0-9
     * @param on The colour while the switch is on
     * @param off The colour while it is off
     * @param row Which of the switch's LEDs lights
     * @return True if a message was sent
     */
    public boolean setLed (final int switchIndex, final PacerColour on, final PacerColour off, final LedRow row)
    {
        return this.setLed (switchIndex, on, false, off, false, row);
    }


    /**
     * Show a colour pair on a switch, choosing the dimmed variants.
     *
     * @param switchIndex 0-9
     * @param on The colour while the switch is on
     * @param onDimmed True for the dimmed variant of the on colour
     * @param off The colour while it is off
     * @param offDimmed True for the dimmed variant of the off colour
     * @param row Which of the switch's LEDs lights
     * @return True if a message was sent
     */
    public boolean setLed (final int switchIndex, final PacerColour on, final boolean onDimmed, final PacerColour off, final boolean offDimmed, final LedRow row)
    {
        final int key = (on.getValue (onDimmed) << 16) | (off.getValue (offDimmed) << 8) | row.getNumber ();
        if (key == this.lastLed[switchIndex])
            return false;
        this.lastLed[switchIndex] = key;
        this.send (PacerSysex.led (switchIndex, on, onDimmed, off, offDimmed, row));
        return true;
    }


    /**
     * Write the display name. A switch press replaces it with that switch's CC readout, so callers that want the
     * name to stay up call {@link #repeatName()} when a CC arrives.
     *
     * @param name The name, padded or cut to five characters
     * @return True if a message was sent
     */
    public boolean setName (final String name)
    {
        final String padded = PacerSysex.pad (name);
        if (padded.equals (this.lastName))
            return false;
        this.lastName = padded;
        this.send (PacerSysex.name (padded));
        return true;
    }


    /**
     * Write the current name again, after something else took over the display.
     *
     * @return True if a message was sent
     */
    public boolean repeatName ()
    {
        if (this.lastName == null)
            return false;
        this.send (PacerSysex.name (this.lastName));
        return true;
    }


    /**
     * Darken every switch and say so on the display, so a board the extension is no longer driving does not look
     * live. Selecting any preset on the Pacer brings its own colours back.
     *
     * @param name What to show on the display
     */
    public void blackout (final String name)
    {
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
            this.setLed (i, PacerColour.OFF, PacerColour.OFF, LedRow.STRIP);
        this.setName (name);
    }


    /**
     * Forget what the Pacer shows, so the next paint writes everything again. Needed whenever the device may have
     * reloaded its stored preset - selecting a preset discards every live edit.
     */
    public void invalidate ()
    {
        Arrays.fill (this.lastLed, UNKNOWN);
        this.lastName = null;
    }


    /**
     * @return How many messages have been sent, for tests and diagnostics
     */
    public int getMessageCount ()
    {
        return this.written;
    }


    private void send (final String message)
    {
        this.written++;
        this.sender.send (message);
    }
}
