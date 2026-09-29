// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * What an expression pedal controls: a Bitwig parameter (bound directly), a remote control of the FX preset's focused
 * instrument, a loop - one loop track, the loop being recorded, or all of them keeping their balance - or a MIDI
 * message sent into the extension's note input, which reaches the instruments of every track listening to it.
 */
public enum ExpressionTarget implements Labelled
{
    /** Nothing. */
    NONE ("Nothing", Kind.NONE, 0),
    /** Selected track volume. */
    SELECTED_VOLUME ("Selected track: volume", Kind.PARAMETER, 0),
    /** Selected track panning. */
    SELECTED_PAN ("Selected track: pan", Kind.PARAMETER, 0),
    /** Selected track send 1. */
    SELECTED_SEND_1 ("Selected track: send 1", Kind.PARAMETER, 0),
    /** Selected track send 2. */
    SELECTED_SEND_2 ("Selected track: send 2", Kind.PARAMETER, 0),
    /** Master volume. */
    MASTER_VOLUME ("Master volume", Kind.PARAMETER, 0),
    /** Remote control 1 of the selected device. */
    DEVICE_REMOTE_1 ("Selected device: remote control 1", Kind.PARAMETER, 0),
    /** Remote control 2 of the selected device. */
    DEVICE_REMOTE_2 ("Selected device: remote control 2", Kind.PARAMETER, 0),
    /** Project remote control 1. */
    PROJECT_REMOTE_1 ("Project remote control 1", Kind.PARAMETER, 0),
    /** Project remote control 2. */
    PROJECT_REMOTE_2 ("Project remote control 2", Kind.PARAMETER, 0),

    /** Volume of loop track 1 (the track bank position, so it follows the loop track window). */
    LOOP_1_VOLUME ("Loop track 1: volume", Kind.PARAMETER, 0),
    /** Volume of loop track 2. */
    LOOP_2_VOLUME ("Loop track 2: volume", Kind.PARAMETER, 1),
    /** Volume of loop track 3. */
    LOOP_3_VOLUME ("Loop track 3: volume", Kind.PARAMETER, 2),
    /** Volume of loop track 4. */
    LOOP_4_VOLUME ("Loop track 4: volume", Kind.PARAMETER, 3),
    /** Volume of loop track 5. */
    LOOP_5_VOLUME ("Loop track 5: volume", Kind.PARAMETER, 4),
    /** Volume of loop track 6. */
    LOOP_6_VOLUME ("Loop track 6: volume", Kind.PARAMETER, 5),
    /** Volume of loop track 7. */
    LOOP_7_VOLUME ("Loop track 7: volume", Kind.PARAMETER, 6),
    /** Volume of loop track 8. */
    LOOP_8_VOLUME ("Loop track 8: volume", Kind.PARAMETER, 7),
    /** Volume of the loop being recorded or waiting to record, else the one recorded last in this row. */
    ACTIVE_LOOP_VOLUME ("Loop being recorded (else the last recorded): volume", Kind.ACTIVE_LOOP, 0),
    /** All loop tracks at once, keeping their balance. */
    ALL_LOOPS_VOLUME ("All loop tracks: volume (keeps their balance)", Kind.ALL_LOOPS, 0),

    /** Remote control 1 of the FX preset's focused instrument ("Pacer" page). */
    FOCUSED_REMOTE_1 ("Focused instrument: remote control 1", Kind.FX_REMOTE, 0),
    /** Remote control 2 of the FX preset's focused instrument ("Pacer" page). */
    FOCUSED_REMOTE_2 ("Focused instrument: remote control 2", Kind.FX_REMOTE, 1),
    /** Remote control 3 of the FX preset's focused instrument ("Pacer" page). */
    FOCUSED_REMOTE_3 ("Focused instrument: remote control 3", Kind.FX_REMOTE, 2),
    /** Remote control 4 of the FX preset's focused instrument ("Pacer" page). */
    FOCUSED_REMOTE_4 ("Focused instrument: remote control 4", Kind.FX_REMOTE, 3),
    /** Remote control 5 of the FX preset's focused instrument ("Pacer" page). */
    FOCUSED_REMOTE_5 ("Focused instrument: remote control 5", Kind.FX_REMOTE, 4),
    /** Remote control 6 of the FX preset's focused instrument ("Pacer" page). */
    FOCUSED_REMOTE_6 ("Focused instrument: remote control 6", Kind.FX_REMOTE, 5),
    /** Remote control 7 of the FX preset's focused instrument ("Pacer" page). */
    FOCUSED_REMOTE_7 ("Focused instrument: remote control 7", Kind.FX_REMOTE, 6),
    /** Remote control 8 of the FX preset's focused instrument ("Pacer" page). */
    FOCUSED_REMOTE_8 ("Focused instrument: remote control 8", Kind.FX_REMOTE, 7),

