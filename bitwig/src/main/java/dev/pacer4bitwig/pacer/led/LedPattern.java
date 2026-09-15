// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

/**
 * On/off patterns. While the transport runs (and beat-synced LEDs are enabled) they follow the beat, otherwise the
 * wall clock, so every LED blinks in phase either way.
 */
public enum LedPattern
{
    /** Always on. */
    SOLID,
    /** On, with a short gap on every downbeat while synced. */
    SOLID_DIP,
    /** Synced: eighth notes. Otherwise 4 Hz. */
    BLINK_FAST,
    /** Synced: quarter notes. Otherwise 2 Hz. */
    BLINK_MEDIUM,
    /** Synced: a flash on every downbeat. Otherwise a short flash once a second. */
    BLIP,
    /** Synced: a flash on every beat. Otherwise off. */
    BEAT_FLASH;


    /** Length of dips and flashes in quarter notes. */
    private static final double FLASH_BEATS = 0.25;


    /**
     * Is the LED on at the given time?
     *
     * @param clock The time
     * @return True if on
     */
    public boolean isOn (final LedClock clock)
    {
        final long ms = clock.millis ();
        return switch (this)
        {
            case SOLID -> true;
            case SOLID_DIP -> !clock.synced () || clock.barPosition () >= FLASH_BEATS;
            case BLINK_FAST -> clock.synced () ? fraction (clock.beats () * 2) < 0.5 : ms / 125 % 2 == 0;
            case BLINK_MEDIUM -> clock.synced () ? clock.beatPhase () < 0.5 : ms / 250 % 2 == 0;
            case BLIP -> clock.synced () ? clock.barPosition () < FLASH_BEATS : ms % 1000 < 120;
            case BEAT_FLASH -> clock.synced () && clock.beatPhase () < FLASH_BEATS;
        };
    }


    private static double fraction (final double value)
    {
        return value - Math.floor (value);
    }
}
