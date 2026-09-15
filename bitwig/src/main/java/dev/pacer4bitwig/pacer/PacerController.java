// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.parameter.IParameter;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.led.LedClock;
import dev.pacer4bitwig.pacer.led.LedState;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.looper.ExpressionTarget;
import dev.pacer4bitwig.pacer.looper.PedalResponse;
import dev.pacer4bitwig.pacer.looper.TapTiming;
import dev.pacer4bitwig.pacer.midi.RawMidiSender;
import dev.pacer4bitwig.pacer.preset.PresetAnnouncement;
import dev.pacer4bitwig.pacer.preset.PresetKind;

import java.util.Arrays;


/**
 * The Pacer's switches, jacks and pedals - and what its LEDs show - for the preset selected on the Pacer. The looper
 * preset has loop switches ({@link LooperController}); on the FX preset every switch runs assignable actions. Looper
 * and FX ({@link FxController}) actions can be assigned on both presets; {@link Action#MOMENTARY} is handled here.
 * The setup only wires hardware to these methods.
 */
public class PacerController
{
    private final IHost              host;
    private final PacerConfiguration configuration;
    private final LooperController   looper;
    private final FxController       fx;
    private RawMidiSender            midiSender            = RawMidiSender.NONE;
    /** The tap action a momentary hold runs again on release, null if none. */
    private final Action []          momentarySwitches     = new Action [PacerMap.NUM_SWITCHES];
    private final Action []          momentaryFootswitches = new Action [PacerMap.NUM_FOOTSWITCHES];


    /**
     * Constructor.
     *
     * @param host The host
     * @param configuration The configuration
     * @param looper The looper
     * @param fx The FX preset
     */
    public PacerController (final IHost host, final PacerConfiguration configuration, final LooperController looper, final FxController fx)
    {
        this.host = host;
        this.configuration = configuration;
        this.looper = looper;
        this.fx = fx;
    }


    /**
     * @param midiSender Sends pedal MIDI into Bitwig
     */
    public void setMidiSender (final RawMidiSender midiSender)
    {
        this.midiSender = midiSender;
    }


    // ---- Stomp switches -----------------------------------------------------------------------------------------

    /**
     * @param switchIndex 0-9
     * @return True if the switch fires its tap on press
     */
    public boolean isTapOnPress (final int switchIndex)
    {
        if (this.isLoopSwitch (switchIndex))
            return this.looper.isLoopSwitchTapOnPress ();
        return TapTiming.actionTapOnPress (this.getSwitchTap (switchIndex), this.getSwitchHold (switchIndex));
    }


    /**
     * @param switchIndex 0-9
     * @return How much longer than a normal hold the switch must stay down before its hold runs
     */
    public long getExtraHoldMillis (final int switchIndex)
    {
        if (this.isLoopSwitch (switchIndex))
            return this.looper.getLoopSwitchExtraHoldMillis ();
        return this.getExtraHoldMillis (this.getSwitchHold (switchIndex));
    }


    /**
     * @param switchIndex 0-9
     * @return True if a double-tap action is assigned to the switch
     */
    public boolean isDoubleTapEnabled (final int switchIndex)
    {
        if (this.isLoopSwitch (switchIndex))
            return this.looper.isLoopSwitchDoubleTapEnabled ();
        return this.getSwitchDoubleTap (switchIndex) != Action.NONE;
    }


    /**
     * A switch was tapped.
     *
     * @param switchIndex 0-9
     */
    public void tap (final int switchIndex)
    {
        if (this.isLoopSwitch (switchIndex))
            this.looper.loopSwitchTap (switchIndex);
        else
            this.perform (this.getSwitchTap (switchIndex));
    }


    /**
     * A switch was double-tapped (the first tap has already run).
     *
     * @param switchIndex 0-9
     */
    public void doubleTap (final int switchIndex)
    {
        if (this.isLoopSwitch (switchIndex))
            this.looper.loopSwitchDoubleTap (switchIndex);
        else
            this.perform (this.getSwitchDoubleTap (switchIndex));
    }


