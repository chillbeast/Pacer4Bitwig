// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.IEnumSetting;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.led.LedMode;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.looper.CountIn;
import dev.pacer4bitwig.pacer.looper.ExpressionTarget;
import dev.pacer4bitwig.pacer.looper.HoldAction;
import dev.pacer4bitwig.pacer.looper.LoopLength;
import dev.pacer4bitwig.pacer.looper.PlayingTapAction;
import dev.pacer4bitwig.pacer.looper.QuantizationChoice;
import dev.pacer4bitwig.pacer.looper.SwitchLayout;
import dev.pacer4bitwig.util.Labelled;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;


/**
 * Settings of the PACER Looper (Bitwig: Settings > Controllers).
 */
public class PacerConfiguration extends AbstractConfiguration
{
    /** Setting ID: LED mode. */
    public static final Integer              LED_MODE            = Integer.valueOf (1000);
    /** Setting ID: launch quantization. */
    public static final Integer              LAUNCH_QUANTIZATION = Integer.valueOf (1001);
    /** Setting ID: loop length. */
    public static final Integer              LOOP_LENGTH         = Integer.valueOf (1002);
    /** Setting ID: expression pedal 1 target. */
    public static final Integer              EXPRESSION_1        = Integer.valueOf (1003);
    /** Setting ID: expression pedal 2 target. */
    public static final Integer              EXPRESSION_2        = Integer.valueOf (1004);
    /** Setting ID: the LED test button was clicked. */
    public static final Integer              LED_TEST            = Integer.valueOf (1005);

    /** Number of expression pedal jacks. */
    public static final int                  NUM_EXPRESSION      = 2;

    /** Index of the first assignable switch (SW 5). */
    private static final int                 FIRST_ASSIGNABLE    = 4;
    /** Default tap/hold actions of SW 5, SW 6, SW A-D. */
    private static final Action [] []        SWITCH_DEFAULTS     =
    {
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
    /** Default tap/hold actions of FS 1-4. */
    private static final Action [] []        FOOTSWITCH_DEFAULTS =
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
    private static final ExpressionTarget [] EXPRESSION_DEFAULTS =
    {
        ExpressionTarget.SELECTED_VOLUME,
        ExpressionTarget.MASTER_VOLUME
    };

    private static final String              CATEGORY_LOOPER     = "Looper";
    private static final String              CATEGORY_SWITCHES   = "Switches (SW 5-6 only in the 4-loop layout)";
    private static final String              CATEGORY_JACKS      = "Footswitch and expression jacks";
    private static final String              CATEGORY_LEDS       = "Pacer LEDs";
    private static final String              CATEGORY_LAUNCHER   = "Clip launcher (pushed into the project)";
    private static final String              CATEGORY_FEEDBACK   = "Feedback";
    private static final String []           ON_OFF              =
    {
        "On",
        "Off"
    };
    private static final String []           MIDI_CHANNELS       = new String [16];

    static
    {
        for (int i = 0; i < MIDI_CHANNELS.length; i++)
            MIDI_CHANNELS[i] = Integer.toString (i + 1);
    }

    private volatile SwitchLayout            switchLayout        = SwitchLayout.FOUR_LOOPS;
    private volatile boolean                 loopOnPress         = true;
    private volatile PlayingTapAction        playingTapAction    = PlayingTapAction.STOP;
    private volatile HoldAction              loopHoldAction      = HoldAction.DELETE;
    private volatile boolean                 armOnRecord         = true;
    private volatile boolean                 exclusiveArm        = true;
    private volatile boolean                 selectOnPress       = true;
    private volatile CountIn                 countIn             = CountIn.OFF;
    private final Action []                  switchTap           = new Action [PacerMap.NUM_SWITCHES];
    private final Action []                  switchHold          = new Action [PacerMap.NUM_SWITCHES];
    private final Action []                  footswitchTap       = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final Action []                  footswitchHold      = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final ExpressionTarget []        expressionTargets   = EXPRESSION_DEFAULTS.clone ();
    private volatile int                     pedalMidiChannel    = 0;
    private volatile LedMode                 ledMode             = LedMode.TWO_COLOUR;
    private volatile boolean                 beatSyncedLeds      = true;
    private volatile QuantizationChoice      launchQuantization  = QuantizationChoice.KEEP;
    private volatile LoopLength              loopLength          = LoopLength.KEEP;
    private volatile boolean                 notifications       = true;


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

        Arrays.fill (this.switchTap, Action.NONE);
        Arrays.fill (this.switchHold, Action.NONE);
        for (int i = 0; i < SWITCH_DEFAULTS.length; i++)
        {
            this.switchTap[FIRST_ASSIGNABLE + i] = SWITCH_DEFAULTS[i][0];
            this.switchHold[FIRST_ASSIGNABLE + i] = SWITCH_DEFAULTS[i][1];
        }
        for (int i = 0; i < FOOTSWITCH_DEFAULTS.length; i++)
        {
            this.footswitchTap[i] = FOOTSWITCH_DEFAULTS[i][0];
            this.footswitchHold[i] = FOOTSWITCH_DEFAULTS[i][1];
        }
    }


