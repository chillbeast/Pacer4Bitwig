// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.daw;

/**
 * The DAW functions the Pacer's built-in Track and Transport presets send on USB port 2, MIDI channel 16, as observed
 * from Nektar's own Bitwig script (see docs/ROADMAP.md).
 */
public enum DawFunction
{
    /** Play/pause. */
    PLAY (84, Kind.PRESS),
    /** Stop. */
    STOP (83, Kind.PRESS),
    /** Arranger record. */
    RECORD (85, Kind.PRESS),
    /** Arranger loop on/off. */
    LOOP (80, Kind.PRESS),
    /** Rewind, repeating while held. */
    REWIND (81, Kind.HELD),
    /** Fast forward, repeating while held. */
    FAST_FORWARD (82, Kind.HELD),
    /** Metronome on/off. */
    METRONOME (89, Kind.PRESS),
    /** Pre-roll none / 1 bar. */
    PRE_ROLL (25, Kind.PRESS),
    /** Arranger overdub. */
    OVERDUB (22, Kind.PRESS),
    /** Jump to the loop start (stops first). */
    GOTO_LOOP_START (86, Kind.PRESS),
    /** Move the loop region left. */
    LOOP_LEFT (18, Kind.PRESS),
    /** Move the loop region right. */
    LOOP_RIGHT (19, Kind.PRESS),
    /** Selected track mute. */
    MUTE (30, Kind.PRESS),
    /** Selected track solo. */
    SOLO (31, Kind.PRESS),
    /** Selected track arm. */
    ARM (90, Kind.PRESS),
    /** Undo. */
    UNDO (88, Kind.PRESS),
    /** Select the previous track, repeating while held. */
    TRACK_PREVIOUS (92, Kind.HELD),
    /** Select the next track, repeating while held. */
    TRACK_NEXT (91, Kind.HELD),
    /** Previous patch (Nektar: preset browser; here: previous device). */
    PATCH_PREVIOUS (94, Kind.HELD),
    /** Next patch (Nektar: preset browser; here: next device). */
    PATCH_NEXT (93, Kind.HELD),
    /** Selected track volume, absolute. */
    TRACK_VOLUME (15, Kind.ABSOLUTE),
    /** Selected track volume, relative (7-bit two's complement). */
    TRACK_VOLUME_RELATIVE (9, Kind.RELATIVE),
    /** Master volume, absolute. */
    MASTER_VOLUME (14, Kind.ABSOLUTE),
    /** Master volume, relative (7-bit two's complement). */
    MASTER_VOLUME_RELATIVE (8, Kind.RELATIVE);


    /** How the function uses the CC value. */
    public enum Kind
    {
        /** Fires once when the value is above 0. */
        PRESS,
        /** Fires when pressed and repeats until released (value 0). */
        HELD,
        /** Sets a value 0-127. */
        ABSOLUTE,
        /** Changes a value by a signed amount. */
        RELATIVE
    }


    private static final DawFunction [] BY_CC = new DawFunction [128];

    static
    {
        for (final DawFunction function: values ())
            BY_CC[function.cc] = function;
    }

    private final int  cc;
    private final Kind kind;


    DawFunction (final int cc, final Kind kind)
    {
        this.cc = cc;
        this.kind = kind;
    }


    /**
     * @return The CC on channel 16
     */
    public int getCC ()
    {
        return this.cc;
    }


    /**
     * @return How the CC value is used
     */
    public Kind getKind ()
    {
        return this.kind;
    }


    /**
     * @param cc A controller number
     * @return The function, null if the CC is not a DAW function
     */
    public static DawFunction fromCC (final int cc)
    {
        return cc >= 0 && cc < BY_CC.length ? BY_CC[cc] : null;
    }
}
