// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.parameter.IParameter;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.led.LedClock;
import dev.pacer4bitwig.pacer.led.LedColour;
import dev.pacer4bitwig.pacer.led.LedState;
import dev.pacer4bitwig.pacer.live.LiveBoard;
import dev.pacer4bitwig.pacer.live.PacerColour;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.looper.ExpressionTarget;
import dev.pacer4bitwig.pacer.looper.PedalResponse;
import dev.pacer4bitwig.pacer.looper.TapTiming;
import dev.pacer4bitwig.pacer.midi.RawMidiSender;
import dev.pacer4bitwig.pacer.mode.Mode;
import dev.pacer4bitwig.pacer.mode.ModeBoard;
import dev.pacer4bitwig.pacer.mode.ModeMenu;
import dev.pacer4bitwig.pacer.mode.ModePainter;
import dev.pacer4bitwig.pacer.mode.ModeState;
import dev.pacer4bitwig.pacer.mode.SwitchLayout;
import dev.pacer4bitwig.pacer.mode.SwitchRole;
import dev.pacer4bitwig.pacer.preset.PresetAnnouncement;
import dev.pacer4bitwig.pacer.preset.PresetKind;

import java.util.Arrays;


/**
 * The Pacer's switches, jacks and pedals - and what its LEDs show - for the active {@link Mode}. The Pacer stays on
 * one preset; a mode decides what each switch does and is painted onto the device live (docs/LIVE-COLOURS-AND-MODES.md).
 * <p>
 * SW 6 is the mode switch everywhere: a tap toggles between the last two modes, a hold opens the {@link ModeMenu}
 * where SW 1-5 pick a mode and SW A-D navigate. {@link Action#MOMENTARY} is handled here. The setup only wires
 * hardware to these methods.
 */
public class PacerController
{
    private final IHost              host;
    private final PacerConfiguration configuration;
    private final LooperController   looper;
    private final FxController       fx;
    private final LiveBoard          board;
    private final ModeState          modes                 = new ModeState (Mode.LOOP);
    /**
     * The colour each switch last asked for, filled in by {@link #getLedCode(int)} during the flush. Painting reads
     * this cache instead of querying Bitwig again: the flush already worked it out, and asking twice both doubled
     * the load and let the two answers disagree.
     */
    private final PacerColour []     switchColours         = new PacerColour [PacerMap.NUM_SWITCHES];
    private RawMidiSender            midiSender            = RawMidiSender.NONE;
    /** Called when the active mode changed, so the setup can re-bind the pedals. */
    private Runnable                 modeListener;
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
     * @param board The Pacer's live colours and display name
     */
    public PacerController (final IHost host, final PacerConfiguration configuration, final LooperController looper, final FxController fx, final LiveBoard board)
    {
        this.host = host;
        this.configuration = configuration;
        this.looper = looper;
        this.fx = fx;
        this.board = board;
    }


    /**
     * @param midiSender Sends pedal MIDI into Bitwig
     */
    public void setMidiSender (final RawMidiSender midiSender)
    {
        this.midiSender = midiSender;
    }


    /**
     * @param modeListener Runs after the active mode changed
     */
    public void setModeListener (final Runnable modeListener)
    {
        this.modeListener = modeListener;
    }


    // ---- Modes --------------------------------------------------------------------------------------------------

    /**
     * @return The active mode
     */
    public Mode getMode ()
    {
        return this.modes.getActive ();
    }


    /**
     * The board of the active mode. {@link Mode#CUSTOM} is laid out in the settings rather than in code, so every
     * read of a layout goes through here.
     *
     * @return The board
     */
    private ModeBoard getBoard ()
    {
        final Mode mode = this.modes.getActive ();
        return mode == Mode.CUSTOM ? this.configuration.getCustomBoard () : mode;
    }


    /**
     * Start in the mode the settings ask for. Called once the extension is running.
     */
    public void applyStartupMode ()
    {
        this.modes.activate (this.configuration.getModeAtStartup ());
        this.repaintAll ();
    }


