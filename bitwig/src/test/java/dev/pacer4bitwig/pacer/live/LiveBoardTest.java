// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.controller.PacerMap;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;


/**
 * The board's dedupe cache is the only thing between the 25 Hz tick and a SysEx flood, so it has to be exactly as
 * optimistic as the port is reliable - and no more. A Bitwig log from 2026-09-18 shows eleven messages refused with
 * "couldn't send midi sysex because the outport isn't opened", which is a whole {@code repaintAll ()}; caching those
 * as sent left the board asserting colours the Pacer never received.
 */
class LiveBoardTest
{
    /** A sender that can be told to start failing, and remembers what it was asked to send. */
    private static final class Sender implements LiveBoard.SysexSender
    {
        final List<String> sent    = new ArrayList<> ();
        boolean            healthy = true;


        /** {@inheritDoc} */
        @Override
        public boolean send (final String hex)
        {
            if (!this.healthy)
                return false;
            this.sent.add (hex);
            return true;
        }
    }


    @Test
    void unchangedSwitchSendsNothing ()
    {
        final Sender sender = new Sender ();
        final LiveBoard board = new LiveBoard (sender);

        assertTrue (board.setLed (0, PacerColour.GREEN, PacerColour.OFF, LedRow.STRIP));
        assertFalse (board.setLed (0, PacerColour.GREEN, PacerColour.OFF, LedRow.STRIP));
        assertEquals (1, sender.sent.size ());
    }


    @Test
    void aRefusedColourIsNotCachedAndIsSentAgainWhenThePortComesBack ()
    {
        final Sender sender = new Sender ();
        final LiveBoard board = new LiveBoard (sender);

        sender.healthy = false;
        assertFalse (board.setLed (3, PacerColour.MAGENTA, PacerColour.OFF, LedRow.STRIP));
        assertEquals (0, sender.sent.size ());
        assertEquals (0, board.getMessageCount ());

        sender.healthy = true;
        assertTrue (board.setLed (3, PacerColour.MAGENTA, PacerColour.OFF, LedRow.STRIP));
        assertEquals (1, sender.sent.size ());
    }


    @Test
    void aRefusedNameIsNotCachedAndIsSentAgainWhenThePortComesBack ()
    {
        final Sender sender = new Sender ();
        final LiveBoard board = new LiveBoard (sender);

        sender.healthy = false;
        assertFalse (board.setName ("LOOP"));
        sender.healthy = true;
        assertTrue (board.setName ("LOOP"));
        assertEquals (1, sender.sent.size ());
    }


    @Test
    void repeatNameSaysWhetherItWentOut ()
    {
        final Sender sender = new Sender ();
        final LiveBoard board = new LiveBoard (sender);

        // Nothing has been named yet, so there is nothing to repeat
        assertFalse (board.repeatName ());

        board.setName ("LOOP");
        assertTrue (board.repeatName ());

        sender.healthy = false;
        assertFalse (board.repeatName ());
    }


    @Test
    void aBoardThatLostEveryMessageRepaintsInFullOnceThePortReturns ()
    {
        final Sender sender = new Sender ();
        final LiveBoard board = new LiveBoard (sender);

        sender.healthy = false;
        board.blackout ("OFF");
        assertEquals (0, sender.sent.size ());

        // Without the send-before-cache order this second pass would be deduped away and the board would stay wrong
        sender.healthy = true;
        board.blackout ("OFF");
        assertEquals (PacerMap.NUM_SWITCHES + 1, sender.sent.size (), "ten switches plus the display name");
    }
}
