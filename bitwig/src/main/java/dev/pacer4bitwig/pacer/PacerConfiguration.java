// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.IEnumSetting;
import de.mossgrabers.framework.configuration.IIntegerSetting;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.led.LedMode;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.looper.ClearHoldTime;
import dev.pacer4bitwig.pacer.looper.CountIn;
import dev.pacer4bitwig.pacer.looper.DoubleTapWindow;
import dev.pacer4bitwig.pacer.looper.ExpressionTarget;
import dev.pacer4bitwig.pacer.looper.FadeLength;
import dev.pacer4bitwig.pacer.looper.HoldAction;
import dev.pacer4bitwig.pacer.looper.LoopColours;
import dev.pacer4bitwig.pacer.looper.LoopDoubleTap;
import dev.pacer4bitwig.pacer.looper.LoopLength;
import dev.pacer4bitwig.pacer.looper.LoopSwitchCount;
import dev.pacer4bitwig.pacer.looper.LoopSwitchMode;
import dev.pacer4bitwig.pacer.looper.MuteTiming;
import dev.pacer4bitwig.pacer.looper.NotificationLevel;
import dev.pacer4bitwig.pacer.looper.PedalCurve;
import dev.pacer4bitwig.pacer.looper.PedalResponse;
import dev.pacer4bitwig.pacer.looper.PlayingTapAction;
import dev.pacer4bitwig.pacer.looper.QuantizationChoice;
import dev.pacer4bitwig.util.Labelled;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;


/**
 * Settings of the PACER Looper (Bitwig: Settings > Controllers), plus one project setting (the position of the loop
 * tracks).
 */
public class PacerConfiguration extends AbstractConfiguration
{
    /** Setting ID: LED mode. */
    public static final Integer              LED_MODE             = Integer.valueOf (1000);
    /** Setting ID: launch quantization. */
    public static final Integer              LAUNCH_QUANTIZATION  = Integer.valueOf (1001);
    /** Setting ID: loop length. */
    public static final Integer              LOOP_LENGTH          = Integer.valueOf (1002);
    /** Setting ID: expression pedal 1 target or response. */
    public static final Integer              EXPRESSION_1         = Integer.valueOf (1003);
    /** Setting ID: expression pedal 2 target or response. */
    public static final Integer              EXPRESSION_2         = Integer.valueOf (1004);
    /** Setting ID: the LED test button was clicked. */
    public static final Integer              LED_TEST             = Integer.valueOf (1005);
    /** Setting ID: Nektar DAW mode on/off. */
    public static final Integer              DAW_MODE             = Integer.valueOf (1006);
    /** Setting ID: the project's loop track position. */
    public static final Integer              LOOP_TRACK_START     = Integer.valueOf (1007);

    /** Number of expression pedal jacks. */
    public static final int                  NUM_EXPRESSION       = 2;
    /** Highest selectable first loop track. */
    public static final int                  MAX_LOOP_TRACK_START = 128;

    /** Default tap / hold actions of SW 1-6 (when not loop switches) and SW A-D. */
    private static final Action [] []        SWITCH_DEFAULTS      =
    {
        {
            Action.RECORD_NEXT_LAYER,
            Action.CLEAR_LAST_LOOP
        },
        {
            Action.MUTE_ALL_TOGGLE,
            Action.FADE_OUT
        },
        {
            Action.LOOP_SELECTED,
            Action.CLEAR_SELECTED
        },
        {
            Action.SELECT_NEXT_LOOP,
            Action.SELECT_PREVIOUS_LOOP
        },
        {
            Action.UNDO,
            Action.REDO
        },
        {
            Action.PLAY_STOP_ALL,
            Action.CLEAR_ROW
        },
        {
            Action.ROW_PREVIOUS,
            Action.TRACKS_LEFT
        },
        {
            Action.ROW_NEXT,
            Action.TRACKS_RIGHT
        },
        {
            Action.LAUNCHER_OVERDUB,
            Action.METRONOME
        },
        {
            Action.TAP_TEMPO,
            Action.TRANSPORT_PLAY_STOP
        }
    };
    /** Default tap / hold actions of FS 1-4. */
    private static final Action [] []        FOOTSWITCH_DEFAULTS  =
    {
        {
            Action.RECORD_NEXT_LAYER,
            Action.CLEAR_LAST_LOOP
        },
        {
            Action.PLAY_STOP_ALL,
            Action.CLEAR_ROW
        },
        {
            Action.NONE,
            Action.NONE
        },
        {
            Action.NONE,
            Action.NONE
        }
    };
    private static final ExpressionTarget [] EXPRESSION_DEFAULTS  =
    {
        ExpressionTarget.SELECTED_VOLUME,
        ExpressionTarget.MASTER_VOLUME
    };