    /**
     * Darken the Pacer and say so, for when the extension stops driving it.
     */
    public void blackout ()
    {
        this.board.blackout ("OFF");
    }


    /**
     * @return True while SW 6 is held and the mode menu is showing
     */
    public boolean isMenuOpen ()
    {
        return this.modes.isMenuOpen ();
    }


    /**
     * Paint the active mode (or the menu) onto the Pacer. Only switches that would change are written.
     */
    public void paint ()
    {
        ModePainter.paint (this.board, this.modes, this.getBoard (), this.getDisplayName (), this::stateColour);
    }


    /**
     * What the Pacer's display should read: the mode's name, or - when the mode has something more useful to say and
     * the setting allows it - what it is doing. The FX mode names the focused instrument, the song mode names the row.
     *
     * @return At most five characters' worth; the writer cuts it
     */
    private String getDisplayName ()
    {
        final ModeBoard board = this.getBoard ();
        if (!this.configuration.isShowContext ())
            return board.getDisplayName ();
        final String context = switch (this.modes.getActive ())
        {
            case FX -> this.fx.getFocusedInstrumentName ();
            case SONG -> this.looper.getRowDisplayName ();
            default -> "";
        };
        return context == null || context.isBlank () ? board.getDisplayName () : context.trim ();
    }


    /**
     * The colour a switch shows while it is lit, as of the last flush. {@link PacerColour#OFF} means "no state of its
     * own", and the switch keeps the colour its mode gave it.
     *
     * @param switchIndex 0-9
     * @return The colour
     */
    private PacerColour stateColour (final int switchIndex)
    {
        final PacerColour colour = this.switchColours[switchIndex];
        return colour == null ? PacerColour.OFF : colour;
    }


    /**
     * Forget what the Pacer shows and paint everything again - the device discards every live edit when a preset is
     * selected on it.
     */
    public void repaintAll ()
    {
        this.board.invalidate ();
        this.paint ();
    }


    // ---- Stomp switches -----------------------------------------------------------------------------------------

    /**
     * @param switchIndex 0-9
     * @return True if the switch fires its tap on press
     */
    public boolean isTapOnPress (final int switchIndex)
    {
        return switch (this.role (switchIndex))
        {
            // The mode switch must let a hold pre-empt its tap, and menu slots should answer at once
            case MODE_SWITCH -> false;
            case MODE_SLOT, NAVIGATION -> true;
            case LOOP_TRACK -> this.looper.isLoopSwitchTapOnPress ();
            case ACTION -> TapTiming.actionTapOnPress (this.getSwitchTap (switchIndex), this.getSwitchHold (switchIndex));
            case NONE -> false;
        };
    }


    /**
     * @param switchIndex 0-9
     * @return How much longer than a normal hold the switch must stay down before its hold runs
     */
    public long getExtraHoldMillis (final int switchIndex)
    {
        return switch (this.role (switchIndex))
        {
            // The menu opens at the normal hold time - waiting longer for it would feel broken
            case MODE_SWITCH, MODE_SLOT, NAVIGATION, NONE -> 0;
            case LOOP_TRACK -> this.looper.getLoopSwitchExtraHoldMillis ();
            case ACTION -> this.getExtraHoldMillis (this.getSwitchHold (switchIndex));
        };
    }


    /**
     * @param switchIndex 0-9
     * @return True if a double-tap action is assigned to the switch
     */
    public boolean isDoubleTapEnabled (final int switchIndex)
    {
        return switch (this.role (switchIndex))
        {
            case MODE_SWITCH, MODE_SLOT, NAVIGATION, NONE -> false;
            case LOOP_TRACK -> this.looper.isLoopSwitchDoubleTapEnabled ();
            case ACTION -> this.getSwitchDoubleTap (switchIndex) != Action.NONE;
        };
    }


