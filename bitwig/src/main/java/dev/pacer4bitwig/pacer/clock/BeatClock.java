// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.clock;

/**
 * The running transport position. DrivenByMoss only exposes the play start position, so the live position comes
 * from a Bitwig transport created by the extension (see TransportBeatClock).
 */
public interface BeatClock
{
    /** A clock that never runs. */
    BeatClock NONE = new BeatClock ()
    {
        /** {@inheritDoc} */
        @Override
        public boolean isPlaying ()
        {
            return false;
        }


        /** {@inheritDoc} */
        @Override
        public double getPositionInBeats ()
        {
            return 0;
        }


        /** {@inheritDoc} */
        @Override
        public double getBeatsPerBar ()
        {
            return 4;
        }
    };


    /**
     * @return True while the transport plays
     */
    boolean isPlaying ();


    /**
     * @return The play position in quarter notes
     */
    double getPositionInBeats ();


    /**
     * @return The length of a bar in quarter notes
     */
    double getBeatsPerBar ();
}
