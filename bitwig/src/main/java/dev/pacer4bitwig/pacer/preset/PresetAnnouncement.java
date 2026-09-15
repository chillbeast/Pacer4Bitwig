// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.preset;

import dev.pacer4bitwig.pacer.led.LedMode;


/**
 * The value of the preset-loaded CC: which preset was selected and its LED variant (docs/PACER-MAP.md). Values are
 * kind * 16 + variant (kind 0 looper, 1 FX; variant 1 two-colour, 2 multi-colour), except 127, the looper's legacy
 * two-colour value. Unknown kinds count as the looper, unknown variants as two-colour.
 *
 * @param kind The preset
 * @param ledMode TWO_COLOUR or MULTI_COLOUR
 */
public record PresetAnnouncement (PresetKind kind, LedMode ledMode)
{
    /** Looper preset, two-colour LEDs. */
    public static final int  LOOPER_TWO_COLOUR    = 127;
    /** Looper preset, multi-colour LEDs. */
    public static final int  LOOPER_MULTI_COLOUR  = 2;
    /** FX preset, two-colour LEDs. */
    public static final int  FX_TWO_COLOUR        = 17;
    /** FX preset, multi-colour LEDs. */
    public static final int  FX_MULTI_COLOUR      = 18;

    private static final int KIND_FX              = 1;
    private static final int VARIANT_MULTI_COLOUR = 2;


    /**
     * Decode a preset-loaded CC value.
     *
     * @param value The CC value, 0-127
     * @return The announcement
     */
    public static PresetAnnouncement fromValue (final int value)
    {
        if (value == LOOPER_TWO_COLOUR)
            return new PresetAnnouncement (PresetKind.LOOPER, LedMode.TWO_COLOUR);
        final PresetKind kind = value >> 4 == KIND_FX ? PresetKind.FX : PresetKind.LOOPER;
        final LedMode ledMode = (value & 0x0F) == VARIANT_MULTI_COLOUR ? LedMode.MULTI_COLOUR : LedMode.TWO_COLOUR;
        return new PresetAnnouncement (kind, ledMode);
    }


    /**
     * @return The CC value a preset sends for this announcement
     */
    public int toValue ()
    {
        final boolean multi = this.ledMode == LedMode.MULTI_COLOUR;
        if (this.kind == PresetKind.FX)
            return multi ? FX_MULTI_COLOUR : FX_TWO_COLOUR;
        return multi ? LOOPER_MULTI_COLOUR : LOOPER_TWO_COLOUR;
    }
}
