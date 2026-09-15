// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.bitwig;

import de.mossgrabers.bitwig.framework.BitwigSetupFactory;
import de.mossgrabers.bitwig.framework.configuration.SettingsUIImpl;
import de.mossgrabers.bitwig.framework.daw.HostImpl;
import de.mossgrabers.bitwig.framework.extension.AbstractControllerExtensionDefinition;
import de.mossgrabers.framework.controller.IControllerSetup;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.NoteInput;

import dev.pacer4bitwig.pacer.PacerConfiguration;
import dev.pacer4bitwig.pacer.PacerControllerDefinition;
import dev.pacer4bitwig.pacer.PacerControllerSetup;
import dev.pacer4bitwig.pacer.controller.PacerControlSurface;


/**
 * Bitwig entry point for the "PACER Looper" controller extension. Everything that needs the raw Bitwig API is
 * handed to the setup as a small callback, so the rest of the code only sees the DrivenByMoss abstractions.
 */
public class PacerControllerExtensionDefinition extends AbstractControllerExtensionDefinition<PacerControlSurface, PacerConfiguration>
{
    /**
     * Constructor.
     */
    public PacerControllerExtensionDefinition ()
    {
        super (new PacerControllerDefinition ());
    }


    /** {@inheritDoc} */
    @Override
    protected IControllerSetup<PacerControlSurface, PacerConfiguration> getControllerSetup (final ControllerHost host)
    {
        return new PacerControllerSetup (new HostImpl (host), new BitwigSetupFactory (host), new SettingsUIImpl (host, host.getPreferences ()), new SettingsUIImpl (host, host.getDocumentState ()), host::requestFlush, () -> new TransportBeatClock (host), (name, filters) -> {
            final NoteInput noteInput = host.getMidiInPort (0).createNoteInput (name, filters);
            // Let the hardware bindings see every message as well
            noteInput.setShouldConsumeEvents (false);
            return noteInput::sendRawMidiEvent;
        }, () -> new BitwigFxTracks (host));
    }


    /** {@inheritDoc} */
    @Override
    public String getVersion ()
    {
        final String v = this.getClass ().getPackage ().getImplementationVersion ();
        return v == null ? "dev" : v;
    }


    /** {@inheritDoc} */
    @Override
    public String getAuthor ()
    {
        return "Pacer4Bitwig (on DrivenByMoss by Jürgen Moßgraber)";
    }


    /** {@inheritDoc} */
    @Override
    public boolean isUsingBetaAPI ()
    {
        return false;
    }
}
