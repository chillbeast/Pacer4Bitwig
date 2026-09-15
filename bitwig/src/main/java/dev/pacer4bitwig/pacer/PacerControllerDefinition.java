// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.controller.DefaultControllerDefinition;
import de.mossgrabers.framework.utils.OperatingSystem;
import de.mossgrabers.framework.utils.Pair;

import java.util.List;
import java.util.UUID;


/**
 * Controller definition for "PACER Looper". Uses USB MIDI port 1 of the Pacer; port 2 belongs to Nektar's
 * own DAW integration.
 */
public class PacerControllerDefinition extends DefaultControllerDefinition
{
    private static final UUID EXTENSION_ID = UUID.fromString ("5b1f7c2e-3d4a-4e8b-9f60-2a7c1d9e4b83");


    /**
     * Constructor.
     */
    public PacerControllerDefinition ()
    {
        super (EXTENSION_ID, "PACER Looper", "Nektar", 1, 1);
    }


    /** {@inheritDoc} */
    @Override
    public List<Pair<String [], String []>> getMidiDiscoveryPairs (final OperatingSystem os)
    {
        final List<Pair<String [], String []>> pairs = super.getMidiDiscoveryPairs (os);
        switch (os)
        {
            case MAC, MAC_ARM:
                pairs.add (this.addDeviceDiscoveryPair ("PACER MIDI1", "PACER MIDI1"));
                break;

            case LINUX:
                pairs.addAll (this.createLinuxDeviceDiscoveryPairs ("PACER", "PACER"));
                break;

            case WINDOWS:
            default:
                pairs.add (this.addDeviceDiscoveryPair ("PACER", "PACER"));
                break;
        }
        return pairs;
    }
}
