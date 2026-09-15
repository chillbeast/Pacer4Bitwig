// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.looper;

/**
 * Decides whether a switch fires its tap on press or on release.
 * <p>
 * On press is tight but means a hold also runs the tap first; on release avoids that but adds the press duration as
 * latency. Without a hold action there is no conflict, so the tap always fires on press.
 */
public final class TapTiming
{
    private TapTiming ()
    {
        // Utility
    }


    /**
     * @param loopOnPress The user setting for loop switches
     * @param hold The hold action of loop switches
     * @return True to fire on press
     */
    public static boolean loopTapOnPress (final boolean loopOnPress, final HoldAction hold)
    {
        return loopOnPress || hold == HoldAction.NOTHING;
    }


    /**
     * A momentary hold needs the tap on press: it runs the tap again on release.
     *
     * @param tap The tap action
     * @param hold The hold action
     * @return True to fire on press
     */
    public static boolean actionTapOnPress (final Action tap, final Action hold)
    {
        return hold == Action.NONE || hold == Action.MOMENTARY || tap.isTimingCritical ();
    }
}
