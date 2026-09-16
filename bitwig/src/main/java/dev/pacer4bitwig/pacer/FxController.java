// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.daw.IHost;

import dev.pacer4bitwig.pacer.fx.FxLookup;
import dev.pacer4bitwig.pacer.fx.FxTarget;
import dev.pacer4bitwig.pacer.fx.FxTracks;
import dev.pacer4bitwig.pacer.fx.SnapshotBank;
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedMode;
import dev.pacer4bitwig.pacer.led.LedPattern;
import dev.pacer4bitwig.pacer.led.LedState;
import dev.pacer4bitwig.pacer.looper.Action;

import java.util.Arrays;
import java.util.function.Supplier;


/**
 * The FX preset: a pedalboard for the instruments played live through Bitwig (docs/FX-PRESET.md).
 * <ul>
 * <li>Up to four instruments, each a track found by the name stored in the project. One of them is focused.</li>
 * <li>FX switches control the focused track's remote controls page named "Pacer" (setting) or, if the track has no
 * such page, its first devices.</li>
 * <li>Snapshots store and recall the state of the FX switches, per instrument.</li>
 * </ul>
 * Bitwig is reached through {@link FxTracks}, whose cursor follows the focused instrument and never Bitwig's selection.
 */
public class FxController
{
    /** Do not point the cursor at the focused track again before Bitwig had time to follow. */
    private static final long         FOCUS_RETRY_MS     = 500;
    /** How often the instruments' track positions and the "Pacer" page are looked up again. */
    private static final long         RESOLVE_INTERVAL_MS = 500;
    /** Multi-colour LED of snapshots 1-4. */
    private static final LedColour [] SNAPSHOT_COLOURS   =
    {
        LedColour.WHITE,
        LedColour.RED,
        LedColour.GREEN,
        LedColour.AMBER
    };

    private final IHost               host;
    private final PacerConfiguration  configuration;
    private final FxTracks            tracks;
    private final Supplier<String>    selectedTrackName;
    private final SnapshotBank []     banks              = new SnapshotBank [PacerConfiguration.NUM_INSTRUMENTS];
    /** The stored text each bank was decoded from. */
    private final String []           bankSources        = new String [PacerConfiguration.NUM_INSTRUMENTS];
    private String                    focusRequestedName = "";
    private long                      focusRequestedAt;
    /** Track position of each instrument and the index of the "Pacer" page: looking these up scans every track name,
     * which is far too much work for the LED flushes that ask for them 25 times a second. */
    private final int []              slotPositions      = new int [PacerConfiguration.NUM_INSTRUMENTS];
    private int                       remotePage         = -1;
    private long                      resolvedAt;


    /**
     * Constructor.
     *
     * @param host The host
     * @param configuration The configuration, which also stores instruments and snapshots in the project
     * @param tracks Access to the project's tracks
     * @param selectedTrackName The name of the track selected in Bitwig, empty if none
     */
    public FxController (final IHost host, final PacerConfiguration configuration, final FxTracks tracks, final Supplier<String> selectedTrackName)
    {
        this.host = host;
        this.configuration = configuration;
        this.tracks = tracks;
        this.selectedTrackName = selectedTrackName;
        Arrays.fill (this.slotPositions, -1);
    }


    // ---- Actions ------------------------------------------------------------------------------------------------

