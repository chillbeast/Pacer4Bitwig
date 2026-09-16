import type { ControlLabels } from '../pacer/json';
import type { Preset } from '../pacer/model';
import {
  LOOPER_CHANNEL,
  LOOPER_DEFAULT_SLOT,
  LOOPER_LABELS,
  LOOPER_PRESET_LOADED_VALUE,
  buildBitwigLooperPreset,
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

export const TEMPLATES: readonly TemplateDef[] = [
  {
    id: 'bitwig-looper',
    name: 'Bitwig Pacer',
    summary:
      'The one preset the PACER Looper extension needs: CC Trigger 127/0 on the looper channel — switches CC 102–111, footswitch jacks CC 112–115, expression pedals CC 116/117, preset-loaded CC 119. The extension paints the colours, the display name and every switch live, and they change with the active mode (hold SW 6).',
    defaultSlot: LOOPER_DEFAULT_SLOT,
    channel: {
      default: LOOPER_CHANNEL,
      note: 'Set the same channel in Bitwig: Settings > Controllers > PACER Looper > Looper MIDI channel',
    },
    hints: (_choice, channel) => [
      { label: 'Controller', value: 'Settings > Controllers > Add > Nektar > PACER Looper, ports PACER / PACER' },
      { label: 'Looper MIDI channel', value: String(channel) },
      {
        label: 'Modes',
        value: `Nothing to match: the preset announces itself (preset-loaded CC 119 = ${LOOPER_PRESET_LOADED_VALUE}) and the extension writes the rest. Tap SW 6 to toggle modes, hold it for the menu.`,
      },
      { label: 'Other presets', value: `Avoid channel ${channel} in other presets: the extension reserves it for the looper.` },
    ],
    build: (_choice, channel = LOOPER_CHANNEL) => ({
      preset: buildBitwigLooperPreset(channel),
      labels: LOOPER_LABELS,
      title: `Bitwig Pacer${channel === LOOPER_CHANNEL ? '' : ` (channel ${channel})`}`,
    }),
  },
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
