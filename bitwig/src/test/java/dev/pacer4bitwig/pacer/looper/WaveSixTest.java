// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.controller.MidiFilters;
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.live.PacerColour;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;


class WaveSixTest
{
    @Test
    void everyStateColourSurvivesTheTripToTheHardwarePalette ()
    {
        // The light cache carries a LedColour; the Pacer is written a PacerColour
        for (final LedColour colour: LedColour.values ())
            assertEquals (colour == LedColour.OFF, colour.toPacer () == PacerColour.OFF, colour + " maps to a real colour");
        assertEquals (PacerColour.GOLD, LedColour.AMBER.toPacer (), "amber is the Pacer's gold");
    }


    @Test
    void noteInputFiltersFollowTheLooperChannel ()
    {
        final List<String> filters = Arrays.asList (MidiFilters.allChannelsExcept (4));
        assertFalse (filters.contains ("B4????"));
        assertFalse (filters.contains ("94????"));
        assertTrue (filters.contains ("BF????"));
        assertTrue (filters.contains ("90????"));
    }
}
