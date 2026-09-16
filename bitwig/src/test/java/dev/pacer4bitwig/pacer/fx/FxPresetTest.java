// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.fx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.looper.ExpressionTarget;
import dev.pacer4bitwig.pacer.looper.TapTiming;
import dev.pacer4bitwig.pacer.preset.PresetAnnouncement;
import dev.pacer4bitwig.pacer.preset.PresetKind;

import org.junit.jupiter.api.Test;


class FxPresetTest
{
    @Test
    void presetAnnouncementSaysWhichPresetWasSelected ()
    {
        // The old LED variants (2, 18) still decode to the right preset, so presets already on a Pacer keep working
        assertEquals (new PresetAnnouncement (PresetKind.LOOPER), PresetAnnouncement.fromValue (127));
        assertEquals (new PresetAnnouncement (PresetKind.LOOPER), PresetAnnouncement.fromValue (2));
        assertEquals (new PresetAnnouncement (PresetKind.FX), PresetAnnouncement.fromValue (17));
        assertEquals (new PresetAnnouncement (PresetKind.FX), PresetAnnouncement.fromValue (18));
        // Unknown kinds count as the looper, unknown variants as two-colour
        assertEquals (new PresetAnnouncement (PresetKind.LOOPER), PresetAnnouncement.fromValue (34));
        assertEquals (new PresetAnnouncement (PresetKind.LOOPER), PresetAnnouncement.fromValue (0));
        // Only the canonical values round-trip; the old variants collapse onto them
        assertEquals (127, PresetAnnouncement.fromValue (127).toValue ());
        assertEquals (17, PresetAnnouncement.fromValue (17).toValue ());
        assertEquals (127, PresetAnnouncement.fromValue (2).toValue (), "the old multi-colour looper value");
        assertEquals (17, PresetAnnouncement.fromValue (18).toValue (), "the old multi-colour FX value");
    }


    @Test
    void fxSwitchesUseThePacerPageOrElseTheDevices ()
    {
        assertEquals (FxTarget.REMOTE, FxTarget.resolve (true, true, true, true));
        // A track with a Pacer page never falls back to its devices
        assertEquals (FxTarget.NONE, FxTarget.resolve (true, true, false, true));
        assertEquals (FxTarget.NONE, FxTarget.resolve (true, false, true, true));
        assertEquals (FxTarget.DEVICE, FxTarget.resolve (false, false, false, true));
        assertEquals (FxTarget.NONE, FxTarget.resolve (false, false, true, false));

        assertTrue (FxTarget.isOn (0.5));
        assertFalse (FxTarget.isOn (0.49));
        assertEquals (0, FxTarget.toggled (0.7));
        assertEquals (1, FxTarget.toggled (0.2));
    }


    @Test
    void lookupsIgnoreCaseAndSpaces ()
    {
        assertEquals (1, FxLookup.findPage (new String [] {"Main", " pacer "}, "Pacer"));
        assertEquals (-1, FxLookup.findPage (new String [] {"Main"}, "Pacer"));
        assertEquals (-1, FxLookup.findPage (null, "Pacer"));

        final String [] names = {"Drums", "", "Guitar", "guitar"};
        assertEquals (2, FxLookup.findTrack ("GUITAR", i -> names[i], names.length));
        assertEquals (-1, FxLookup.findTrack ("Bass", i -> names[i], names.length));
        assertEquals (-1, FxLookup.findTrack ("", i -> names[i], names.length));
        assertEquals ("C", FxLookup.slotLetter (2));
    }


    @Test
    void focusCyclesThroughAssignedInstruments ()
    {
        final boolean [] assigned = {true, false, true, false};
        assertEquals (2, FxLookup.nextAssigned (assigned, 0, 1));
        assertEquals (0, FxLookup.nextAssigned (assigned, 2, 1));
        assertEquals (2, FxLookup.nextAssigned (assigned, 0, -1));
        assertEquals (-1, FxLookup.nextAssigned (new boolean [4], 0, 1));
    }


    @Test
    void snapshotsSurviveBeingStoredInTheProject ()
    {
        final SnapshotBank bank = new SnapshotBank ();
        bank.store (1, new double [] {1, 0, 0.35, Double.NaN, 0, 1});
        bank.select (1);

        final SnapshotBank copy = SnapshotBank.decode (bank.encode ());
        assertEquals (1, copy.getCurrent (4));
        assertFalse (copy.isStored (0));
        assertTrue (copy.isStored (1));
        assertArrayEquals (new double [] {1, 0, 0.35, Double.NaN, 0, 1}, copy.get (1), 1e-9);

        // Anything unreadable is empty, never an error
        assertFalse (SnapshotBank.decode ("x;;1,2").isStored (0));
        assertTrue (SnapshotBank.decode ("x;;1,2").isStored (1));
        assertFalse (SnapshotBank.decode (null).isStored (0));
    }


    @Test
    void snapshotsCycleWithinTheConfiguredCount ()
    {
        final SnapshotBank bank = new SnapshotBank ();
        assertEquals (1, bank.next (2));
        assertEquals (0, bank.next (2));
        bank.select (3);
        // Fewer snapshots configured than the current one
        assertEquals (1, bank.getCurrent (2));
        assertEquals (0, bank.next (2));
    }


    @Test
    void aSnapshotMatchesWhileNothingChanged ()
    {
        final double [] stored = {1, 0, 0.35, Double.NaN, 0, 0};
        assertTrue (SnapshotBank.matches (stored, new double [] {1, 0, 0.355, 0.8, 0, 0}));
        assertFalse (SnapshotBank.matches (stored, new double [] {0, 0, 0.35, 0, 0, 0}));
    }


    @Test
    void trackColoursMapToPacerColours ()
    {
        assertEquals (LedColour.WHITE, LedColour.nearest (0.5, 0.5, 0.5));
        assertEquals (LedColour.WHITE, LedColour.nearest (0, 0, 0));
        assertEquals (LedColour.RED, LedColour.nearest (0.9, 0.1, 0.1));
        assertEquals (LedColour.AMBER, LedColour.nearest (1, 0.8, 0));
        assertEquals (LedColour.GREEN, LedColour.nearest (0.2, 0.8, 0.2));
        assertEquals (LedColour.BLUE, LedColour.nearest (0.1, 0.4, 0.9));
        assertEquals (LedColour.PURPLE, LedColour.nearest (0.6, 0.2, 0.9));
    }


    @Test
    void momentaryHoldsFireTheTapOnPress ()
    {
        assertTrue (TapTiming.actionTapOnPress (Action.SNAPSHOT_NEXT, Action.MOMENTARY));
        // Holding to store must not first move to the next snapshot
        assertFalse (TapTiming.actionTapOnPress (Action.SNAPSHOT_NEXT, Action.SNAPSHOT_STORE));
        assertTrue (Action.FX_1.isFx ());
        assertFalse (Action.UNDO.isFx ());
        assertFalse (Action.MOMENTARY.isFx ());
        assertTrue (Action.ASSIGN_A.isDestructive ());
    }


    @Test
    void pedalsCanTargetTheFocusedInstrument ()
    {
        assertEquals (ExpressionTarget.Kind.FX_REMOTE, ExpressionTarget.FOCUSED_REMOTE_7.getKind ());
        assertEquals (6, ExpressionTarget.FOCUSED_REMOTE_7.getRemoteIndex ());
        assertFalse (ExpressionTarget.FOCUSED_REMOTE_8.isMidi ());
        assertNull (ExpressionTarget.FOCUSED_REMOTE_1.toMidi (64, 0));
    }
}
