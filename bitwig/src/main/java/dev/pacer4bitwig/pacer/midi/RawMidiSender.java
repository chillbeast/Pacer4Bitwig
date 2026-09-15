// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.midi;

/**
 * Sends a MIDI message into Bitwig, as if it came from the Pacer on the extension's note input.
 */
@FunctionalInterface
public interface RawMidiSender
{
    /** Drops everything. */
    RawMidiSender NONE = (status, data1, data2) -> {
        // Not connected
    };


    /**
     * Send a message.
     *
     * @param status The status byte
     * @param data1 The first data byte
     * @param data2 The second data byte
     */
    void send (int status, int data1, int data2);
}
