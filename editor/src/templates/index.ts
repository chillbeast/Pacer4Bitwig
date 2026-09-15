import type { ControlLabels } from '../pacer/json';
import type { Preset } from '../pacer/model';
import { FX_DEFAULT_SLOT, FX_LABELS, FX_PRESET_LOADED_VALUE, buildBitwigFxPreset } from './bitwigFx';
import {
  LOOPER_CHANNEL,
  LOOPER_DEFAULT_SLOT,
  LOOPER_LABELS,
  LOOPER_PRESET_LOADED_VALUE,
  buildBitwigLooperPreset,
  type LooperLedMode,
} from './bitwigLooper';
import { buildCcTogglePedalboard, buildMmcTransport, buildProgramPedalboard } from './pedalboards';

export interface TemplateChoice {
  value: string;
  label: string;
  description: string;
}

export interface TemplateHint {
  label: string;
  value: string;
}

export interface TemplateDef {
  id: string;
  name: string;
  summary: string;
  /** Preferred target slot; undefined = the selected slot. */
  defaultSlot?: number;
  choices?: { label: string; options: readonly TemplateChoice[] };
  /** Offer a MIDI channel picker (1–16). */
  channel?: { default: number; note: string };
  /** "Matching Bitwig settings" shown next to the options. */
  hints?: (choice: string | undefined, channel: number) => readonly TemplateHint[];
  build: (choice?: string, channel?: number) => { preset: Preset; labels: ControlLabels; title: string };
}

const looperMode = (choice: string | undefined): LooperLedMode => (choice === 'multi-colour' ? 'multi-colour' : 'two-colour');

/** The presets of the PACER Looper extension: same layout, LED choice and channel picker (docs/PACER-MAP.md). */
function bitwigTemplate(def: {
  id: string;
  name: string;
  summary: string;
  defaultSlot: number;
  /** Two-colour LED colours, e.g. "red for loop switches 1–4, white for the rest". */
  twoColourLeds: string;
  /** How the LED mode hint names what the preset announces. */
  announced: (mode: LooperLedMode) => string;
  presetLoadedValue: Readonly<Record<LooperLedMode, number>>;
  labels: ControlLabels;
  buildPreset: (mode: LooperLedMode, channel: number) => Preset;
}): TemplateDef {
  return {
    id: def.id,
    name: def.name,
    summary: def.summary,
    defaultSlot: def.defaultSlot,
    choices: {
      label: 'LED strategy',
      options: [
        {
          value: 'two-colour',
          label: 'Two-colour',
          description: `Documented behaviour: step 1 carries the LED (${def.twoColourLeds}, off when idle). The extension blinks to show state.`,
        },
        {
          value: 'multi-colour',
          label: 'Multi-colour (experimental)',
          description:
            'Hypothesis: steps 2–6 act as colour slots (CC 20–69) on the same LED — white, red, green, amber, blue, purple. Verify in the LED Lab first.',
        },
      ],
    },
    channel: {
      default: LOOPER_CHANNEL,
      note: 'Set the same channel in Bitwig: Settings > Controllers > PACER Looper > Looper MIDI channel',
    },
    hints: (choice, channel) => {
      const mode = looperMode(choice);
      return [
        { label: 'Controller', value: 'Settings > Controllers > Add > Nektar > PACER Looper, ports PACER / PACER' },
        { label: 'Looper MIDI channel', value: String(channel) },
        {
          label: 'LED mode',
          value: `Automatic — nothing to match: the preset announces ${def.announced(mode)} (preset-loaded CC 119 = ${def.presetLoadedValue[mode]}). If you pick a mode manually, choose ${mode}.`,
        },
        { label: 'Other presets', value: `Avoid channel ${channel} in other presets: the extension reserves it for the looper.` },
      ];
    },
    build: (choice, channel = LOOPER_CHANNEL) => {
      const mode = looperMode(choice);
      return {
        preset: def.buildPreset(mode, channel),
        labels: def.labels,
        title: `${def.name} (${mode}${channel === LOOPER_CHANNEL ? '' : `, channel ${channel}`})`,
      };
    },
  };
}

export const TEMPLATES: readonly TemplateDef[] = [
  bitwigTemplate({
    id: 'bitwig-looper',
    name: 'Bitwig Looper',
    summary:
      'Implements docs/PACER-MAP.md: CC Trigger 127/0 on the looper channel — switches CC 102–111, footswitch jacks CC 112–115, expression pedals CC 116/117, preset-loaded CC 119. Preset name “LOOPS”.',
    defaultSlot: LOOPER_DEFAULT_SLOT,
    twoColourLeds: 'red for loop switches 1–4, white for the rest',
    announced: (mode) => mode,
    presetLoadedValue: LOOPER_PRESET_LOADED_VALUE,
    labels: LOOPER_LABELS,
    buildPreset: buildBitwigLooperPreset,
  }),
  bitwigTemplate({
    id: 'bitwig-fx',
    name: 'Bitwig FX',
    summary:
      'A pedalboard for the instruments you play live through Bitwig: SW 1–6 switch effects, SW A–C pick the instrument, SW D steps through snapshots; needs PACER Looper 0.3.0 in Bitwig. Same CCs as Bitwig Looper (docs/FX-PRESET.md), preset name “FX”.',
    defaultSlot: FX_DEFAULT_SLOT,
    twoColourLeds: 'green for FX switches 1–6, white for A–D',
    announced: (mode) => `the FX preset, ${mode}`,
    presetLoadedValue: FX_PRESET_LOADED_VALUE,
    labels: FX_LABELS,
    buildPreset: buildBitwigFxPreset,
  }),
  {
    id: 'cc-toggle',
    name: 'CC toggle pedalboard',
    summary:
      'Channel 1. Switches toggle CC 20–29 (127/0) with a different colour each (bright = on, dim = off); footswitch jacks toggle CC 30–33; EXP 1 = CC 11 (expression), EXP 2 = CC 7 (volume).',
    build: () => ({ ...buildCcTogglePedalboard(), title: 'CC toggle pedalboard' }),
  },
  {
    id: 'program',
    name: 'Program change pedalboard',
    summary:
      'Channel 1. Switches 1–6 and A–D send program 0–9 (no bank change); FS 1 / FS 2 step through programs up / down like the factory PRGM presets; EXP 1 = CC 11, EXP 2 = CC 7.',
    build: () => ({ ...buildProgramPedalboard(), title: 'Program change pedalboard' }),
  },
  {
    id: 'mmc',
    name: 'MMC transport',
    summary:
      'MIDI Machine Control to all devices (ID 127): 1 rewind (5), 2 fast forward (4), 3 stop (1), 4 play (2), 5 record strobe (6), 6 pause (9); C/D preset down/up; FS 1 play, FS 2 stop.',
    build: () => ({ ...buildMmcTransport(), title: 'MMC transport' }),
  },
];

export function templateById(id: string): TemplateDef | undefined {
  return TEMPLATES.find((t) => t.id === id);
}
