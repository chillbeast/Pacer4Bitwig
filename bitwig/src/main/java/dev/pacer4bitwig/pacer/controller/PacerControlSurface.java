// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.controller;

import de.mossgrabers.framework.controller.AbstractControlSurface;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;

import dev.pacer4bitwig.pacer.PacerConfiguration;


/**
 * The Pacer control surface: 10 stomp switches with LEDs, 4 footswitch jacks, 2 expression pedal jacks.
 */
public class PacerControlSurface extends AbstractControlSurface<PacerConfiguration>
{
    /**
     * Constructor.
     *
     * @param host The host
     * @param colorManager The color manager
     * @param configuration The configuration
     * @param output The MIDI output
     * @param input The MIDI input
     */
    public PacerControlSurface (final IHost host, final ColorManager colorManager, final PacerConfiguration configuration, final IMidiOutput output, final IMidiInput input)
    {
        super (host, configuration, colorManager, output, input, null, 200, 100);
    }
}
