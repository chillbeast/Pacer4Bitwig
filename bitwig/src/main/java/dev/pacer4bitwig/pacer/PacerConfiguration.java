// Pacer4Bitwig - Nektar Pacer looper extension on the DrivenByMoss framework (LGPLv3)

package dev.pacer4bitwig.pacer;

import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.IEnumSetting;
import de.mossgrabers.framework.configuration.IIntegerSetting;
import de.mossgrabers.framework.configuration.ISettingsUI;
import de.mossgrabers.framework.configuration.IStringSetting;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;

import dev.pacer4bitwig.pacer.controller.PacerMap;
import dev.pacer4bitwig.pacer.looper.Action;
import dev.pacer4bitwig.pacer.live.LedRow;
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
import dev.pacer4bitwig.pacer.mode.Mode;
import dev.pacer4bitwig.pacer.mode.ModeBoard;
import dev.pacer4bitwig.pacer.mode.StartupMode;
import dev.pacer4bitwig.pacer.mode.SwitchColour;
import dev.pacer4bitwig.pacer.mode.SwitchLayout;
import dev.pacer4bitwig.pacer.looper.LoopSwitchMode;
import dev.pacer4bitwig.pacer.looper.MuteTiming;
import dev.pacer4bitwig.pacer.looper.NotificationLevel;
import dev.pacer4bitwig.pacer.looper.PedalCurve;
import dev.pacer4bitwig.pacer.looper.PedalResponse;
import dev.pacer4bitwig.pacer.looper.PlayingTapAction;
import dev.pacer4bitwig.pacer.looper.QuantizationChoice;
import dev.pacer4bitwig.pacer.preset.PresetKind;
import dev.pacer4bitwig.util.Labelled;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;


/**
 * Settings of the PACER Looper (Bitwig: Settings > Controllers), plus the project settings of the FX preset (its
 * instruments and their snapshots).
 */
public class PacerConfiguration extends AbstractConfiguration
{
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
    /** Setting ID: the custom mode's layout changed. */
    public static final Integer              CUSTOM_MODE          = Integer.valueOf (1009);
    /** Setting ID: the looper MIDI channel. */
    public static final Integer              LOOPER_CHANNEL       = Integer.valueOf (1008);

    /** Number of expression pedal jacks. */
    public static final int                  NUM_EXPRESSION       = 2;
    /** Highest selectable first loop track. */
    public static final int                  MAX_LOOP_TRACK_START = 128;
    /** Instruments of the FX preset. */
    public static final int                  NUM_INSTRUMENTS      = 4;

