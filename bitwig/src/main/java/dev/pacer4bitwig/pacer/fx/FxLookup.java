// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.fx;

import java.util.function.IntFunction;


/**
 * Finding instruments and remote controls pages by name. Names match ignoring case and surrounding spaces.
 */
public final class FxLookup
{
    private FxLookup ()
    {
        // Utility
    }


    /**
     * @param pageNames The page names, may be null
     * @param wanted The configured page name
     * @return The index of the first matching page, -1 if none
     */
    public static int findPage (final String [] pageNames, final String wanted)
    {
        if (pageNames == null)
            return -1;
        for (int i = 0; i < pageNames.length; i++)
            if (sameName (pageNames[i], wanted))
                return i;
        return -1;
    }


    /**
     * @param wanted The stored track name
     * @param nameAt The track name at a position
     * @param count The number of positions
     * @return The first position with that name, -1 if none
     */
    public static int findTrack (final String wanted, final IntFunction<String> nameAt, final int count)
    {
        for (int i = 0; i < count; i++)
            if (sameName (nameAt.apply (i), wanted))
                return i;
        return -1;
    }


    /**
     * @param assigned Which instrument slots have a track
     * @param current The current slot
     * @param delta +1 for the next, -1 for the previous slot
     * @return The next assigned slot in that direction (wrapping, possibly the current one), -1 if none is assigned
     */
    public static int nextAssigned (final boolean [] assigned, final int current, final int delta)
    {
        for (int step = 1; step <= assigned.length; step++)
        {
            final int slot = Math.floorMod (current + delta * step, assigned.length);
            if (assigned[slot])
                return slot;
        }
        return -1;
    }


    /**
     * @param slot 0-3
     * @return A-D
     */
    public static String slotLetter (final int slot)
    {
        return String.valueOf ((char) ('A' + slot));
    }


    private static boolean sameName (final String name, final String wanted)
    {
        return name != null && wanted != null && !wanted.isBlank () && name.trim ().equalsIgnoreCase (wanted.trim ());
    }
}