    /** CC 1. */
    MIDI_MOD_WHEEL ("MIDI: mod wheel (CC 1)", Kind.CC, 1),
    /** CC 2. */
    MIDI_BREATH ("MIDI: breath (CC 2)", Kind.CC, 2),
    /** CC 7. */
    MIDI_VOLUME ("MIDI: channel volume (CC 7)", Kind.CC, 7),
    /** CC 11. */
    MIDI_EXPRESSION ("MIDI: expression (CC 11)", Kind.CC, 11),
    /** CC 74 - also the MPE timbre dimension. */
    MIDI_BRIGHTNESS ("MIDI: brightness / timbre (CC 74)", Kind.CC, 74),
    /** Channel pressure. */
    MIDI_CHANNEL_PRESSURE ("MIDI: channel pressure (aftertouch)", Kind.CHANNEL_PRESSURE, 0),
    /** Pitch bend from centre (heel) to full up (toe). */
    MIDI_PITCH_BEND_UP ("MIDI: pitch bend up", Kind.PITCH_BEND_UP, 0);


    /** {@link #restingValue()} for a target that must not be handed back at all. */
    public static final int NO_REST = -1;


    /** How the target is driven. */
    public enum Kind
    {
        /** Not driven. */
        NONE,
        /** A Bitwig parameter binding. */
        PARAMETER,
        /** A remote control of the FX preset's focused instrument. */
        FX_REMOTE,
        /** The volume of whichever loop is being recorded, or was recorded last. */
        ACTIVE_LOOP,
        /** One fader over every loop track. */
        ALL_LOOPS,
        /** A MIDI control change. */
        CC,
        /** MIDI channel pressure. */
        CHANNEL_PRESSURE,
        /** MIDI pitch bend, upper half. */
        PITCH_BEND_UP
    }


    private final String label;
    private final Kind   kind;
    private final int    controller;


    ExpressionTarget (final String label, final Kind kind, final int controller)
    {
        this.label = label;
        this.kind = kind;
        this.controller = controller;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * @return How the target is driven
     */
    public Kind getKind ()
    {
        return this.kind;
    }


    /**
     * @return True if the pedal sends MIDI instead of controlling a parameter
     */
    public boolean isMidi ()
    {
        return this.kind == Kind.CC || this.kind == Kind.CHANNEL_PRESSURE || this.kind == Kind.PITCH_BEND_UP;
    }


    /**
     * @return The loop track (0-7) of a loop track volume target, -1 for every other target
     */
    public int getLoopTrack ()
    {
        return this.name ().startsWith ("LOOP_") ? this.controller : -1;
    }


    /**
     * @return The remote control (0-7) of a focused instrument target
     */
    public int getRemoteIndex ()
    {
        return this.controller;
    }


    /**
     * The value a receiver should be left holding once this pedal stops driving it - the controller's documented
     * neutral, which is not always zero.
     * <p>
     * CC 11 is an attenuator: handing it back at 0 silences the instrument, which would be worse than the parked
     * value this exists to undo. Channel volume and brightness have no neutral at all - on a non-MPE synth CC 74
     * <em>is</em> the filter cutoff, so any value is an override rather than a release, and CC 7 is an absolute
     * level only the player knows. Those two are the targets whose effect is audible and obvious, so a player will
     * simply sweep the pedal back; the ones with a real neutral are the invisible mod-matrix sources that cause the
     * bug in the first place.
     *
     * @return The resting value 0-127, or {@link #NO_REST} if this target must not be handed back
     */
    public int restingValue ()
    {
        return switch (this)
        {
            case MIDI_EXPRESSION -> 127;
            case MIDI_VOLUME, MIDI_BRIGHTNESS -> NO_REST;
            // toMidi (0) is already 8192 for the bend, so the centre falls out of the normal path
            case MIDI_MOD_WHEEL, MIDI_BREATH, MIDI_CHANNEL_PRESSURE, MIDI_PITCH_BEND_UP -> 0;
            default -> NO_REST;
        };
    }


    /**
     * Build the MIDI message for a pedal position.
     *
     * @param value The pedal position, 0-127
     * @param channel The MIDI channel, 0-15
     * @return Status, data 1, data 2; null if the target does not send MIDI
     */
    public int [] toMidi (final int value, final int channel)
    {
        final int v = Math.max (0, Math.min (127, value));
        final int ch = channel & 0x0F;
        return switch (this.kind)
        {
            case CC -> new int []
            {
                0xB0 | ch,
                this.controller,
                v
            };
            case CHANNEL_PRESSURE -> new int []
            {
                0xD0 | ch,
                v,
                0
            };
            case PITCH_BEND_UP -> {
                final int bend = 8192 + (int) Math.round (v / 127.0 * 8191);
                yield new int []
                {
                    0xE0 | ch,
                    bend & 0x7F,
                    bend >> 7
                };
            }
            case NONE, PARAMETER, FX_REMOTE, ACTIVE_LOOP, ALL_LOOPS -> null;
        };
    }
}
