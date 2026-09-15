// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.util;

/**
 * An option shown in an enum setting of the Bitwig controller preferences.
 */
public interface Labelled
{
    /**
     * Get the label shown in the settings.
     *
     * @return The label
     */
    String getLabel ();


    /**
     * Get all labels, in declaration order.
     *
     * @param values The enum values
     * @return The labels
     */
    static String [] labels (final Labelled [] values)
    {
        final String [] labels = new String [values.length];
        for (int i = 0; i < values.length; i++)
            labels[i] = values[i].getLabel ();
        return labels;
    }


    /**
     * Look up a value by its label.
     *
     * @param values The enum values
     * @param label The label
     * @param fallback Returned if the label is unknown
     * @return The value
     * @param <E> The enum type
     */
    static <E extends Labelled> E fromLabel (final E [] values, final String label, final E fallback)
    {
        for (final E value: values)
            if (value.getLabel ().equals (label))
                return value;
        return fallback;
    }
}