    private static final String              CATEGORY_LOOPER      = "Looper";
    private static final String              CATEGORY_BOTTOM_ROW  = "Bottom row SW 1-6 (switches that are not loop switches)";
    private static final String              CATEGORY_TOP_ROW     = "Top row SW A-D";
    private static final String              CATEGORY_JACKS       = "Footswitch jacks FS 1-4";
    private static final String              CATEGORY_PEDALS      = "Expression pedals";
    private static final String              CATEGORY_LEDS        = "Pacer LEDs";
    private static final String              CATEGORY_LAUNCHER    = "Clip launcher (pushed into the project)";
    private static final String              CATEGORY_DAW_MODE    = "Nektar DAW mode (USB port 2)";
    private static final String              CATEGORY_FEEDBACK    = "Feedback";
    private static final String              CATEGORY_PROJECT     = "PACER Looper";
    private static final String []           ON_OFF               =
    {
        "On",
        "Off"
    };
    private static final String []           MIDI_CHANNELS        = new String [16];
    private static final String []           LOOP_TRACK_COUNTS    = new String [PacerMap.MAX_LOOP_TRACKS];

    static
    {
        for (int i = 0; i < MIDI_CHANNELS.length; i++)
            MIDI_CHANNELS[i] = Integer.toString (i + 1);
        for (int i = 0; i < LOOP_TRACK_COUNTS.length; i++)
            LOOP_TRACK_COUNTS[i] = Integer.toString (i + 1);
    }

    private volatile int                     loopTrackCount       = 4;
    private volatile LoopSwitchCount         loopSwitchCount      = LoopSwitchCount.FOUR;
    private volatile LoopSwitchMode          loopSwitchMode       = LoopSwitchMode.TAP;
    private volatile boolean                 loopOnPress          = true;
    private volatile PlayingTapAction        playingTapAction     = PlayingTapAction.STOP;
    private volatile HoldAction              loopHoldAction       = HoldAction.DELETE;
    private volatile LoopDoubleTap           loopDoubleTap        = LoopDoubleTap.NOTHING;
    private volatile DoubleTapWindow         doubleTapWindow      = DoubleTapWindow.NORMAL;
    private volatile ClearHoldTime           clearHoldTime        = ClearHoldTime.NORMAL;
    private volatile boolean                 armOnRecord          = true;
    private volatile boolean                 exclusiveArm         = true;
    private volatile boolean                 selectOnPress        = true;
    private volatile CountIn                 countIn              = CountIn.OFF;
    private volatile MuteTiming              muteTiming           = MuteTiming.IMMEDIATE;
    private volatile FadeLength              fadeLength           = FadeLength.BARS_2;
    private volatile String                  rowNames             = "";
    private final Action []                  switchTap            = new Action [PacerMap.NUM_SWITCHES];
    private final Action []                  switchHold           = new Action [PacerMap.NUM_SWITCHES];
    private final Action []                  switchDoubleTap      = new Action [PacerMap.NUM_SWITCHES];
    private final Action []                  footswitchTap        = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final Action []                  footswitchHold       = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final Action []                  footswitchDoubleTap  = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final ExpressionTarget []        expressionTargets    = EXPRESSION_DEFAULTS.clone ();
    private final PedalCurve []              pedalCurves          = new PedalCurve [NUM_EXPRESSION];
    private final int []                     pedalMinimum         = new int [NUM_EXPRESSION];
    private final int []                     pedalMaximum         = new int [NUM_EXPRESSION];
    private volatile int                     pedalMidiChannel     = 0;
    private volatile LedMode                 ledMode              = LedMode.TWO_COLOUR;
    private volatile boolean                 beatSyncedLeds       = true;
    private volatile boolean                 countBeats           = true;
    private volatile LoopColours.Choice      colourStopped        = LoopColours.Choice.of (LoopColours.DEFAULT.stopped ());
    private volatile LoopColours.Choice      colourPlaying        = LoopColours.Choice.of (LoopColours.DEFAULT.playing ());
    private volatile LoopColours.Choice      colourRecording      = LoopColours.Choice.of (LoopColours.DEFAULT.recording ());
    private volatile LoopColours.Choice      colourMuted          = LoopColours.Choice.of (LoopColours.DEFAULT.muted ());
    private volatile QuantizationChoice      launchQuantization   = QuantizationChoice.KEEP;
    private volatile LoopLength              loopLength           = LoopLength.KEEP;
    private volatile boolean                 dawMode              = false;
    private volatile NotificationLevel       notificationLevel    = NotificationLevel.ALL;
    private volatile int                     loopTrackStart       = 1;
    private IIntegerSetting                  loopTrackStartSetting;


