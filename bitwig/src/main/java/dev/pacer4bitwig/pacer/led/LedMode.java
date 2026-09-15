// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.led;

import dev.pacer4bitwig.util.Labelled;


/**
 * How LED feedback is sent to the Pacer (see docs/PACER-MAP.md).
 */
public enum LedMode implements Labelled
{
    /** Follow the variant the looper preset announces with its preset-loaded message. */
    AUTO ("Automatic (the looper preset tells)"),
    /** Step 1 only: 127 = on colour, 0 = off colour. State is shown with blink patterns. */
    TWO_COLOUR ("Two-colour (safe)"),
    /** Steps 2-6 act as colour slots on the same LED. Needs the multi-colour preset. */
    MULTI_COLOUR ("Multi-colour (experimental)");


    /** Preset-loaded CC value of the two-colour preset variant. */
    public static final int PRESET_VALUE_TWO_COLOUR   = 127;
    /** Preset-loaded CC value of the multi-colour preset variant. */
    public static final int PRESET_VALUE_MULTI_COLOUR = 2;

    private final String    label;


    LedMode (final String label)
    {
        this.label = label;
    }


    /** {@inheritDoc} */
    @Override
    public String getLabel ()
    {
        return this.label;
    }


    /**
     * The mode LEDs are actually driven in.
     *
     * @param setting The LED mode setting
     * @param announced The variant the preset announced (AUTO counts as two-colour)
     * @return TWO_COLOUR or MULTI_COLOUR
     */
    public static LedMode resolve (final LedMode setting, final LedMode announced)
    {
        final LedMode mode = setting == AUTO ? announced : setting;
        return mode == MULTI_COLOUR ? MULTI_COLOUR : TWO_COLOUR;
    }


    /**
     * The variant a preset-loaded CC value announces. Anything but the multi-colour value (older presets send 127)
     * means two-colour.
     *
     * @param value The CC value
     * @return TWO_COLOUR or MULTI_COLOUR
     */
    public static LedMode fromPresetValue (final int value)
    {
        return value == PRESET_VALUE_MULTI_COLOUR ? MULTI_COLOUR : TWO_COLOUR;
    }
}
