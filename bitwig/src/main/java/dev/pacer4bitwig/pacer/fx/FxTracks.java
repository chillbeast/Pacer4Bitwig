// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.fx;

/**
 * What the FX preset needs from Bitwig: the project's tracks (to find instruments by name) and a cursor on the focused
 * instrument with its devices and track remote controls. The cursor follows only {@link #focus(int)} - never Bitwig's
 * selection - and never changes the selection either.
 */
public interface FxTracks
{
    /** Devices reachable by the FX switches. */
    int NUM_DEVICES         = 6;
    /** Remote controls of a page. */
    int NUM_REMOTE_CONTROLS = 8;


    /**
     * @return How many tracks of the project (flat list, from the top) can be instruments
     */
    int getTrackCount ();


    /**
     * @param position 0 to track count - 1
     * @return The track name, empty if there is no track
     */
    String getTrackName (int position);


    /**
     * @param position 0 to track count - 1
     * @return True if the track is muted
     */
    boolean isTrackMuted (int position);


    /**
     * @param position 0 to track count - 1
     * @param muted True to mute
     */
    void setTrackMuted (int position, boolean muted);


    /**
     * @param position 0 to track count - 1
     * @return Red, green, blue, each 0-1
     */
    double [] getTrackColour (int position);


    /**
     * Select a track in Bitwig.
     *
     * @param position 0 to track count - 1
     */
    void selectTrack (int position);


    /**
     * Point the cursor at a track.
     *
     * @param position 0 to track count - 1
     */
    void focus (int position);


    /**
     * @return The name of the track the cursor is on, empty if none
     */
    String getFocusedName ();


    /**
     * @param index 0-5
     * @return True if the focused track has a device at this position
     */
    boolean deviceExists (int index);


    /**
     * @param index 0-5
     * @return The device name
     */
    String getDeviceName (int index);


    /**
     * @param index 0-5
     * @return True if the device is on
     */
    boolean isDeviceEnabled (int index);


    /**
     * @param index 0-5
     * @param enabled True to switch the device on
     */
    void setDeviceEnabled (int index, boolean enabled);


    /**
     * @return The names of the focused track's remote controls pages
     */
    String [] getRemotePageNames ();


    /**
     * @return The page the remote controls cursor is on
     */
    int getSelectedRemotePage ();


    /**
     * @param index The page to move the remote controls cursor to
     */
    void selectRemotePage (int index);


    /**
     * @param index 0-7
     * @return True if the remote control is mapped
     */
    boolean remoteExists (int index);


    /**
     * @param index 0-7
     * @return The remote control's name
     */
    String getRemoteName (int index);


    /**
     * @param index 0-7
     * @return The normalized value, 0-1
     */
    double getRemoteValue (int index);


    /**
     * @param index 0-7
     * @param value The normalized value, 0-1
     */
    void setRemoteValue (int index, double value);
}
