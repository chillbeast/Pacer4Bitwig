// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.util;

import java.util.function.Consumer;
import java.util.function.Supplier;


/**
 * Optional logging to Bitwig's controller console, for bug reports from the hardware: what each press was taken for,
 * which action ran, mode and shift changes, the preset check, and - at the highest level - every SysEx message. The
 * message is only built when its level is on.
 */
public final class Diagnostics
{
    /** How much to log. */
    public enum Level implements Labelled
    {
        /** Nothing. */
        OFF ("Off"),
        /** Presses, actions, modes, the shift layer and the preset check. */
        ACTIONS ("Presses, actions and modes"),
        /** All of that and every SysEx message in and out. */
        ALL ("Everything, with every SysEx message");


        private final String label;


        Level (final String label)
        {
            this.label = label;
        }


        /** {@inheritDoc} */
        @Override
        public String getLabel ()
        {
            return this.label;
        }
    }


    /** Logs nothing. */
    public static final Diagnostics NONE = new Diagnostics (line -> {
        // Nowhere
    }, () -> Level.OFF);

    private final Consumer<String>  console;
    private final Supplier<Level>   level;


    /**
     * Constructor.
     *
     * @param console Where lines go
     * @param level How much to log, asked on every line
     */
    public Diagnostics (final Consumer<String> console, final Supplier<Level> level)
    {
        this.console = console;
        this.level = level;
    }


    /**
     * @param wanted A level
     * @return True if lines of that level are logged
     */
    public boolean isOn (final Level wanted)
    {
        return wanted != Level.OFF && this.level.get ().ordinal () >= wanted.ordinal ();
    }


    /**
     * Log a line, if its level is on.
     *
     * @param wanted The line's level
     * @param line Builds the line
     */
    public void log (final Level wanted, final Supplier<String> line)
    {
        if (this.isOn (wanted))
            this.console.accept ("PACER " + line.get ());
    }
}