    /**
     * Run an FX action.
     *
     * @param action The action; looper actions are ignored
     */
    public void perform (final Action action)
    {
        this.resolve (true);
        switch (action)
        {
            case FX_1 -> this.toggleFx (0);
            case FX_2 -> this.toggleFx (1);
            case FX_3 -> this.toggleFx (2);
            case FX_4 -> this.toggleFx (3);
            case FX_5 -> this.toggleFx (4);
            case FX_6 -> this.toggleFx (5);
            case FOCUS_A -> this.focus (0);
            case FOCUS_B -> this.focus (1);
            case FOCUS_C -> this.focus (2);
            case FOCUS_D -> this.focus (3);
            case FOCUS_NEXT -> this.focusNext (1);
            case FOCUS_PREVIOUS -> this.focusNext (-1);
            case MUTE_A -> this.toggleMute (0);
            case MUTE_B -> this.toggleMute (1);
            case MUTE_C -> this.toggleMute (2);
            case MUTE_D -> this.toggleMute (3);
            case MUTE_FOCUSED -> this.toggleMute (this.configuration.getFocusedInstrument ());
            case ASSIGN_A -> this.assign (0);
            case ASSIGN_B -> this.assign (1);
            case ASSIGN_C -> this.assign (2);
            case ASSIGN_D -> this.assign (3);
            case SNAPSHOT_NEXT -> this.nextSnapshot ();
            case SNAPSHOT_FIRST -> this.firstSnapshot ();
            case SNAPSHOT_STORE -> this.storeSnapshot ();
            default -> {
                // Looper actions run in the LooperController
            }
        }
    }


    /**
     * The LED of a switch whose tap runs an FX action.
     *
     * @param action The action
     * @return The LED state
     */
    public LedState actionLed (final Action action)
    {
        return switch (action)
        {
            case FX_1 -> this.fxLed (0);
            case FX_2 -> this.fxLed (1);
            case FX_3 -> this.fxLed (2);
            case FX_4 -> this.fxLed (3);
            case FX_5 -> this.fxLed (4);
            case FX_6 -> this.fxLed (5);
            case FOCUS_A -> this.instrumentLed (0);
            case FOCUS_B -> this.instrumentLed (1);
            case FOCUS_C -> this.instrumentLed (2);
            case FOCUS_D -> this.instrumentLed (3);
            case FOCUS_NEXT, FOCUS_PREVIOUS -> LedState.when (this.countAssigned () > 1, LedColour.WHITE);
            case MUTE_A -> this.muteLed (0);
            case MUTE_B -> this.muteLed (1);
            case MUTE_C -> this.muteLed (2);
            case MUTE_D -> this.muteLed (3);
            case MUTE_FOCUSED -> this.muteLed (this.configuration.getFocusedInstrument ());
            case ASSIGN_A -> LedState.when (this.isAssigned (0), LedColour.WHITE);
            case ASSIGN_B -> LedState.when (this.isAssigned (1), LedColour.WHITE);
            case ASSIGN_C -> LedState.when (this.isAssigned (2), LedColour.WHITE);
            case ASSIGN_D -> LedState.when (this.isAssigned (3), LedColour.WHITE);
            case SNAPSHOT_NEXT, SNAPSHOT_FIRST, SNAPSHOT_STORE -> this.snapshotLed ();
            default -> LedState.DARK;
        };
    }


    /**
     * Runs on every tick: keeps the cursor on the focused instrument and on its "Pacer" remote controls page.
     */
    public void tick ()
    {
        this.resolve (false);
        this.followFocus ();
        this.followRemotePage ();
    }


    /** Look the instruments and the "Pacer" page up again, at most every {@link #RESOLVE_INTERVAL_MS}. */
    private void resolve (final boolean force)
    {
        final long now = System.currentTimeMillis ();
        if (!force && now - this.resolvedAt < RESOLVE_INTERVAL_MS)
            return;
        this.resolvedAt = now;
        for (int slot = 0; slot < this.slotPositions.length; slot++)
        {
            final String name = this.configuration.getInstrumentTrack (slot);
            this.slotPositions[slot] = name.isEmpty () ? -1 : this.findTrack (name);
        }
        this.remotePage = FxLookup.findPage (this.tracks.getRemotePageNames (), this.configuration.getRemotePageName ());
    }


    /**
     * Show which instrument the FX preset controls.
     */
    public void showFocus ()
    {
        final String name = this.getFocusedInstrumentName ();
        if (name.isEmpty ())
            this.notifyImportant ("FX preset: select an instrument's track in Bitwig, then hold SW A to assign it");
        else
            this.notifyImportant ("FX preset: " + name);
    }


