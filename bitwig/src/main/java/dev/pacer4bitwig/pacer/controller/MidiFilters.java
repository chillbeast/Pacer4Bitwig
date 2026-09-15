// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * Bitwig note input filters.
 */
public final class MidiFilters
{
    private static final String CHANNEL_MESSAGE_STATUS = "89ABCDE";


    private MidiFilters ()
    {
        // Utility
    }


    /**
     * Filters which pass every channel message (notes, poly/channel pressure, CC, program change, pitch bend) on
     * all channels but one.
     *
     * @param excludedChannel The channel to hold back, 0-15
     * @return The filters
     */
    public static String [] allChannelsExcept (final int excludedChannel)
    {
        final List<String> filters = new ArrayList<> ();
        for (final char status: CHANNEL_MESSAGE_STATUS.toCharArray ())
            for (int channel = 0; channel < 16; channel++)
                if (channel != excludedChannel)
                    filters.add (status + Integer.toHexString (channel).toUpperCase (Locale.ROOT) + "????");
        return filters.toArray (new String [0]);
    }
}
