// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.midi;

/**
 * Creates a Bitwig note input on the Pacer's MIDI port. DrivenByMoss' note input wrapper cannot inject MIDI, so the
 * Bitwig layer creates the note input itself. Must be called during init.
 */
@FunctionalInterface
public interface NoteInputFactory
{
    /**
     * Create the note input.
     *
     * @param name The name shown in Bitwig's track input chooser
     * @param filters The MIDI messages from the hardware that pass into the note input
     * @return Sends further MIDI into the note input, bypassing the filters
     */
    RawMidiSender create (String name, String [] filters);
}