    /**
     * A switch was tapped.
     *
     * @param switchIndex 0-9
     */
    public void tap (final int switchIndex)
    {
        switch (this.role (switchIndex))
        {
            case MODE_SWITCH -> {
                // Closes the menu if it is open, otherwise goes back to the mode before this one
                if (this.modes.tapModeSwitch ())
                    this.modeChanged ();
                else
                    this.paint ();
            }
            case MODE_SLOT -> {
                // Picking a mode closes the menu, so one foot can hold, let go, and tap
                if (this.modes.select (switchIndex))
                    this.modeChanged ();
                else
                    this.paint ();
            }
            // Navigation leaves the menu open so it can be pressed again
            case NAVIGATION -> this.perform (ModeMenu.actionAt (switchIndex));
            case LOOP_TRACK -> this.looper.loopSwitchTap (switchIndex);
            case ACTION -> this.perform (this.getSwitchTap (switchIndex));
            case NONE -> {
                // Nothing assigned
            }
        }
    }


    /**
     * A switch was double-tapped (the first tap has already run).
     *
     * @param switchIndex 0-9
     */
    public void doubleTap (final int switchIndex)
    {
        switch (this.role (switchIndex))
        {
            case LOOP_TRACK -> this.looper.loopSwitchDoubleTap (switchIndex);
            case ACTION -> this.perform (this.getSwitchDoubleTap (switchIndex));
            default -> {
                // The mode switch and the menu have no double-tap
            }
        }
    }


    /**
     * A switch was held.
     *
     * @param switchIndex 0-9
     */
    public void hold (final int switchIndex)
    {
        if (Mode.isModeSwitch (switchIndex))
        {
            // The menu stays open when the foot comes off - a foot cannot hold one switch and press another
            this.modes.openMenu ();
            this.paint ();
            return;
        }
        this.momentarySwitches[switchIndex] = null;
        switch (this.role (switchIndex))
        {
            case LOOP_TRACK -> this.looper.loopSwitchHold (switchIndex);
            case ACTION -> {
                final Action hold = this.getSwitchHold (switchIndex);
                if (hold == Action.MOMENTARY)
                    this.momentarySwitches[switchIndex] = this.getSwitchTap (switchIndex);
                else
                    this.perform (hold);
            }
            default -> {
                // While the menu is open the other switches belong to it, and it has no hold actions
            }
        }
    }


    /**
     * A switch was released.
     *
     * @param switchIndex 0-9
     */
    public void release (final int switchIndex)
    {
        if (Mode.isModeSwitch (switchIndex))
            // Nothing: the menu latches, and a tap is what closes it again
            return;
        final Action momentary = this.momentarySwitches[switchIndex];
        this.momentarySwitches[switchIndex] = null;
        if (momentary != null)
            this.perform (momentary);
        if (this.role (switchIndex) == SwitchRole.LOOP_TRACK)
            this.looper.loopSwitchRelease (switchIndex);
    }


