// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.controller.DefaultControllerDefinition;
import de.mossgrabers.framework.utils.OperatingSystem;
import de.mossgrabers.framework.utils.Pair;

import java.util.List;
import java.util.UUID;


/**
 * Controller definition for "PACER Looper". Port 1 carries the looper preset and every other user preset; port 2 is
 * the Pacer's DAW port, used by the optional Nektar DAW mode.
 */
public class PacerControllerDefinition extends DefaultControllerDefinition
{
    private static final UUID EXTENSION_ID = UUID.fromString ("5b1f7c2e-3d4a-4e8b-9f60-2a7c1d9e4b83");


    /**
     * Constructor.
     */
    public PacerControllerDefinition ()
    {
        super (EXTENSION_ID, "PACER Looper", "Nektar", 2, 2);
    }


    /** {@inheritDoc} */
    @Override
    public List<Pair<String [], String []>> getMidiDiscoveryPairs (final OperatingSystem os)
    {
        final List<Pair<String [], String []>> pairs = super.getMidiDiscoveryPairs (os);
        switch (os)
        {
            case MAC, MAC_ARM:
                pairs.add (this.addDeviceDiscoveryPair (new String []
                {
                    "PACER MIDI1",
                    "PACER MIDI2"
                }, new String []
                {
                    "PACER MIDI1",
                    "PACER MIDI2"
                }));
                break;

            case LINUX:
                pairs.add (this.addDeviceDiscoveryPair (new String []
                {
                    "PACER MIDI 1",
                    "PACER MIDI 2"
                }, new String []
                {
                    "PACER MIDI 1",
                    "PACER MIDI 2"
                }));
                break;

            case WINDOWS:
            default:
                pairs.add (this.addDeviceDiscoveryPair (new String []
                {
                    "PACER",
                    "MIDIIN2 (PACER)"
                }, new String []
                {
                    "PACER",
                    "MIDIOUT2 (PACER)"
                }));
                break;
        }
        return pairs;
    }
}
