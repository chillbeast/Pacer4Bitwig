// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.bitwig;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Transport;

import dev.pacer4bitwig.pacer.clock.BeatClock;


/**
 * Beat clock on Bitwig's own transport. Must be created during the extension's init phase. The position is
 * interpolated with the tempo between observer updates so beat-synced LEDs do not stutter.
 */
public class TransportBeatClock implements BeatClock
{
    /** Never extrapolate further than this past the last position update. */
    private static final double MAX_EXTRAPOLATION_SECONDS = 0.5;

    private final Transport     transport;
    private volatile double     lastPosition;
    private volatile long       lastUpdateNanos = System.nanoTime ();


    /**
     * Constructor.
     *
     * @param host The controller host
     */
    public TransportBeatClock (final ControllerHost host)
    {
        this.transport = host.createTransport ();
        this.transport.isPlaying ().markInterested ();
        this.transport.tempo ().value ().markInterested ();
        this.transport.timeSignature ().numerator ().markInterested ();
        this.transport.timeSignature ().denominator ().markInterested ();
        this.transport.playPosition ().addValueObserver (beats -> {
            this.lastPosition = beats;
            this.lastUpdateNanos = System.nanoTime ();
        });
    }


    /** {@inheritDoc} */
    @Override
    public boolean isPlaying ()
    {
        return this.transport.isPlaying ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public double getPositionInBeats ()
    {
        if (!this.isPlaying ())
            return this.lastPosition;
        final double seconds = Math.min ((System.nanoTime () - this.lastUpdateNanos) / 1e9, MAX_EXTRAPOLATION_SECONDS);
        return this.lastPosition + seconds * this.transport.tempo ().value ().getRaw () / 60.0;
    }


    /** {@inheritDoc} */
    @Override
    public double getBeatsPerBar ()
    {
        final int denominator = this.transport.timeSignature ().denominator ().get ();
        final int numerator = this.transport.timeSignature ().numerator ().get ();
        return denominator <= 0 || numerator <= 0 ? 4 : numerator * 4.0 / denominator;
    }
}