    /**
     * Set a remote control of the focused instrument's "Pacer" page, e.g. from an expression pedal.
     *
     * @param index 0-7
     * @param value The normalized value
     */
    public void setRemoteValue (final int index, final double value)
    {
        if (this.isFocusReady () && this.isPageSelected ())
            this.tracks.setRemoteValue (index, value);
    }


    // ---- FX switches --------------------------------------------------------------------------------------------

    private void toggleFx (final int index)
    {
        if (!this.checkFocus ())
            return;

        final String instrument = this.getFocusedInstrumentName ();
        switch (this.getTarget (index))
        {
            case REMOTE -> {
                final double value = FxTarget.toggled (this.tracks.getRemoteValue (index));
                this.tracks.setRemoteValue (index, value);
                this.notify (instrument + ": " + this.tracks.getRemoteName (index) + (FxTarget.isOn (value) ? " on" : " off"));
            }
            case DEVICE -> {
                final boolean on = !this.tracks.isDeviceEnabled (index);
                this.tracks.setDeviceEnabled (index, on);
                this.notify (instrument + ": " + this.tracks.getDeviceName (index) + (on ? " on" : " off"));
            }
            case NONE -> this.notifyImportant (instrument + ": nothing on FX " + (index + 1));
        }
    }


    private LedState fxLed (final int index)
    {
        if (!this.isFocusReady ())
            return LedState.DARK;
        final boolean on = switch (this.getTarget (index))
        {
            case REMOTE -> FxTarget.isOn (this.tracks.getRemoteValue (index));
            case DEVICE -> this.tracks.isDeviceEnabled (index);
            case NONE -> false;
        };
        return LedState.when (on, LedColour.GREEN);
    }


    private FxTarget getTarget (final int index)
    {
        return FxTarget.resolve (this.remotePage >= 0, this.isPageSelected (), this.tracks.remoteExists (index), this.tracks.deviceExists (index));
    }


    private boolean isPageSelected ()
    {
        return this.remotePage >= 0 && this.remotePage == this.tracks.getSelectedRemotePage ();
    }


    private void followRemotePage ()
    {
        if (this.isFocusReady () && this.remotePage >= 0 && this.remotePage != this.tracks.getSelectedRemotePage ())
            this.tracks.selectRemotePage (this.remotePage);
    }


    // ---- Instruments --------------------------------------------------------------------------------------------

    private void focus (final int slot)
    {
        final String name = this.configuration.getInstrumentTrack (slot);
        if (name.isEmpty ())
        {
            this.notifyImportant ("Instrument " + FxLookup.slotLetter (slot) + " is not assigned: select its track in Bitwig and hold the switch");
            return;
        }

        this.configuration.setFocusedInstrument (slot);
        final int position = this.findTrack (name);
        if (position < 0)
        {
            this.notifyImportant ("Instrument " + FxLookup.slotLetter (slot) + ": no track named \"" + name + "\" in this project");
            return;
        }
        this.pointCursor (position, name);
        if (this.configuration.isFocusSelectsTrack ())
            this.tracks.selectTrack (position);
        this.notifyImportant ("FX: " + name);
    }


    private void focusNext (final int delta)
    {
        final boolean [] assigned = new boolean [PacerConfiguration.NUM_INSTRUMENTS];
        for (int i = 0; i < assigned.length; i++)
            assigned[i] = this.isAssigned (i);
        final int next = FxLookup.nextAssigned (assigned, this.configuration.getFocusedInstrument (), delta);
        if (next < 0)
            this.showFocus ();
        else
            this.focus (next);
    }


    private void toggleMute (final int slot)
    {
        final String name = this.configuration.getInstrumentTrack (slot);
        final int position = name.isEmpty () ? -1 : this.findTrack (name);
        if (position < 0)
        {
            this.notifyImportant ("Instrument " + FxLookup.slotLetter (slot) + (name.isEmpty () ? " is not assigned" : ": no track named \"" + name + "\""));
            return;
        }
        final boolean mute = !this.tracks.isTrackMuted (position);
        this.tracks.setTrackMuted (position, mute);
        this.notifyImportant (name + (mute ? " muted" : " unmuted"));
    }