    /**
     * Constructor.
     *
     * @param host The DAW host
     * @param valueChanger The value changer
     * @param arpeggiatorModes The available arpeggiator modes
     */
    public PacerConfiguration (final IHost host, final IValueChanger valueChanger, final List<ArpeggiatorMode> arpeggiatorModes)
    {
        super (host, valueChanger, arpeggiatorModes);

        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            this.switchTap[i] = SWITCH_DEFAULTS[i][0];
            this.switchHold[i] = SWITCH_DEFAULTS[i][1];
        }
        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
        {
            this.footswitchTap[i] = FOOTSWITCH_DEFAULTS[i][0];
            this.footswitchHold[i] = FOOTSWITCH_DEFAULTS[i][1];
        }
        Arrays.fill (this.switchDoubleTap, Action.NONE);
        Arrays.fill (this.footswitchDoubleTap, Action.NONE);
        Arrays.fill (this.pedalCurves, PedalCurve.LINEAR);
        Arrays.fill (this.pedalMaximum, 100);
    }


    /** {@inheritDoc} */
    @Override
    public void init (final ISettingsUI globalSettings, final ISettingsUI documentSettings)
    {
        this.initLooper (globalSettings);
        this.initSwitches (globalSettings);
        this.initPedals (globalSettings);
        this.initLeds (globalSettings);

        enumSetting (globalSettings, "Launch quantization", CATEGORY_LAUNCHER, QuantizationChoice.values (), QuantizationChoice.KEEP, value -> {
            this.launchQuantization = value;
            this.notifyObservers (LAUNCH_QUANTIZATION);
        });
        enumSetting (globalSettings, "Loop length", CATEGORY_LAUNCHER, LoopLength.values (), LoopLength.KEEP, value -> {
            this.loopLength = value;
            this.notifyObservers (LOOP_LENGTH);
        });

        onOffSetting (globalSettings, "Serve the Track and Transport presets (replaces Nektar's script)", CATEGORY_DAW_MODE, false, value -> {
            this.dawMode = value;
            this.notifyObservers (DAW_MODE);
        });

        enumSetting (globalSettings, "Pop-up notifications", CATEGORY_FEEDBACK, NotificationLevel.values (), NotificationLevel.ALL, value -> this.notificationLevel = value);

        // Stored in the project: where this project's loop tracks are
        this.loopTrackStartSetting = documentSettings.getRangeSetting ("Loop tracks start at track", CATEGORY_PROJECT, 1, MAX_LOOP_TRACK_START, 1, "", 1);
        this.loopTrackStartSetting.addValueObserver (value -> {
            this.loopTrackStart = value.intValue ();
            this.notifyObservers (LOOP_TRACK_START);
        });
    }


    private void initLooper (final ISettingsUI settings)
    {
        final IEnumSetting trackCountSetting = settings.getEnumSetting ("Loop tracks", CATEGORY_LOOPER, LOOP_TRACK_COUNTS, LOOP_TRACK_COUNTS[3]);
        trackCountSetting.addValueObserver (value -> this.loopTrackCount = Math.max (0, Arrays.asList (LOOP_TRACK_COUNTS).indexOf (value)) + 1);
        enumSetting (settings, "Loop switches", CATEGORY_LOOPER, LoopSwitchCount.values (), LoopSwitchCount.FOUR, value -> this.loopSwitchCount = value);
        enumSetting (settings, "Loop switch mode", CATEGORY_LOOPER, LoopSwitchMode.values (), LoopSwitchMode.TAP, value -> this.loopSwitchMode = value);
        onOffSetting (settings, "Loop switch fires on press (off: on release)", CATEGORY_LOOPER, true, value -> this.loopOnPress = value);
        enumSetting (settings, "Tap on a playing loop", CATEGORY_LOOPER, PlayingTapAction.values (), PlayingTapAction.STOP, value -> this.playingTapAction = value);
        enumSetting (settings, "Hold a loop switch", CATEGORY_LOOPER, HoldAction.values (), HoldAction.DELETE, value -> this.loopHoldAction = value);
        enumSetting (settings, "Double-tap a loop switch", CATEGORY_LOOPER, LoopDoubleTap.values (), LoopDoubleTap.NOTHING, value -> this.loopDoubleTap = value);
        enumSetting (settings, "Double-tap speed", CATEGORY_LOOPER, DoubleTapWindow.values (), DoubleTapWindow.NORMAL, value -> this.doubleTapWindow = value);
        enumSetting (settings, "Hold time for clearing actions", CATEGORY_LOOPER, ClearHoldTime.values (), ClearHoldTime.NORMAL, value -> this.clearHoldTime = value);
        onOffSetting (settings, "Arm the track when recording", CATEGORY_LOOPER, true, value -> this.armOnRecord = value);
        onOffSetting (settings, "Exclusive arm (disarm finished loops)", CATEGORY_LOOPER, true, value -> this.exclusiveArm = value);
        onOffSetting (settings, "Select the track on press", CATEGORY_LOOPER, true, value -> this.selectOnPress = value);
        enumSetting (settings, "Count-in from a stopped transport", CATEGORY_LOOPER, CountIn.values (), CountIn.OFF, value -> this.countIn = value);
        enumSetting (settings, "Mute timing", CATEGORY_LOOPER, MuteTiming.values (), MuteTiming.IMMEDIATE, value -> this.muteTiming = value);
        enumSetting (settings, "Fade length", CATEGORY_LOOPER, FadeLength.values (), FadeLength.BARS_2, value -> this.fadeLength = value);
        settings.getStringSetting ("Names for new rows (comma separated)", CATEGORY_LOOPER, 200, "").addValueObserver (value -> this.rowNames = value == null ? "" : value);
    }


    private void initSwitches (final ISettingsUI settings)
    {
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            final int index = i;
            final String name = PacerMap.SWITCH_NAMES[i];
            final String category = i < PacerMap.FIRST_TOP_ROW_SWITCH ? CATEGORY_BOTTOM_ROW : CATEGORY_TOP_ROW;
            enumSetting (settings, name + " tap", category, Action.values (), SWITCH_DEFAULTS[i][0], value -> this.switchTap[index] = value);
            enumSetting (settings, name + " double-tap", category, Action.values (), Action.NONE, value -> this.switchDoubleTap[index] = value);
            enumSetting (settings, name + " hold", category, Action.values (), SWITCH_DEFAULTS[i][1], value -> this.switchHold[index] = value);
        }

        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
        {
            final int index = i;
            final String name = "FS " + (i + 1);
            enumSetting (settings, name + " tap", CATEGORY_JACKS, Action.values (), FOOTSWITCH_DEFAULTS[i][0], value -> this.footswitchTap[index] = value);
            enumSetting (settings, name + " double-tap", CATEGORY_JACKS, Action.values (), Action.NONE, value -> this.footswitchDoubleTap[index] = value);
            enumSetting (settings, name + " hold", CATEGORY_JACKS, Action.values (), FOOTSWITCH_DEFAULTS[i][1], value -> this.footswitchHold[index] = value);
        }
    }


    private void initPedals (final ISettingsUI settings)
    {
        for (int i = 0; i < NUM_EXPRESSION; i++)
        {
            final int index = i;
            final Integer settingID = i == 0 ? EXPRESSION_1 : EXPRESSION_2;
            final String name = "EXP " + (i + 1);
            enumSetting (settings, name, CATEGORY_PEDALS, ExpressionTarget.values (), EXPRESSION_DEFAULTS[i], value -> {
                this.expressionTargets[index] = value;
                this.notifyObservers (settingID);
            });
            enumSetting (settings, name + " response", CATEGORY_PEDALS, PedalCurve.values (), PedalCurve.LINEAR, value -> {
                this.pedalCurves[index] = value;
                this.notifyObservers (settingID);
            });
            settings.getRangeSetting (name + " heel (minimum)", CATEGORY_PEDALS, 0, 100, 1, "%", 0).addValueObserver (value -> {
                this.pedalMinimum[index] = value.intValue ();
                this.notifyObservers (settingID);
            });
            settings.getRangeSetting (name + " toe (maximum)", CATEGORY_PEDALS, 0, 100, 1, "%", 100).addValueObserver (value -> {
                this.pedalMaximum[index] = value.intValue ();
                this.notifyObservers (settingID);
            });
        }
        final IEnumSetting channelSetting = settings.getEnumSetting ("MIDI channel for pedal messages", CATEGORY_PEDALS, MIDI_CHANNELS, MIDI_CHANNELS[0]);
        channelSetting.addValueObserver (value -> this.pedalMidiChannel = Math.max (0, Arrays.asList (MIDI_CHANNELS).indexOf (value)));
    }


    private void initLeds (final ISettingsUI settings)
    {
        enumSetting (settings, "LED mode", CATEGORY_LEDS, LedMode.values (), LedMode.TWO_COLOUR, value -> {
            this.ledMode = value;
            this.notifyObservers (LED_MODE);
        });
        onOffSetting (settings, "Blink in time with the transport", CATEGORY_LEDS, true, value -> this.beatSyncedLeds = value);
        onOffSetting (settings, "Count beats on SW A-D (count-in and recording)", CATEGORY_LEDS, true, value -> this.countBeats = value);
        enumSetting (settings, "Multi-colour: stopped loop", CATEGORY_LEDS, LoopColours.Choice.values (), this.colourStopped, value -> this.colourStopped = value);
        enumSetting (settings, "Multi-colour: playing loop", CATEGORY_LEDS, LoopColours.Choice.values (), this.colourPlaying, value -> this.colourPlaying = value);
        enumSetting (settings, "Multi-colour: recording loop", CATEGORY_LEDS, LoopColours.Choice.values (), this.colourRecording, value -> this.colourRecording = value);
        enumSetting (settings, "Multi-colour: muted loop", CATEGORY_LEDS, LoopColours.Choice.values (), this.colourMuted, value -> this.colourMuted = value);
        settings.getSignalSetting ("Cycle every LED through all colours", CATEGORY_LEDS, "Test the LEDs").addSignalObserver (value -> this.notifyObservers (LED_TEST));
    }


    private static <E extends Labelled> void enumSetting (final ISettingsUI settings, final String label, final String category, final E [] values, final E initial, final Consumer<E> observer)
    {
        final IEnumSetting setting = settings.getEnumSetting (label, category, Labelled.labels (values), initial.getLabel ());
        setting.addValueObserver (value -> observer.accept (Labelled.fromLabel (values, value, initial)));
    }


    private static void onOffSetting (final ISettingsUI settings, final String label, final String category, final boolean initial, final Consumer<Boolean> observer)
    {
        final IEnumSetting setting = settings.getEnumSetting (label, category, ON_OFF, initial ? ON_OFF[0] : ON_OFF[1]);
        setting.addValueObserver (value -> observer.accept (Boolean.valueOf (ON_OFF[0].equals (value))));
    }


    /**
     * @return How many tracks the looper manages, 1-6
     */
    public int getLoopTrackCount ()
    {
        return this.loopTrackCount;
    }


    /**
     * @return How many bottom switches are loop switches
     */
    public LoopSwitchCount getLoopSwitchCount ()
    {
        return this.loopSwitchCount;
    }


    /**
     * @return How loop switches record
     */
    public LoopSwitchMode getLoopSwitchMode ()
    {
        return this.loopSwitchMode;
    }


    /**
     * @return True if loop switches fire on press, false on release
     */
    public boolean isLoopOnPress ()
    {
        return this.loopOnPress;
    }


    /**
     * @return What tapping a playing loop does
     */
    public PlayingTapAction getPlayingTapAction ()
    {
        return this.playingTapAction;
    }


    /**
     * @return What holding a loop switch does
     */
    public HoldAction getLoopHoldAction ()
    {
        return this.loopHoldAction;
    }


    /**
     * @return What double-tapping a loop switch does
     */
    public LoopDoubleTap getLoopDoubleTap ()
    {
        return this.loopDoubleTap;
    }


    /**
     * @return How quickly a double-tap must be
     */
    public DoubleTapWindow getDoubleTapWindow ()
    {
        return this.doubleTapWindow;
    }


    /**
     * @return How long destructive hold actions need the switch held
     */
    public ClearHoldTime getClearHoldTime ()
    {
        return this.clearHoldTime;
    }


    /**
     * @return True to arm the track before recording
     */
    public boolean isArmOnRecord ()
    {
        return this.armOnRecord;
    }


    /**
     * @return True to disarm loop tracks the looper armed once they finished recording
     */
    public boolean isExclusiveArm ()
    {
        return this.exclusiveArm;
    }


    /**
     * @return True to select the track when its loop switch is pressed
     */
    public boolean isSelectOnPress ()
    {
        return this.selectOnPress;
    }


    /**
     * @return The count-in used when recording starts from a stopped transport
     */
    public CountIn getCountIn ()
    {
        return this.countIn;
    }


    /**
     * @return When mute changes take effect
     */
    public MuteTiming getMuteTiming ()
    {
        return this.muteTiming;
    }


    /**
     * @return The length of the fade actions
     */
    public FadeLength getFadeLength ()
    {
        return this.fadeLength;
    }


    /**
     * @return Comma separated names for new rows, may be empty
     */
    public String getRowNames ()
    {
        return this.rowNames;
    }


    /**
     * @param switchIndex 0-9
     * @return The tap action of a switch that is not a loop switch
     */
    public Action getSwitchTap (final int switchIndex)
    {
        return this.switchTap[switchIndex];
    }


    /**
     * @param switchIndex 0-9
     * @return The hold action of a switch that is not a loop switch
     */
    public Action getSwitchHold (final int switchIndex)
    {
        return this.switchHold[switchIndex];
    }


    /**
     * @param switchIndex 0-9
     * @return The double-tap action of a switch that is not a loop switch
     */
    public Action getSwitchDoubleTap (final int switchIndex)
    {
        return this.switchDoubleTap[switchIndex];
    }


    /**
     * @param index 0-3
     * @return The tap action of a footswitch jack
     */
    public Action getFootswitchTap (final int index)
    {
        return this.footswitchTap[index];
    }


    /**
     * @param index 0-3
     * @return The hold action of a footswitch jack
     */
    public Action getFootswitchHold (final int index)
    {
        return this.footswitchHold[index];
    }


    /**
     * @param index 0-3
     * @return The double-tap action of a footswitch jack
     */
    public Action getFootswitchDoubleTap (final int index)
    {
        return this.footswitchDoubleTap[index];
    }


    /**
     * @param index 0-1
     * @return The target of an expression pedal
     */
    public ExpressionTarget getExpressionTarget (final int index)
    {
        return this.expressionTargets[index];
    }


    /**
     * @param index 0-1
     * @return The response (curve and range) of an expression pedal
     */
    public PedalResponse getPedalResponse (final int index)
    {
        return new PedalResponse (this.pedalCurves[index], this.pedalMinimum[index], this.pedalMaximum[index]);
    }


    /**
     * @return The MIDI channel (0-15) of pedal messages
     */
    public int getPedalMidiChannel ()
    {
        return this.pedalMidiChannel;
    }


    /**
     * @return The LED mode
     */
    public LedMode getLedMode ()
    {
        return this.ledMode;
    }


    /**
     * @return True if LED patterns follow the transport while it runs
     */
    public boolean isBeatSyncedLeds ()
    {
        return this.beatSyncedLeds;
    }


    /**
     * @return True to count beats on SW A-D during count-ins and recordings
     */
    public boolean isCountBeats ()
    {
        return this.countBeats;
    }


    /**
     * @return The multi-colour palette of loop switches
     */
    public LoopColours getLoopColours ()
    {
        return new LoopColours (this.colourStopped.getColour (), this.colourPlaying.getColour (), this.colourRecording.getColour (), this.colourMuted.getColour ());
    }


    /**
     * @return The launch quantization to push into the project
     */
    public QuantizationChoice getLaunchQuantization ()
    {
        return this.launchQuantization;
    }


    /**
     * @return The loop length to push into the project
     */
    public LoopLength getLoopLength ()
    {
        return this.loopLength;
    }


    /**
     * @return True to serve the Pacer's Track and Transport presets on USB port 2
     */
    public boolean isDawMode ()
    {
        return this.dawMode;
    }


    /**
     * @return Which pop-up notifications to show
     */
    public NotificationLevel getNotificationLevel ()
    {
        return this.notificationLevel;
    }


    /**
     * @return The first loop track of this project, 1-based
     */
    public int getLoopTrackStart ()
    {
        return this.loopTrackStart;
    }


    /**
     * Remember a new loop track position in the project.
     *
     * @param oneBased The first loop track, 1-based
     */
    public void setLoopTrackStart (final int oneBased)
    {
        final int value = Math.max (1, Math.min (MAX_LOOP_TRACK_START, oneBased));
        if (this.loopTrackStartSetting != null && value != this.loopTrackStart)
            this.loopTrackStartSetting.set (value);
    }
}
