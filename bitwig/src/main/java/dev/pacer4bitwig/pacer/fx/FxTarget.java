// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.fx;

/**
 * What an FX switch controls on the focused instrument.
 */
public enum FxTarget
{
    /** A remote control of the "Pacer" page, toggled between the ends of its mapping. */
    REMOTE,
    /** A device of the track's chain, switched on and off. */
    DEVICE,
    /** Nothing. */
    NONE;


    /** A remote control counts as on from the middle of its range. */
    public static final double ON_THRESHOLD = 0.5;


    /**
     * A track with a "Pacer" remote controls page is controlled through that page only; any other track through its
     * devices.
     *
     * @param hasPage True if the track has a page with the configured name
     * @param pageSelected True if the remote controls cursor is on that page
     * @param remoteMapped True if the switch's remote control is mapped
     * @param deviceExists True if the track has a device at the switch's position
     * @return The target
     */
    public static FxTarget resolve (final boolean hasPage, final boolean pageSelected, final boolean remoteMapped, final boolean deviceExists)
    {
        if (hasPage)
            return pageSelected && remoteMapped ? REMOTE : NONE;
        return deviceExists ? DEVICE : NONE;
    }


    /**
     * @param value A normalized remote control value
     * @return True if it counts as on
     */
    public static boolean isOn (final double value)
    {
        return value >= ON_THRESHOLD;
    }


    /**
     * @param value A normalized remote control value
     * @return The other end of the range
     */
    public static double toggled (final double value)
    {
        return isOn (value) ? 0 : 1;
    }
}