    private void assign (final int slot)
    {
        final String selected = this.selectedTrackName.get ();
        if (selected == null || selected.isBlank ())
        {
            this.notifyImportant ("Select the instrument's track in Bitwig first, then hold the switch again");
            return;
        }

        final String name = selected.trim ();
        this.configuration.setInstrumentTrack (slot, name);
        this.configuration.setFocusedInstrument (slot);
        final int position = this.findTrack (name);
        if (position >= 0)
            this.pointCursor (position, name);
        this.notifyImportant ("Instrument " + FxLookup.slotLetter (slot) + " is now " + name);
    }


    private LedState instrumentLed (final int slot)
    {
        final int position = this.slotPositions[slot];
        if (position < 0)
            return LedState.DARK;
        if (this.tracks.isTrackMuted (position))
            return new LedState (LedColour.RED, LedPattern.BLINK_MEDIUM);
        final double [] rgb = this.tracks.getTrackColour (position);
        final LedPattern pattern = slot == this.configuration.getFocusedInstrument () ? LedPattern.SOLID : LedPattern.BLIP;
        return new LedState (LedColour.nearest (rgb[0], rgb[1], rgb[2]), pattern);
    }


    private LedState muteLed (final int slot)
    {
        final int position = this.slotPositions[slot];
        return LedState.when (position >= 0 && this.tracks.isTrackMuted (position), LedColour.RED);
    }


    /** Keep the cursor on the focused instrument: after loading a project, or when tracks were added or moved. */
    private void followFocus ()
    {
        final String name = this.getFocusedInstrumentName ();
        if (name.isEmpty () || this.isFocusReady ())
            return;
        final long now = System.currentTimeMillis ();
        if (name.equals (this.focusRequestedName) && now - this.focusRequestedAt < FOCUS_RETRY_MS)
            return;
        final int position = this.findTrack (name);
        if (position >= 0)
            this.pointCursor (position, name);
    }


    private void pointCursor (final int position, final String name)
    {
        this.tracks.focus (position);
        this.focusRequestedName = name;
        this.focusRequestedAt = System.currentTimeMillis ();
    }


    /** True if the cursor is on the focused instrument's track. */
    private boolean isFocusReady ()
    {
        final String name = this.getFocusedInstrumentName ();
        return !name.isEmpty () && name.equalsIgnoreCase (this.tracks.getFocusedName ().trim ());
    }


    /** Like {@link #isFocusReady()}, but tells the user what is missing. */
    private boolean checkFocus ()
    {
        if (this.isFocusReady ())
            return true;
        final String name = this.getFocusedInstrumentName ();
        if (name.isEmpty ())
            this.showFocus ();
        else
            this.notifyImportant ("No track named \"" + name + "\" in this project");
        return false;
    }


    private String getFocusedInstrumentName ()
    {
        return this.configuration.getInstrumentTrack (this.configuration.getFocusedInstrument ());
    }


    private boolean isAssigned (final int slot)
    {
        return !this.configuration.getInstrumentTrack (slot).isEmpty ();
    }


    private int countAssigned ()
    {
        int count = 0;
        for (int i = 0; i < PacerConfiguration.NUM_INSTRUMENTS; i++)
            if (this.isAssigned (i))
                count++;
        return count;
    }


    private int findTrack (final String name)
    {
        return FxLookup.findTrack (name, this.tracks::getTrackName, this.tracks.getTrackCount ());
    }


    // ---- Snapshots ----------------------------------------------------------------------------------------------

    private void nextSnapshot ()
    {
        if (!this.checkFocus ())
            return;
        final int slot = this.configuration.getFocusedInstrument ();
        final int index = this.getBank (slot).next (this.configuration.getSnapshotsPerInstrument ());
        this.saveBank (slot);
        this.recallSnapshot (slot, index);
    }


