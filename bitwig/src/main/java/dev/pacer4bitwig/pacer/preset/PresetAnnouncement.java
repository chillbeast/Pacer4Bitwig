// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer.preset;

/**
 * The value of the preset-loaded CC: which preset was selected (docs/PACER-MAP.md). Values are kind * 16 + variant
 * (kind 0 looper, 1 FX), except 127, the looper's legacy value. Unknown kinds count as the looper.
 * <p>
 * The variant used to choose an LED strategy. Colours are written live now, so it is ignored - but the values
 * themselves stay as they are, so presets already on a Pacer keep announcing themselves correctly.
 *
 * @param kind The preset
 */
public record PresetAnnouncement (PresetKind kind)
{
    /** Looper preset, the legacy value every existing preset sends. */
    public static final int  LOOPER               = 127;
    /** FX preset. */
    public static final int  FX                   = 17;

    private static final int KIND_FX              = 1;


    /**
     * Decode a preset-loaded CC value.
     *
     * @param value The CC value, 0-127
     * @return The announcement
     */
    public static PresetAnnouncement fromValue (final int value)
    {
        if (value == LOOPER)
            return new PresetAnnouncement (PresetKind.LOOPER);
        return new PresetAnnouncement (value >> 4 == KIND_FX ? PresetKind.FX : PresetKind.LOOPER);
    }


    /**
     * @return The CC value a preset sends for this announcement
     */
    public int toValue ()
    {
        return this.kind == PresetKind.FX ? FX : LOOPER;
    }
}
