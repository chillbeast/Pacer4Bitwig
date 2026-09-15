// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.bitwig;

import com.bitwig.extension.controller.api.ColorValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;

import dev.pacer4bitwig.pacer.fx.FxTracks;


/**
 * The FX preset's view of Bitwig: a flat track bank to find instruments by name, and a cursor track which does not
 * follow the selection, with a device bank and the track's remote controls. Must be created during init.
 */
public class BitwigFxTracks implements FxTracks
{
    /** Instruments are looked up among the first tracks of the project (group children included). */
    private static final int               NUM_TRACKS = 64;

    private final TrackBank                trackBank;
    private final CursorTrack              cursorTrack;
    private final DeviceBank               deviceBank;
    private final CursorRemoteControlsPage remoteControls;


    /**
     * Constructor.
     *
     * @param host The controller host
     */
    public BitwigFxTracks (final ControllerHost host)
    {
        this.trackBank = host.createTrackBank (NUM_TRACKS, 0, 0, true);
        for (int i = 0; i < NUM_TRACKS; i++)
        {
            final Track track = this.trackBank.getItemAt (i);
            track.exists ().markInterested ();
            track.name ().markInterested ();
            track.mute ().markInterested ();
            track.color ().markInterested ();
        }

        this.cursorTrack = host.createCursorTrack ("PACER_FX_INSTRUMENT", "PACER FX instrument", 0, 0, false);
        this.cursorTrack.exists ().markInterested ();
        this.cursorTrack.name ().markInterested ();

        this.deviceBank = this.cursorTrack.createDeviceBank (NUM_DEVICES);
        for (int i = 0; i < NUM_DEVICES; i++)
        {
            final Device device = this.deviceBank.getItemAt (i);
            device.exists ().markInterested ();
            device.name ().markInterested ();
            device.isEnabled ().markInterested ();
        }

        this.remoteControls = this.cursorTrack.createCursorRemoteControlsPage (NUM_REMOTE_CONTROLS);
        this.remoteControls.pageNames ().markInterested ();
        this.remoteControls.selectedPageIndex ().markInterested ();
        for (int i = 0; i < NUM_REMOTE_CONTROLS; i++)
        {
            final RemoteControl remoteControl = this.remoteControls.getParameter (i);
            remoteControl.exists ().markInterested ();
            remoteControl.name ().markInterested ();
            remoteControl.value ().markInterested ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public int getTrackCount ()
    {
        return NUM_TRACKS;
    }


    /** {@inheritDoc} */
    @Override
    public String getTrackName (final int position)
    {
        final Track track = this.getTrack (position);
        return track == null || !track.exists ().get () ? "" : track.name ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isTrackMuted (final int position)
    {
        final Track track = this.getTrack (position);
        return track != null && track.mute ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public void setTrackMuted (final int position, final boolean muted)
    {
        final Track track = this.getTrack (position);
        if (track != null)
            track.mute ().set (muted);
    }


    /** {@inheritDoc} */
    @Override
    public double [] getTrackColour (final int position)
    {
        final Track track = this.getTrack (position);
        if (track == null)
            return new double [3];
        final ColorValue colour = track.color ();
        return new double []
        {
            colour.red (),
            colour.green (),
            colour.blue ()
        };
    }


    /** {@inheritDoc} */
    @Override
    public void selectTrack (final int position)
    {
        final Track track = this.getTrack (position);
        if (track != null)
            track.selectInEditor ();
    }


    /** {@inheritDoc} */
    @Override
    public void focus (final int position)
    {
        final Track track = this.getTrack (position);
        if (track != null)
            this.cursorTrack.selectChannel (track);
    }


    /** {@inheritDoc} */
    @Override
    public String getFocusedName ()
    {
        return this.cursorTrack.exists ().get () ? this.cursorTrack.name ().get () : "";
    }


    /** {@inheritDoc} */
    @Override
    public boolean deviceExists (final int index)
    {
        return this.deviceBank.getItemAt (index).exists ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public String getDeviceName (final int index)
    {
        return this.deviceBank.getItemAt (index).name ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isDeviceEnabled (final int index)
    {
        return this.deviceBank.getItemAt (index).isEnabled ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public void setDeviceEnabled (final int index, final boolean enabled)
    {
        this.deviceBank.getItemAt (index).isEnabled ().set (enabled);
    }


    /** {@inheritDoc} */
    @Override
    public String [] getRemotePageNames ()
    {
        final String [] names = this.remoteControls.pageNames ().get ();
        return names == null ? new String [0] : names;
    }


    /** {@inheritDoc} */
    @Override
    public int getSelectedRemotePage ()
    {
        return this.remoteControls.selectedPageIndex ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectRemotePage (final int index)
    {
        this.remoteControls.selectedPageIndex ().set (index);
    }


    /** {@inheritDoc} */
    @Override
    public boolean remoteExists (final int index)
    {
        return this.remoteControls.getParameter (index).exists ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public String getRemoteName (final int index)
    {
        return this.remoteControls.getParameter (index).name ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public double getRemoteValue (final int index)
    {
        return this.remoteControls.getParameter (index).value ().get ();
    }


    /** {@inheritDoc} */
    @Override
    public void setRemoteValue (final int index, final double value)
    {
        this.remoteControls.getParameter (index).value ().set (value);
    }


    private Track getTrack (final int position)
    {
        return position >= 0 && position < NUM_TRACKS ? this.trackBank.getItemAt (position) : null;
    }
}