    /** Default tap / hold actions of FS 1-4 (shared by both presets). */
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
            Action.FOCUS_NEXT,
            Action.NONE
        },
        {
            Action.SNAPSHOT_NEXT,
            Action.NONE
        }
    };
    /**
     * What the two pedals do in each mode, in {@link Mode} order. Two pedals and no spare footswitches is the normal
     * Pacer rig, so letting them follow the mode is what turns two controls into ten.
     */
    private static final ExpressionTarget [] [] EXPRESSION_DEFAULTS =
    {
        // Looper: ride the loop you are on, and the room
        {
            ExpressionTarget.SELECTED_VOLUME,
            ExpressionTarget.MASTER_VOLUME
        },
        // FX: the two spare slots of the instrument's "Pacer" page - a wah and a filter, say
        {
            ExpressionTarget.FOCUSED_REMOTE_7,
            ExpressionTarget.FOCUSED_REMOTE_8
        },
        // Mixer: level and a send, so a reverb throw is under the other foot
        {
            ExpressionTarget.SELECTED_VOLUME,
            ExpressionTarget.SELECTED_SEND_1
        },
        // Song: the master for endings, and a project macro for whatever the song needs
        {
            ExpressionTarget.MASTER_VOLUME,
            ExpressionTarget.PROJECT_REMOTE_1
        },
        // Custom: yours, like the rest of it
        {
            ExpressionTarget.NONE,
            ExpressionTarget.NONE
        }
    };

    private static final String              CATEGORY_LOOPER      = "Looper";
    private static final String              CATEGORY_MODES       = "Modes";
    private static final String              CATEGORY_CUSTOM      = "Custom mode";
    private static final String              CATEGORY_FX          = "FX mode";
    private static final String              CATEGORY_JACKS       = "Footswitch jacks FS 1-4";
    private static final String              CATEGORY_PEDALS      = "Expression pedals";
    private static final String              CATEGORY_LEDS        = "Pacer LEDs";
    private static final String              CATEGORY_LAUNCHER    = "Clip launcher (pushed into the project)";
    private static final String              CATEGORY_DAW_MODE    = "Nektar DAW mode (USB port 2)";
    private static final String              CATEGORY_FEEDBACK    = "Feedback";
    private static final String              CATEGORY_FX_PROJECT  = "PACER FX";
    private static final String []           ON_OFF               =
    {
        "On",
        "Off"
    };
    private static final String []           MIDI_CHANNELS        = new String [16];
    private static final String []           LOOP_TRACK_COUNTS    = new String [PacerMap.MAX_LOOP_TRACKS];
    private static final String []           SNAPSHOT_COUNTS      =
    {
        "2",
        "3",
        "4"
    };
    private static final String []           INSTRUMENT_LETTERS   =
    {
        "A",
        "B",
        "C",
        "D"
    };

    static
    {
        for (int i = 0; i < MIDI_CHANNELS.length; i++)
            MIDI_CHANNELS[i] = Integer.toString (i + 1);
        for (int i = 0; i < LOOP_TRACK_COUNTS.length; i++)
            LOOP_TRACK_COUNTS[i] = Integer.toString (i + 1);
    }

    private volatile int                     looperMidiChannel    = PacerMap.DEFAULT_MIDI_CHANNEL;
    private volatile int                     loopTrackCount       = 4;
    private volatile LoopSwitchMode          loopSwitchMode       = LoopSwitchMode.TAP;
    private volatile boolean                 loopOnPress          = true;
    private volatile PlayingTapAction        playingTapAction     = PlayingTapAction.STOP;
    private volatile HoldAction              loopHoldAction       = HoldAction.DELETE;
    private volatile LoopDoubleTap           loopDoubleTap        = LoopDoubleTap.NOTHING;
    private volatile DoubleTapWindow         doubleTapWindow      = DoubleTapWindow.NORMAL;
    private volatile ClearHoldTime           clearHoldTime        = ClearHoldTime.LONG;
    private volatile boolean                 armOnRecord          = true;
    private volatile boolean                 exclusiveArm         = true;
    private volatile boolean                 selectOnPress        = true;
    private volatile CountIn                 countIn              = CountIn.OFF;
    private volatile MuteTiming              muteTiming           = MuteTiming.IMMEDIATE;
    private volatile FadeLength              fadeLength           = FadeLength.BARS_2;
    private volatile String                  rowNames             = "";
    private final Action []                  footswitchTap        = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final Action []                  footswitchHold       = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final Action []                  footswitchDoubleTap  = new Action [PacerMap.NUM_FOOTSWITCHES];
    private final ExpressionTarget [] []     expressionTargets    = new ExpressionTarget [Mode.values ().length] [NUM_EXPRESSION];
    private final PedalCurve []              pedalCurves          = new PedalCurve [NUM_EXPRESSION];
    private final int []                     pedalMinimum         = new int [NUM_EXPRESSION];
    private final int []                     pedalMaximum         = new int [NUM_EXPRESSION];
    private volatile int                     pedalMidiChannel     = 0;
    private volatile boolean                 beatSyncedLeds       = true;
    private volatile boolean                 countBeats           = true;
    private volatile StartupMode             startupMode          = StartupMode.REMEMBER;
    private volatile Mode                    projectMode          = Mode.LOOP;
    private IEnumSetting                     projectModeSetting;
    private volatile boolean                 showContext          = true;
    private volatile boolean                 keepModeName         = true;
    private volatile String                  customName           = "CUST";
    private volatile LoopSwitchCount         customLoopSwitches   = LoopSwitchCount.NONE;
    private final Action []                  customTap            = new Action [PacerMap.NUM_SWITCHES];
    private final Action []                  customDoubleTap      = new Action [PacerMap.NUM_SWITCHES];
    private final Action []                  customHold           = new Action [PacerMap.NUM_SWITCHES];
    private final SwitchColour []            customColour         = new SwitchColour [PacerMap.NUM_SWITCHES];
    private final LedRow []                  customRow            = new LedRow [PacerMap.NUM_SWITCHES];
    /** Rebuilt whenever a custom setting changes, so painting never allocates. */
    private final SwitchLayout []            customLayouts        = new SwitchLayout [PacerMap.NUM_SWITCHES];
    /** Reads the settings above; the layouts themselves are prebuilt, so this does no work per paint. */
    private final ModeBoard                  customBoard          = new ModeBoard ()
    {
        /** {@inheritDoc} */
        @Override
        public String getDisplayName ()
        {
            return PacerConfiguration.this.customName;
        }


        /** {@inheritDoc} */
        @Override
        public SwitchLayout getLayout (final int switchIndex)
        {
            return PacerConfiguration.this.customLayouts[switchIndex];
        }


        /** {@inheritDoc} */
        @Override
        public int getLoopSwitches ()
        {
            return PacerConfiguration.this.customLoopSwitches.getCount ();
        }
    };
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
    private volatile int                     snapshotsPerInstrument = 2;
    private volatile boolean                 focusSelectsTrack    = false;
    private volatile String                  remotePageName       = "Pacer";
    private final String []                  instrumentTracks     = new String [NUM_INSTRUMENTS];
    private final IStringSetting []          instrumentTrackSettings = new IStringSetting [NUM_INSTRUMENTS];
    private final String []                  instrumentSnapshots  = new String [NUM_INSTRUMENTS];
    private final IStringSetting []          snapshotSettings     = new IStringSetting [NUM_INSTRUMENTS];
    private volatile int                     focusedInstrument    = 0;
    private IEnumSetting                     focusedInstrumentSetting;


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

        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
        {
            this.footswitchTap[i] = FOOTSWITCH_DEFAULTS[i][0];
            this.footswitchHold[i] = FOOTSWITCH_DEFAULTS[i][1];
        }
        Arrays.fill (this.footswitchDoubleTap, Action.NONE);
        Arrays.fill (this.customTap, Action.NONE);
        Arrays.fill (this.customDoubleTap, Action.NONE);
        Arrays.fill (this.customHold, Action.NONE);
        Arrays.fill (this.customColour, SwitchColour.AUTO);
        Arrays.fill (this.customRow, LedRow.STRIP);
        for (int mode = 0; mode < this.expressionTargets.length; mode++)
        {
            // A mode added without a default row still gets something rather than nulls
            if (mode < EXPRESSION_DEFAULTS.length)
                System.arraycopy (EXPRESSION_DEFAULTS[mode], 0, this.expressionTargets[mode], 0, NUM_EXPRESSION);
            else
                Arrays.fill (this.expressionTargets[mode], ExpressionTarget.NONE);
        }
        this.rebuildCustomLayouts ();
        Arrays.fill (this.pedalCurves, PedalCurve.LINEAR);
        Arrays.fill (this.pedalMaximum, 100);
        Arrays.fill (this.instrumentTracks, "");
        Arrays.fill (this.instrumentSnapshots, "");
    }


    /** {@inheritDoc} */
    @Override
    public void init (final ISettingsUI globalSettings, final ISettingsUI documentSettings)
    {
        this.initLooper (globalSettings);
        this.initModes (globalSettings);
        this.initJacks (globalSettings);
        this.initCustom (globalSettings);
        this.initFx (globalSettings);
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

        this.initFxProject (documentSettings);
    }


    private void initLooper (final ISettingsUI settings)
    {
        // Read during init (the setting fires its stored value right away): MIDI bindings are created with it
        final IEnumSetting channelSetting = settings.getEnumSetting ("Looper MIDI channel (must match the Pacer preset)", CATEGORY_LOOPER, MIDI_CHANNELS, MIDI_CHANNELS[PacerMap.DEFAULT_MIDI_CHANNEL]);
        channelSetting.addValueObserver (value -> {
            final int index = Arrays.asList (MIDI_CHANNELS).indexOf (value);
            this.looperMidiChannel = index < 0 ? PacerMap.DEFAULT_MIDI_CHANNEL : index;
            this.notifyObservers (LOOPER_CHANNEL);
        });

        final IEnumSetting trackCountSetting = settings.getEnumSetting ("Loop tracks", CATEGORY_LOOPER, LOOP_TRACK_COUNTS, LOOP_TRACK_COUNTS[3]);
        trackCountSetting.addValueObserver (value -> this.loopTrackCount = Math.max (0, Arrays.asList (LOOP_TRACK_COUNTS).indexOf (value)) + 1);
        enumSetting (settings, "Loop switch mode", CATEGORY_LOOPER, LoopSwitchMode.values (), LoopSwitchMode.TAP, value -> this.loopSwitchMode = value);
        onOffSetting (settings, "Loop switch fires on press (off: on release)", CATEGORY_LOOPER, true, value -> this.loopOnPress = value);
        enumSetting (settings, "Tap on a playing loop", CATEGORY_LOOPER, PlayingTapAction.values (), PlayingTapAction.STOP, value -> this.playingTapAction = value);
        enumSetting (settings, "Hold a loop switch", CATEGORY_LOOPER, HoldAction.values (), HoldAction.DELETE, value -> this.loopHoldAction = value);
        enumSetting (settings, "Double-tap a loop switch", CATEGORY_LOOPER, LoopDoubleTap.values (), LoopDoubleTap.NOTHING, value -> this.loopDoubleTap = value);
        enumSetting (settings, "Double-tap speed", CATEGORY_LOOPER, DoubleTapWindow.values (), DoubleTapWindow.NORMAL, value -> this.doubleTapWindow = value);
        // Long by default: at the plain half second a foot resting on a loop switch deletes the take it just started
        enumSetting (settings, "Hold time for clearing actions", CATEGORY_LOOPER, ClearHoldTime.values (), ClearHoldTime.LONG, value -> this.clearHoldTime = value);
        onOffSetting (settings, "Arm the track when recording", CATEGORY_LOOPER, true, value -> this.armOnRecord = value);
        onOffSetting (settings, "Exclusive arm (disarm finished loops)", CATEGORY_LOOPER, true, value -> this.exclusiveArm = value);
        onOffSetting (settings, "Select the track on press", CATEGORY_LOOPER, true, value -> this.selectOnPress = value);
        enumSetting (settings, "Count-in from a stopped transport", CATEGORY_LOOPER, CountIn.values (), CountIn.OFF, value -> this.countIn = value);
        enumSetting (settings, "Mute timing", CATEGORY_LOOPER, MuteTiming.values (), MuteTiming.IMMEDIATE, value -> this.muteTiming = value);
        enumSetting (settings, "Fade length", CATEGORY_LOOPER, FadeLength.values (), FadeLength.BARS_2, value -> this.fadeLength = value);
        settings.getStringSetting ("Names for new rows (comma separated)", CATEGORY_LOOPER, 200, "").addValueObserver (value -> this.rowNames = value == null ? "" : value);

        // Where the loop tracks start, in every project: moving the window from the Pacer writes it. Deliberately not
        // a project setting - Bitwig 6 shows those nowhere, so a stale one could neither be seen nor corrected.
        this.loopTrackStartSetting = settings.getRangeSetting ("Loop tracks start at track", CATEGORY_LOOPER, 1, MAX_LOOP_TRACK_START, 1, "", 1);
        this.loopTrackStartSetting.addValueObserver (value -> {
            this.loopTrackStart = value.intValue ();
            this.notifyObservers (LOOP_TRACK_START);
        });
    }


    private void initModes (final ISettingsUI settings)
    {
        enumSetting (settings, "Mode at startup", CATEGORY_MODES, StartupMode.values (), StartupMode.REMEMBER, value -> this.startupMode = value);
        // Every restore is one SysEx, which flashes LOAD SYS; turn it off to leave the display on the CC readout
        onOffSetting (settings, "Put the mode name back on the display after a press", CATEGORY_MODES, true, value -> this.keepModeName = value);
        onOffSetting (settings, "Show what the mode is doing on the display", CATEGORY_MODES, true, value -> this.showContext = value);
    }


    /**
     * The custom mode is laid out here, switch by switch. SW 6 is missing on purpose: it is the mode switch in every
     * mode, including this one.
     */
    private void initCustom (final ISettingsUI settings)
    {
        settings.getStringSetting ("Name on the Pacer display (5 characters)", CATEGORY_CUSTOM, 5, "CUST").addValueObserver (value -> {
            this.customName = value == null || value.isBlank () ? "CUST" : value;
            this.rebuildCustomLayouts ();
        });
        enumSetting (settings, "Loop switches", CATEGORY_CUSTOM, LoopSwitchCount.values (), LoopSwitchCount.NONE, value -> {
            this.customLoopSwitches = value;
            this.rebuildCustomLayouts ();
        });

        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            if (Mode.isModeSwitch (i))
                continue;
            final int index = i;
            final String name = PacerMap.SWITCH_NAMES[i];
            enumSetting (settings, name + " tap", CATEGORY_CUSTOM, Action.values (), Action.NONE, value -> {
                this.customTap[index] = value;
                this.rebuildCustomLayouts ();
            });
            enumSetting (settings, name + " double-tap", CATEGORY_CUSTOM, Action.values (), Action.NONE, value -> {
                this.customDoubleTap[index] = value;
                this.rebuildCustomLayouts ();
            });
            enumSetting (settings, name + " hold", CATEGORY_CUSTOM, Action.values (), Action.NONE, value -> {
                this.customHold[index] = value;
                this.rebuildCustomLayouts ();
            });
            enumSetting (settings, name + " colour", CATEGORY_CUSTOM, SwitchColour.values (), SwitchColour.AUTO, value -> {
                this.customColour[index] = value;
                this.rebuildCustomLayouts ();
            });
            enumSetting (settings, name + " LED", CATEGORY_CUSTOM, LedRow.values (), LedRow.STRIP, value -> {
                this.customRow[index] = value;
                this.rebuildCustomLayouts ();
            });
        }
    }


    private void rebuildCustomLayouts ()
    {
        for (int i = 0; i < PacerMap.NUM_SWITCHES; i++)
        {
            if (Mode.isModeSwitch (i))
            {
                this.customLayouts[i] = SwitchLayout.MODE_SWITCH;
                continue;
            }
            final Action tap = this.customTap[i];
            this.customLayouts[i] = new SwitchLayout (tap, this.customDoubleTap[i], this.customHold[i], this.customColour[i].resolve (tap), this.customRow[i].orStripOn (i));
        }
        this.notifyObservers (CUSTOM_MODE);
    }


    /** The stomp switches belong to the modes; only the jacks are assignable. */
    private void initJacks (final ISettingsUI settings)
    {
        for (int i = 0; i < PacerMap.NUM_FOOTSWITCHES; i++)
        {
            final int index = i;
            final String name = "FS " + (i + 1);
            enumSetting (settings, name + " tap", CATEGORY_JACKS, Action.values (), FOOTSWITCH_DEFAULTS[i][0], value -> this.footswitchTap[index] = value);
            enumSetting (settings, name + " double-tap", CATEGORY_JACKS, Action.values (), Action.NONE, value -> this.footswitchDoubleTap[index] = value);
            enumSetting (settings, name + " hold", CATEGORY_JACKS, Action.values (), FOOTSWITCH_DEFAULTS[i][1], value -> this.footswitchHold[index] = value);
        }
    }


    private void initFx (final ISettingsUI settings)
    {
        final IEnumSetting snapshotSetting = settings.getEnumSetting ("Snapshots per instrument", CATEGORY_FX, SNAPSHOT_COUNTS, SNAPSHOT_COUNTS[0]);
        snapshotSetting.addValueObserver (value -> this.snapshotsPerInstrument = Math.max (0, Arrays.asList (SNAPSHOT_COUNTS).indexOf (value)) + 2);
        onOffSetting (settings, "Focusing an instrument selects its track in Bitwig", CATEGORY_FX, false, value -> this.focusSelectsTrack = value);
        settings.getStringSetting ("Remote controls page name", CATEGORY_FX, 40, "Pacer").addValueObserver (value -> this.remotePageName = value == null ? "" : value.trim ());
    }


    private void initFxProject (final ISettingsUI settings)
    {
        for (int i = 0; i < NUM_INSTRUMENTS; i++)
        {
            final int index = i;
            this.instrumentTrackSettings[i] = settings.getStringSetting ("Instrument " + INSTRUMENT_LETTERS[i] + " (track name)", CATEGORY_FX_PROJECT, 64, "");
            this.instrumentTrackSettings[i].addValueObserver (value -> this.instrumentTracks[index] = value == null ? "" : value.trim ());
        }

        this.focusedInstrumentSetting = settings.getEnumSetting ("Focused instrument", CATEGORY_FX_PROJECT, INSTRUMENT_LETTERS, INSTRUMENT_LETTERS[0]);
        this.focusedInstrumentSetting.addValueObserver (value -> this.focusedInstrument = Math.max (0, Arrays.asList (INSTRUMENT_LETTERS).indexOf (value)));

        // Internal state: which mode this project was last left in, for "Mode at startup = whatever this project used last"
        this.projectModeSetting = settings.getEnumSetting ("Mode", CATEGORY_FX_PROJECT, Labelled.labels (Mode.values ()), Mode.LOOP.getLabel ());
        this.projectModeSetting.addValueObserver (value -> this.projectMode = Labelled.fromLabel (Mode.values (), value, Mode.LOOP));
        this.projectModeSetting.setVisible (false);

        for (int i = 0; i < NUM_INSTRUMENTS; i++)
        {
            final int index = i;
            this.snapshotSettings[i] = settings.getStringSetting ("Instrument " + INSTRUMENT_LETTERS[i] + " snapshots", CATEGORY_FX_PROJECT, 256, "");
            this.snapshotSettings[i].addValueObserver (value -> this.instrumentSnapshots[index] = value == null ? "" : value);
            // Internal state, only saved with the project
            this.snapshotSettings[i].setVisible (false);
        }
    }


    private void initPedals (final ISettingsUI settings)
    {
        for (int i = 0; i < NUM_EXPRESSION; i++)
        {
            final int index = i;
            final Integer settingID = i == 0 ? EXPRESSION_1 : EXPRESSION_2;
            final String name = "EXP " + (i + 1);
            // One target per mode: the pedals are the only controls a bare Pacer has to spare
            for (final Mode mode: Mode.values ())
            {
                final int modeIndex = mode.ordinal ();
                enumSetting (settings, name + " · " + mode.getLabel (), CATEGORY_PEDALS, ExpressionTarget.values (), EXPRESSION_DEFAULTS[modeIndex][i], value -> {
                    this.expressionTargets[modeIndex][index] = value;
                    this.notifyObservers (settingID);
                });
            }
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
        onOffSetting (settings, "Blink in time with the transport", CATEGORY_LEDS, true, value -> this.beatSyncedLeds = value);
        onOffSetting (settings, "Count beats on SW A-D (count-in and recording)", CATEGORY_LEDS, true, value -> this.countBeats = value);
        enumSetting (settings, "Loop colour: stopped", CATEGORY_LEDS, LoopColours.Choice.values (), this.colourStopped, value -> this.colourStopped = value);
        enumSetting (settings, "Loop colour: playing", CATEGORY_LEDS, LoopColours.Choice.values (), this.colourPlaying, value -> this.colourPlaying = value);
        enumSetting (settings, "Loop colour: recording", CATEGORY_LEDS, LoopColours.Choice.values (), this.colourRecording, value -> this.colourRecording = value);
        enumSetting (settings, "Loop colour: muted", CATEGORY_LEDS, LoopColours.Choice.values (), this.colourMuted, value -> this.colourMuted = value);
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
     * @return The MIDI channel (0-15) of the looper preset
     */
    public int getLooperMidiChannel ()
    {
        return this.looperMidiChannel;
    }


    /**
     * @return How many tracks the looper manages, 1-6
     */
    public int getLoopTrackCount ()
    {
        return this.loopTrackCount;
    }


    /**
     * @return The custom mode's board, laid out entirely in the settings
     */
    public ModeBoard getCustomBoard ()
    {
        return this.customBoard;
    }


    /**
     * @return The mode to start in, honouring "whatever this project used last"
     */
    public Mode getModeAtStartup ()
    {
        return this.startupMode.resolve (this.projectMode);
    }


    /**
     * Remember the mode with the project.
     *
     * @param mode The mode
     */
    public void setProjectMode (final Mode mode)
    {
        if (this.projectModeSetting != null && mode != this.projectMode)
            this.projectModeSetting.set (mode.getLabel ());
    }


    /**
     * @return True to show what the mode is doing on the display rather than just its name
     */
    public boolean isShowContext ()
    {
        return this.showContext;
    }


    /**
     * @return True to write the mode name again after a switch press took the display
     */
    public boolean isKeepModeName ()
    {
        return this.keepModeName;
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
     * @param mode The active mode - each has its own pedal targets
     * @param index 0-1
     * @return The target of an expression pedal
     */
    public ExpressionTarget getExpressionTarget (final Mode mode, final int index)
    {
        return this.expressionTargets[mode.ordinal ()][index];
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
     * @return The colours of loop switches
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
     * @return The first loop track, 1-based
     */
    public int getLoopTrackStart ()
    {
        return this.loopTrackStart;
    }


    /**
     * Remember a new loop track position.
     *
     * @param oneBased The first loop track, 1-based
     */
    public void setLoopTrackStart (final int oneBased)
    {
        final int value = Math.max (1, Math.min (MAX_LOOP_TRACK_START, oneBased));
        if (this.loopTrackStartSetting != null && value != this.loopTrackStart)
            this.loopTrackStartSetting.set (value);
    }


    /**
     * @return How many snapshots each instrument cycles through, 2-4
     */
    public int getSnapshotsPerInstrument ()
    {
        return this.snapshotsPerInstrument;
    }


    /**
     * @return True to select an instrument's track in Bitwig when it gets focused
     */
    public boolean isFocusSelectsTrack ()
    {
        return this.focusSelectsTrack;
    }


    /**
     * @return The name of the remote controls page the FX switches and pedals use
     */
    public String getRemotePageName ()
    {
        return this.remotePageName;
    }


    /**
     * @param slot 0-3
     * @return The track name of an instrument, empty if not assigned
     */
    public String getInstrumentTrack (final int slot)
    {
        return this.instrumentTracks[slot];
    }


    /**
     * Assign a track to an instrument, saved in the project.
     *
     * @param slot 0-3
     * @param trackName The track name
     */
    public void setInstrumentTrack (final int slot, final String trackName)
    {
        this.instrumentTracks[slot] = trackName;
        if (this.instrumentTrackSettings[slot] != null)
            this.instrumentTrackSettings[slot].set (trackName);
    }


    /**
     * @return The focused instrument, 0-3
     */
    public int getFocusedInstrument ()
    {
        return this.focusedInstrument;
    }


    /**
     * Focus an instrument, saved in the project.
     *
     * @param slot 0-3
     */
    public void setFocusedInstrument (final int slot)
    {
        this.focusedInstrument = slot;
        if (this.focusedInstrumentSetting != null)
            this.focusedInstrumentSetting.set (INSTRUMENT_LETTERS[slot]);
    }


    /**
     * @param slot 0-3
     * @return The encoded snapshots of an instrument, may be empty
     */
    public String getInstrumentSnapshots (final int slot)
    {
        return this.instrumentSnapshots[slot];
    }


    /**
     * Store the snapshots of an instrument in the project.
     *
     * @param slot 0-3
     * @param encoded The encoded snapshots
     */
    public void setInstrumentSnapshots (final int slot, final String encoded)
    {
        this.instrumentSnapshots[slot] = encoded;
        if (this.snapshotSettings[slot] != null)
            this.snapshotSettings[slot].set (encoded);
    }
}
