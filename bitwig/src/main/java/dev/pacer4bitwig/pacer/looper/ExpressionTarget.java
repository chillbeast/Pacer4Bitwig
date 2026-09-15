// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import dev.pacer4bitwig.util.Labelled;


/**
 * What an expression pedal controls: a Bitwig parameter (bound directly), a remote control of the FX preset's focused
 * instrument, or a MIDI message sent into the extension's note input, which reaches the instruments of every track
 * listening to it.
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


    /** How the target is driven. */
    public enum Kind
    {
        /** Not driven. */
        NONE,
        /** A Bitwig parameter binding. */
        PARAMETER,
        /** A remote control of the FX preset's focused instrument. */
        FX_REMOTE,
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
     * @return The remote control (0-7) of a focused instrument target
     */
    public int getRemoteIndex ()
    {
        return this.controller;
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
            case NONE, PARAMETER, FX_REMOTE -> null;
        };
    }
}
