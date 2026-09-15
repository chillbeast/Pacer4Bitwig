// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.fx;

import java.util.Arrays;
import java.util.Locale;


/**
 * The snapshots of one instrument: up to four stored states of its six FX switches, and which one is current. Stored in
 * the project as {@code current;values1;values2;values3;values4}: an empty part is an empty snapshot, values are six
 * comma separated numbers 0-1 (a device on/off, or a remote control value), empty where the switch controlled nothing.
 */
public final class SnapshotBank
{
    /** Most snapshots per instrument. */
    public static final int     MAX_SNAPSHOTS = 4;
    /** Values per snapshot: FX switches 1-6. */
    public static final int     NUM_VALUES    = 6;
    /** Smaller differences still match (values are rounded when stored). */
    private static final double TOLERANCE     = 0.01;

    private int                 current;
    private final double [] []  snapshots     = new double [MAX_SNAPSHOTS] [];


    /**
     * Read snapshots stored in the project. Never fails: anything unreadable counts as empty.
     *
     * @param text The stored text, may be null
     * @return The snapshots
     */
    public static SnapshotBank decode (final String text)
    {
        final SnapshotBank bank = new SnapshotBank ();
        if (text == null || text.isBlank ())
            return bank;
        final String [] parts = text.split (";", -1);
        bank.select (parseIndex (parts[0]));
        for (int i = 0; i < MAX_SNAPSHOTS && i + 1 < parts.length; i++)
            bank.snapshots[i] = parseValues (parts[i + 1]);
        return bank;
    }


    /**
     * @return The text to store in the project
     */
    public String encode ()
    {
        final StringBuilder sb = new StringBuilder ().append (this.current);
        for (final double [] values: this.snapshots)
        {
            sb.append (';');
            if (values == null)
                continue;
            for (int i = 0; i < values.length; i++)
            {
                if (i > 0)
                    sb.append (',');
                if (!Double.isNaN (values[i]))
                    sb.append (String.format (Locale.ROOT, "%.3f", Double.valueOf (values[i])));
            }
        }
        return sb.toString ();
    }


    /**
     * @param count Snapshots per instrument (setting)
     * @return The current snapshot, 0 to count - 1
     */
    public int getCurrent (final int count)
    {
        return Math.min (this.current, clampCount (count) - 1);
    }


    /**
     * Move to the next snapshot, wrapping around.
     *
     * @param count Snapshots per instrument (setting)
     * @return The new current snapshot
     */
    public int next (final int count)
    {
        this.current = (this.getCurrent (count) + 1) % clampCount (count);
        return this.current;
    }


    /**
     * @param index The new current snapshot
     */
    public void select (final int index)
    {
        this.current = Math.max (0, Math.min (MAX_SNAPSHOTS - 1, index));
    }


    /**
     * @param index 0-3
     * @return True if something was stored in the snapshot
     */
    public boolean isStored (final int index)
    {
        return index >= 0 && index < MAX_SNAPSHOTS && this.snapshots[index] != null;
    }


    /**
     * @param index 0-3
     * @return A copy of the stored values (NaN where the switch controlled nothing), null if empty
     */
    public double [] get (final int index)
    {
        return this.isStored (index) ? this.snapshots[index].clone () : null;
    }


    /**
     * @param index 0-3
     * @param values The values of FX switches 1-6, NaN where a switch controls nothing
     */
    public void store (final int index, final double [] values)
    {
        if (index < 0 || index >= MAX_SNAPSHOTS)
            return;
        final double [] copy = new double [NUM_VALUES];
        Arrays.fill (copy, Double.NaN);
        System.arraycopy (values, 0, copy, 0, Math.min (values.length, NUM_VALUES));
        this.snapshots[index] = copy;
    }


    /**
     * Does the current sound still match a snapshot? Switches that controlled nothing, then or now, are ignored.
     *
     * @param stored The snapshot's values
     * @param current The current values
     * @return True if nothing changed
     */
    public static boolean matches (final double [] stored, final double [] current)
    {
        for (int i = 0; i < NUM_VALUES; i++)
        {
            final double a = i < stored.length ? stored[i] : Double.NaN;
            final double b = i < current.length ? current[i] : Double.NaN;
            if (!Double.isNaN (a) && !Double.isNaN (b) && Math.abs (a - b) > TOLERANCE)
                return false;
        }
        return true;
    }


    private static int clampCount (final int count)
    {
        return Math.max (1, Math.min (MAX_SNAPSHOTS, count));
    }


    private static int parseIndex (final String text)
    {
        try
        {
            return Integer.parseInt (text.trim ());
        }
        catch (final NumberFormatException ex)
        {
            return 0;
        }
    }


    private static double [] parseValues (final String text)
    {
        if (text.isBlank ())
            return null;
        final double [] values = new double [NUM_VALUES];
        Arrays.fill (values, Double.NaN);
        final String [] items = text.split (",", -1);
        for (int i = 0; i < Math.min (items.length, NUM_VALUES); i++)
        {
            final String item = items[i].trim ();
            if (item.isEmpty ())
                continue;
            try
            {
                values[i] = Math.max (0, Math.min (1, Double.parseDouble (item)));
            }
            catch (final NumberFormatException ex)
            {
                // Unreadable: the switch controlled nothing
            }
        }
        return values;
    }
}
