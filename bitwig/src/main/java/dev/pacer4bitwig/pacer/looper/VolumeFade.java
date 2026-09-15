// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * A volume fade over the loop tracks, measured in beats so it follows the tempo. Remembers every track's original
 * volume: a fade-out ramps from it to silence, a fade-in from silence to it; both are restored afterwards.
 */
public final class VolumeFade
{
    /** Fade direction. */
    public enum Direction
    {
        /** From the original volume to silence. */
        OUT,
        /** From silence to the original volume. */
        IN
    }


    private final Direction            direction;
    private final double               lengthBeats;
    private final Map<Integer, Double> originalVolumes;
    private double                     startBeats = Double.NaN;


    /**
     * Constructor.
     *
     * @param direction The direction
     * @param lengthBeats The length in quarter notes
     * @param originalVolumes Normalized volume (0..1) per track bank position
     */
    public VolumeFade (final Direction direction, final double lengthBeats, final Map<Integer, Double> originalVolumes)
    {
        this.direction = direction;
        this.lengthBeats = Math.max (lengthBeats, 0.001);
        this.originalVolumes = Collections.unmodifiableMap (new LinkedHashMap<> (originalVolumes));
    }


    /**
     * @return The direction
     */
    public Direction getDirection ()
    {
        return this.direction;
    }


    /**
     * @return Normalized volume per track bank position, as it was before the fade
     */
    public Map<Integer, Double> getOriginalVolumes ()
    {
        return this.originalVolumes;
    }


    /**
     * Start the fade clock at the given position, unless it already runs.
     *
     * @param beats The play position
     */
    public void startAt (final double beats)
    {
        if (Double.isNaN (this.startBeats))
            this.startBeats = beats;
    }


    /**
     * @return True once the fade clock runs
     */
    public boolean isStarted ()
    {
        return !Double.isNaN (this.startBeats);
    }


    /**
     * @param beats The play position
     * @return 0..1, 0 before the start
     */
    public double getProgress (final double beats)
    {
        if (!this.isStarted ())
            return 0;
        return Math.max (0, Math.min (1, (beats - this.startBeats) / this.lengthBeats));
    }


    /**
     * @param beats The play position
     * @return True when the ramp has reached its end
     */
    public boolean isComplete (final double beats)
    {
        return this.getProgress (beats) >= 1;
    }


    /**
     * The volume a track should have now.
     *
     * @param trackIndex The track bank position
     * @param beats The play position
     * @return The normalized volume, or -1 for tracks that are not part of the fade
     */
    public double getVolume (final int trackIndex, final double beats)
    {
        final Double original = this.originalVolumes.get (Integer.valueOf (trackIndex));
        if (original == null)
            return -1;
        final double progress = this.getProgress (beats);
        return original.doubleValue () * (this.direction == Direction.OUT ? 1 - progress : progress);
    }
}