    /**
     * A switch was held.
     *
     * @param switchIndex 0-9
     */
    public void hold (final int switchIndex)
    {
        this.momentarySwitches[switchIndex] = null;
        if (this.isLoopSwitch (switchIndex))
        {
            this.looper.loopSwitchHold (switchIndex);
            return;
        }
        final Action hold = this.getSwitchHold (switchIndex);
        if (hold == Action.MOMENTARY)
            this.momentarySwitches[switchIndex] = this.getSwitchTap (switchIndex);
        else
            this.perform (hold);
    }


    /**
     * A switch was released.
     *
     * @param switchIndex 0-9
     */
    public void release (final int switchIndex)
    {
        final Action momentary = this.momentarySwitches[switchIndex];
        this.momentarySwitches[switchIndex] = null;
        if (momentary != null)
            this.perform (momentary);
        if (this.isLoopSwitch (switchIndex))
            this.looper.loopSwitchRelease (switchIndex);
    }


    // ---- Footswitch jacks ---------------------------------------------------------------------------------------

    /**
     * @param index 0-3
     * @return True if the footswitch jack fires its tap on press
     */
    public boolean isFootswitchTapOnPress (final int index)
    {
        return TapTiming.actionTapOnPress (this.configuration.getFootswitchTap (index), this.configuration.getFootswitchHold (index));
    }


    /**
     * @param index 0-3
     * @return How much longer than a normal hold the jack must stay down before its hold runs
     */
    public long getFootswitchExtraHoldMillis (final int index)
    {
        return this.getExtraHoldMillis (this.configuration.getFootswitchHold (index));
    }


    /**
     * @param index 0-3
     * @return True if a double-tap action is assigned to the jack
     */
    public boolean isFootswitchDoubleTapEnabled (final int index)
    {
        return this.configuration.getFootswitchDoubleTap (index) != Action.NONE;
    }


    /**
     * A footswitch jack was tapped.
     *
     * @param index 0-3
     */
    public void footswitchTap (final int index)
    {
        this.perform (this.configuration.getFootswitchTap (index));
    }


    /**
     * A footswitch jack was double-tapped.
     *
     * @param index 0-3
     */
    public void footswitchDoubleTap (final int index)
    {
        this.perform (this.configuration.getFootswitchDoubleTap (index));
    }


    /**
     * A footswitch jack was held.
     *
     * @param index 0-3
     */
    public void footswitchHold (final int index)
    {
        this.momentaryFootswitches[index] = null;
        final Action hold = this.configuration.getFootswitchHold (index);
        if (hold == Action.MOMENTARY)
            this.momentaryFootswitches[index] = this.configuration.getFootswitchTap (index);
        else
            this.perform (hold);
    }


    /**
     * A footswitch jack was released.
     *
     * @param index 0-3
     */
    public void footswitchRelease (final int index)
    {
        final Action momentary = this.momentaryFootswitches[index];
        this.momentaryFootswitches[index] = null;
        if (momentary != null)
            this.perform (momentary);
    }


    // ---- Expression pedals --------------------------------------------------------------------------------------

    /**
     * The parameter a pedal is bound to directly. Only parameter targets with a linear, full-range response are bound;
     * everything else (MIDI and FX targets, curves, ranges) goes through {@link #pedalMoved(int, int)}.
     *
     * @param index 0-1
     * @return The parameter, or null to route the pedal through its command
     */
    public IParameter getPedalBinding (final int index)
    {
        final ExpressionTarget target = this.getExpressionTarget (index);
        if (target.getKind () != ExpressionTarget.Kind.PARAMETER || !this.configuration.getPedalResponse (index).isIdentity ())
            return null;
        return this.looper.getExpressionParameter (target);
    }


    /**
     * An expression pedal moved and is not bound directly to a parameter.
     *
     * @param index 0-1
     * @param value The pedal position, 0-127
     */
    public void pedalMoved (final int index, final int value)
    {
        final ExpressionTarget target = this.getExpressionTarget (index);
        final PedalResponse response = this.configuration.getPedalResponse (index);

        switch (target.getKind ())
        {
            case CC, CHANNEL_PRESSURE, PITCH_BEND_UP -> {
                final int [] message = target.toMidi (response.map (value), this.configuration.getPedalMidiChannel ());
                if (message != null)
                    this.midiSender.send (message[0], message[1], message[2]);
            }
            case FX_REMOTE -> this.fx.setRemoteValue (target.getRemoteIndex (), response.map (value / 127.0));
            case PARAMETER -> {
                final IParameter parameter = this.looper.getExpressionParameter (target);
                if (parameter != null)
                    parameter.setNormalizedValue (response.map (value / 127.0));
            }
            case NONE -> {
                // Not assigned
            }
        }
    }