    /** {@inheritDoc} */
    @Override
    public void init (final ISettingsUI globalSettings, final ISettingsUI documentSettings)
    {
        enumSetting (globalSettings, "Switch layout", CATEGORY_LOOPER, SwitchLayout.values (), SwitchLayout.FOUR_LOOPS, value -> this.switchLayout = value);
        onOffSetting (globalSettings, "Loop switch fires on press (off: on release)", CATEGORY_LOOPER, true, value -> this.loopOnPress = value);
        enumSetting (globalSettings, "Tap on a playing loop", CATEGORY_LOOPER, PlayingTapAction.values (), PlayingTapAction.STOP, value -> this.playingTapAction = value);
        enumSetting (globalSettings, "Hold a loop switch", CATEGORY_LOOPER, HoldAction.values (), HoldAction.DELETE, value -> this.loopHoldAction = value);
        onOffSetting (globalSettings, "Arm the track when recording", CATEGORY_LOOPER, true, value -> this.armOnRecord = value);
        onOffSetting (globalSettings, "Exclusive arm (disarm finished loops)", CATEGORY_LOOPER, true, value -> this.exclusiveArm = value);
        onOffSetting (globalSettings, "Select the track on press", CATEGORY_LOOPER, true, value -> this.selectOnPress = value);
        enumSetting (globalSettings, "Count-in from a stopped transport", CATEGORY_LOOPER, CountIn.values (), CountIn.OFF, value -> this.countIn = value);

        for (int i = 0; i < SWITCH_DEFAULTS.length; i++)
        {
            final int index = FIRST_ASSIGNABLE + i;
            final String name = PacerMap.SWITCH_NAMES[index];
            enumSetting (globalSettings, name + " tap", CATEGORY_SWITCHES, Action.values (), SWITCH_DEFAULTS[i][0], value -> this.switchTap[index] = value);
            enumSetting (globalSettings, name + " hold", CATEGORY_SWITCHES, Action.values (), SWITCH_DEFAULTS[i][1], value -> this.switchHold[index] = value);
        }

        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
        {
            final int index = i;
            enumSetting (globalSettings, "FS " + (i + 1) + " tap", CATEGORY_JACKS, Action.values (), FOOTSWITCH_DEFAULTS[i][0], value -> this.footswitchTap[index] = value);
            enumSetting (globalSettings, "FS " + (i + 1) + " hold", CATEGORY_JACKS, Action.values (), FOOTSWITCH_DEFAULTS[i][1], value -> this.footswitchHold[index] = value);
        }
        for (int i = 0; i < NUM_EXPRESSION; i++)
        {
            final int index = i;
            final Integer settingID = i == 0 ? EXPRESSION_1 : EXPRESSION_2;
            enumSetting (globalSettings, "EXP " + (i + 1), CATEGORY_JACKS, ExpressionTarget.values (), EXPRESSION_DEFAULTS[i], value -> {
                this.expressionTargets[index] = value;
                this.notifyObservers (settingID);
            });
        }
        final IEnumSetting channelSetting = globalSettings.getEnumSetting ("MIDI channel for pedal messages", CATEGORY_JACKS, MIDI_CHANNELS, MIDI_CHANNELS[0]);
        channelSetting.addValueObserver (value -> this.pedalMidiChannel = Math.max (0, Arrays.asList (MIDI_CHANNELS).indexOf (value)));

        enumSetting (globalSettings, "LED mode", CATEGORY_LEDS, LedMode.values (), LedMode.TWO_COLOUR, value -> {
            this.ledMode = value;
            this.notifyObservers (LED_MODE);
        });
        onOffSetting (globalSettings, "Blink in time with the transport", CATEGORY_LEDS, true, value -> this.beatSyncedLeds = value);
        globalSettings.getSignalSetting ("Cycle every LED through all colours", CATEGORY_LEDS, "Test the LEDs").addSignalObserver (value -> this.notifyObservers (LED_TEST));

        enumSetting (globalSettings, "Launch quantization", CATEGORY_LAUNCHER, QuantizationChoice.values (), QuantizationChoice.KEEP, value -> {
            this.launchQuantization = value;
            this.notifyObservers (LAUNCH_QUANTIZATION);
        });
        enumSetting (globalSettings, "Loop length", CATEGORY_LAUNCHER, LoopLength.values (), LoopLength.KEEP, value -> {
            this.loopLength = value;
            this.notifyObservers (LOOP_LENGTH);
        });

        onOffSetting (globalSettings, "Show pop-up notifications", CATEGORY_FEEDBACK, true, value -> this.notifications = value);
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
     * @return The switch layout
     */
    public SwitchLayout getSwitchLayout ()
    {
        return this.switchLayout;
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
     * @param switchIndex 0-9
     * @return The tap action of an assignable switch, NONE for loop switches
     */
    public Action getSwitchTap (final int switchIndex)
    {
        return this.switchTap[switchIndex];
    }


    /**
     * @param switchIndex 0-9
     * @return The hold action of an assignable switch, NONE for loop switches
     */
    public Action getSwitchHold (final int switchIndex)
    {
        return this.switchHold[switchIndex];
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
     * @param index 0-1
     * @return The target of an expression pedal
     */
    public ExpressionTarget getExpressionTarget (final int index)
    {
        return this.expressionTargets[index];
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
     * @return True to show pop-up notifications
     */
    public boolean isNotifications ()
    {
        return this.notifications;
    }
}
