// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.mode;

/**
 * Which mode is active, and the SW 6 gestures that change it.
 * <p>
 * A <b>hold</b> of SW 6 opens the {@link ModeMenu} and it <b>stays open</b> when the foot comes off - a foot cannot
 * hold one switch and press another. A <b>tap</b> closes the menu again if it is open, and otherwise toggles between
 * the last two modes. Tapping a mode slot goes there and closes the menu; the navigation slots leave it open so they
 * can be pressed repeatedly.
 * <p>
 * Pure state - the caller does the repainting.
 */
public final class ModeState
{
    private Mode    active;
    private Mode    previous;
    private boolean menuOpen;


    /**
     * Constructor.
     *
     * @param initial The mode to start in
     */
    public ModeState (final Mode initial)
    {
        this.active = initial;
        this.previous = initial;
    }


    /**
     * @return The active mode
     */
    public Mode getActive ()
    {
        return this.active;
    }


    /**
     * @return The mode a tap of SW 6 goes back to
     */
    public Mode getPrevious ()
    {
        return this.previous;
    }


    /**
     * @return True while SW 6 is held and the menu is showing
     */
    public boolean isMenuOpen ()
    {
        return this.menuOpen;
    }


    /**
     * SW 6 was held: show the menu. The mode does not change yet.
     */
    public void openMenu ()
    {
        this.menuOpen = true;
    }


    /**
     * Close the menu.
     *
     * @return True if the menu was open, so the caller knows to repaint
     */
    public boolean closeMenu ()
    {
        final boolean wasOpen = this.menuOpen;
        this.menuOpen = false;
        return wasOpen;
    }


    /**
     * SW 6 was tapped: close the menu if it is open, otherwise go back to the mode before this one. A tap never
     * follows a hold, so opening the menu does not immediately close it again.
     *
     * @return True if the active mode changed
     */
    public boolean tapModeSwitch ()
    {
        if (this.menuOpen)
        {
            this.menuOpen = false;
            return false;
        }
        return this.toggle ();
    }


    /**
     * Go back to the mode before this one.
     *
     * @return True if the active mode changed
     */
    public boolean toggle ()
    {
        return this.activate (this.previous);
    }


    /**
     * A mode slot was tapped while the menu is open: go there and close the menu.
     *
     * @param switchIndex 0-9
     * @return True if the active mode changed
     */
    public boolean select (final int switchIndex)
    {
        final Mode mode = ModeMenu.modeAt (switchIndex);
        this.menuOpen = false;
        return this.activate (mode);
    }


    /**
     * Move on to the next mode that has a menu slot.
     *
     * @return True if the active mode changed
     */
    public boolean next ()
    {
        return this.activate (this.active.next ());
    }


    /**
     * Go to a mode, remembering the one being left so a tap of SW 6 comes back to it.
     *
     * @param mode The mode
     * @return True if the active mode changed
     */
    public boolean activate (final Mode mode)
    {
        if (mode == null || mode == this.active)
            return false;
        this.previous = this.active;
        this.active = mode;
        return true;
    }
}