    // ---- LEDs ---------------------------------------------------------------------------------------------------

    /**
     * Get the light code of a switch right now.
     *
     * @param switchIndex 0-9
     * @return The code, see {@link LedState#code(LedClock)}
     */
    public int getLedCode (final int switchIndex)
    {
        final long now = System.currentTimeMillis ();
        final int testCode = this.looper.getLedTestCode (now);
        if (testCode >= 0)
            return testCode;

        if (this.isLooperPreset () && switchIndex >= PacerMap.FIRST_TOP_ROW_SWITCH)
        {
            final int beatCode = this.looper.getBeatCounterCode (switchIndex - PacerMap.FIRST_TOP_ROW_SWITCH, now);
            if (beatCode >= 0)
                return beatCode;
        }

        final LedClock ledClock = this.looper.getLedClock (now);
        final LedState state;
        if (this.isLoopSwitch (switchIndex))
            state = this.looper.loopSwitchLed (switchIndex);
        else
        {
            final Action action = this.getSwitchTap (switchIndex);
            state = action.isFx () ? this.fx.actionLed (action) : this.looper.actionLed (action, ledClock);
        }
        return state.code (ledClock);
    }


    // ---- Presets and periodic work ----------------------------------------------------------------------------------

    /**
     * The preset-loaded CC arrived: remember the preset and its LED variant.
     *
     * @param value The CC value
     */
    public void presetAnnounced (final int value)
    {
        final PresetAnnouncement announcement = PresetAnnouncement.fromValue (value);
        this.configuration.setAnnouncedLedMode (announcement.ledMode ());
        this.configuration.setActivePreset (announcement.kind ());
    }


    /**
     * The active preset changed: forget half-finished gestures and say which preset is active.
     */
    public void presetChanged ()
    {
        Arrays.fill (this.momentarySwitches, null);
        Arrays.fill (this.momentaryFootswitches, null);
        if (!this.isLooperPreset ())
            this.fx.showFocus ();
        else if (this.configuration.getNotificationLevel ().shows (true))
            this.host.showNotification ("Looper preset");
    }


    /**
     * Runs on every tick.
     */
    public void tick ()
    {
        this.looper.tick ();
        this.fx.tick ();
    }


    /**
     * Run an assignable action.
     *
     * @param action The action
     */
    public void perform (final Action action)
    {
        if (action.isFx ())
            this.fx.perform (action);
        else
            this.looper.perform (action);
    }


    // ---- Helpers ------------------------------------------------------------------------------------------------

    private boolean isLooperPreset ()
    {
        return this.configuration.getActivePreset () == PresetKind.LOOPER;
    }


    private boolean isLoopSwitch (final int switchIndex)
    {
        return this.isLooperPreset () && this.looper.isLoopSwitch (switchIndex);
    }


    private Action getSwitchTap (final int switchIndex)
    {
        return this.isLooperPreset () ? this.configuration.getSwitchTap (switchIndex) : this.configuration.getFxSwitchTap (switchIndex);
    }


    private Action getSwitchDoubleTap (final int switchIndex)
    {
        return this.isLooperPreset () ? this.configuration.getSwitchDoubleTap (switchIndex) : this.configuration.getFxSwitchDoubleTap (switchIndex);
    }


    private Action getSwitchHold (final int switchIndex)
    {
        return this.isLooperPreset () ? this.configuration.getSwitchHold (switchIndex) : this.configuration.getFxSwitchHold (switchIndex);
    }


    private ExpressionTarget getExpressionTarget (final int index)
    {
        return this.isLooperPreset () ? this.configuration.getExpressionTarget (index) : this.configuration.getFxExpressionTarget (index);
    }


    private long getExtraHoldMillis (final Action hold)
    {
        return hold.isDestructive () ? this.configuration.getClearHoldTime ().getExtraMillis () : 0;
    }
}
