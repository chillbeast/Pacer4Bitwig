// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.pacer4bitwig.pacer.controller.MidiFilters;
import dev.pacer4bitwig.pacer.led.LedMode;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;


class WaveSixTest
{
    @Test
    void ledModeFollowsThePresetInAutomaticMode ()
    {
        assertEquals (LedMode.TWO_COLOUR, LedMode.resolve (LedMode.AUTO, LedMode.TWO_COLOUR));
        assertEquals (LedMode.MULTI_COLOUR, LedMode.resolve (LedMode.AUTO, LedMode.MULTI_COLOUR));
        assertEquals (LedMode.TWO_COLOUR, LedMode.resolve (LedMode.AUTO, LedMode.AUTO));
        // An explicit setting wins
        assertEquals (LedMode.TWO_COLOUR, LedMode.resolve (LedMode.TWO_COLOUR, LedMode.MULTI_COLOUR));
        assertEquals (LedMode.MULTI_COLOUR, LedMode.resolve (LedMode.MULTI_COLOUR, LedMode.TWO_COLOUR));
        // Decoding the preset-loaded value: FxPresetTest
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