    private void firstSnapshot ()
    {
        if (!this.checkFocus ())
            return;
        final int slot = this.configuration.getFocusedInstrument ();
        this.getBank (slot).select (0);
        this.saveBank (slot);
        this.recallSnapshot (slot, 0);
    }


    private void recallSnapshot (final int slot, final int index)
    {
        final String instrument = this.getFocusedInstrumentName ();
        final double [] values = this.getBank (slot).get (index);
        if (values == null)
        {
            this.notifyImportant (instrument + ": snapshot " + (index + 1) + " is empty - hold to store the current sound");
            return;
        }

        for (int i = 0; i < SnapshotBank.NUM_VALUES; i++)
        {
            if (Double.isNaN (values[i]))
                continue;
            switch (this.getTarget (i))
            {
                case REMOTE -> this.tracks.setRemoteValue (i, values[i]);
                case DEVICE -> this.tracks.setDeviceEnabled (i, FxTarget.isOn (values[i]));
                case NONE -> {
                    // The switch no longer controls anything
                }
            }
        }
        this.notifyImportant (instrument + ": snapshot " + (index + 1));
    }


    private void storeSnapshot ()
    {
        if (!this.checkFocus ())
            return;
        final int slot = this.configuration.getFocusedInstrument ();
        final SnapshotBank bank = this.getBank (slot);
        final int index = bank.getCurrent (this.configuration.getSnapshotsPerInstrument ());
        bank.store (index, this.captureValues ());
        this.saveBank (slot);
        this.notifyImportant (this.getFocusedInstrumentName () + ": snapshot " + (index + 1) + " stored");
    }


    private double [] captureValues ()
    {
        final double [] values = new double [SnapshotBank.NUM_VALUES];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = switch (this.getTarget (i))
            {
                case REMOTE -> this.tracks.getRemoteValue (i);
                case DEVICE -> this.tracks.isDeviceEnabled (i) ? 1 : 0;
                case NONE -> Double.NaN;
            };
        }
        return values;
    }


    private LedState snapshotLed ()
    {
        if (!this.isFocusReady ())
            return LedState.DARK;
        final SnapshotBank bank = this.getBank (this.configuration.getFocusedInstrument ());
        final int index = bank.getCurrent (this.configuration.getSnapshotsPerInstrument ());
        final double [] stored = bank.get (index);
        final boolean changed = stored != null && !SnapshotBank.matches (stored, this.captureValues ());
        final LedPattern pattern = changed ? LedPattern.BLINK_MEDIUM : LedPattern.SOLID;
        if (this.configuration.getEffectiveLedMode () != LedMode.MULTI_COLOUR)
            return index == 0 && !changed ? LedState.DARK : new LedState (LedColour.WHITE, pattern);
        return new LedState (SNAPSHOT_COLOURS[index], pattern);
    }


    private SnapshotBank getBank (final int slot)
    {
        final String stored = this.configuration.getInstrumentSnapshots (slot);
        if (this.banks[slot] == null || !stored.equals (this.bankSources[slot]))
        {
            this.banks[slot] = SnapshotBank.decode (stored);
            this.bankSources[slot] = stored;
        }
        return this.banks[slot];
    }


    private void saveBank (final int slot)
    {
        final String encoded = this.banks[slot].encode ();
        this.bankSources[slot] = encoded;
        this.configuration.setInstrumentSnapshots (slot, encoded);
    }


    // ---- Helpers ------------------------------------------------------------------------------------------------

    /** A confirmation, shown only at notification level "All". */
    private void notify (final String message)
    {
        if (this.configuration.getNotificationLevel ().shows (false))
            this.host.showNotification (message);
    }


    /** Navigation, warnings and state that is not visible on the Pacer. */
    private void notifyImportant (final String message)
    {
        if (this.configuration.getNotificationLevel ().shows (true))
            this.host.showNotification (message);
    }
}