    private void modeChanged ()
    {
        Arrays.fill (this.momentarySwitches, null);
        // Saved with the project, for "Mode at startup = whatever this project used last"
        this.configuration.setProjectMode (this.modes.getActive ());
        this.paint ();
        if (this.modeListener != null)
            this.modeListener.run ();
        if (this.configuration.getNotificationLevel ().shows (true))
            this.host.showNotification (this.modes.getActive ().getLabel ());
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
     * Get the light code of a switch right now. With the colours written live, this only decides whether the LED is
     * lit: the colour itself comes from {@link LiveBoard}.
     *
     * @param switchIndex 0-9
     * @return The code, see {@link LedState#code(LedClock)}
     */
    public int getLedCode (final int switchIndex)
    {
        final long now = System.currentTimeMillis ();
        final int testCode = this.looper.getLedTestCode (now);
        if (testCode >= 0)
        {
            this.switchColours[switchIndex] = LedColour.fromCode (testCode).toPacer ();
            return testCode;
        }

        if (this.modes.isMenuOpen ())
        {
            // The menu paints its own colours; the code only says lit or dark
            this.switchColours[switchIndex] = PacerColour.OFF;
            return ModeMenu.colourAt (switchIndex, this.modes.getActive ()) == PacerColour.OFF ? 0 : LedColour.WHITE.ordinal ();
        }

        if (this.getMode () == Mode.LOOP && switchIndex >= PacerMap.FIRST_TOP_ROW_SWITCH)
        {
            final int beatCode = this.looper.getBeatCounterCode (switchIndex - PacerMap.FIRST_TOP_ROW_SWITCH, now);
            if (beatCode >= 0)
            {
                // The counter is dark on the beats that are not this switch's. Only a lit code carries a colour;
                // caching the dark ones would flip every switch's colour on every beat, and each flip is a SysEx.
                if (beatCode > 0)
                    this.switchColours[switchIndex] = LedColour.fromCode (beatCode).toPacer ();
                return beatCode;
            }
        }

        final LedClock ledClock = this.looper.getLedClock (now);
        final LedState state = switch (this.role (switchIndex))
        {
            case LOOP_TRACK -> this.looper.loopSwitchLed (switchIndex);
            // The mode switch is always lit: it is the way back to everything else
            case MODE_SWITCH -> LedState.solid (LedColour.WHITE);
            case ACTION -> {
                final Action action = this.getSwitchTap (switchIndex);
                yield action.isFx () ? this.fx.actionLed (action) : this.looper.actionLed (action, ledClock);
            }
            default -> LedState.DARK;
        };
        this.switchColours[switchIndex] = state.colour ().toPacer ();
        return state.code (ledClock);
    }


    // ---- Presets and periodic work ----------------------------------------------------------------------------------

    /**
     * The preset-loaded CC arrived. Selecting a preset on the Pacer discards every live edit, so the whole board has
     * to be written again.
     *
     * @param value The CC value
     */
    public void presetAnnounced (final int value)
    {
        // Which of our presets it is decides the mode; selecting it deliberately is a deliberate mode change
        final PresetAnnouncement announcement = PresetAnnouncement.fromValue (value);
        this.modes.activate (announcement.kind () == PresetKind.FX ? Mode.FX : Mode.LOOP);
        this.repaintAll ();
        if (this.modeListener != null)
            this.modeListener.run ();
    }


    /**
     * Runs on every tick.
     */
    public void tick ()
    {
        this.looper.tick ();
        this.fx.tick ();
        // Colours follow state; the board drops everything that would not change, so this is almost always silent
        this.paint ();
    }


    /**
     * Run an assignable action.
     *
     * @param action The action
     */
    public void perform (final Action action)
    {
        if (action.isMode ())
        {
            if (this.performMode (action))
                this.modeChanged ();
            return;
        }
        if (action.isFx ())
            this.fx.perform (action);
        else
            this.looper.perform (action);
    }


    private boolean performMode (final Action action)
    {
        return switch (action)
        {
            case MODE_NEXT -> this.modes.next ();
            case MODE_TOGGLE -> this.modes.toggle ();
            case MODE_LOOP -> this.modes.activate (Mode.LOOP);
            case MODE_FX -> this.modes.activate (Mode.FX);
            case MODE_MIX -> this.modes.activate (Mode.MIX);
            case MODE_SONG -> this.modes.activate (Mode.SONG);
            default -> false;
        };
    }


    // ---- Helpers ------------------------------------------------------------------------------------------------

    /** The mode decides which switches are loop tracks, the project decides how many tracks there are. */
    private SwitchRole role (final int switchIndex)
    {
        return SwitchRole.of (switchIndex, this.getBoard (), this.modes.isMenuOpen (), this.configuration.getLoopTrackCount ());
    }


    private SwitchLayout getLayout (final int switchIndex)
    {
        return this.getBoard ().getLayout (switchIndex);
    }


    private Action getSwitchTap (final int switchIndex)
    {
        return this.getLayout (switchIndex).tap ();
    }


    private Action getSwitchDoubleTap (final int switchIndex)
    {
        return this.getLayout (switchIndex).doubleTap ();
    }


    private Action getSwitchHold (final int switchIndex)
    {
        return this.getLayout (switchIndex).hold ();
    }


    private ExpressionTarget getExpressionTarget (final int index)
    {
        return this.configuration.getExpressionTarget (this.getMode (), index);
    }


    private long getExtraHoldMillis (final Action hold)
    {
        return hold.isDestructive () ? this.configuration.getClearHoldTime ().getExtraMillis () : 0;
    }

}
