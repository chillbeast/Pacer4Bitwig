import type { ControlLabels } from '../pacer/json';
import type { Preset } from '../pacer/model';
import { LOOPER_DEFAULT_SLOT, LOOPER_LABELS, buildBitwigLooperPreset, type LooperLedMode } from './bitwigLooper';
import { buildCcTogglePedalboard, buildMmcTransport, buildProgramPedalboard } from './pedalboards';

export interface TemplateChoice {
  value: string;
  label: string;
  description: string;
}

export interface TemplateDef {
  id: string;
  name: string;
  summary: string;
  /** Preferred target slot; undefined = the selected slot. */
  defaultSlot?: number;
  choices?: { label: string; options: readonly TemplateChoice[] };
  build: (choice?: string) => { preset: Preset; labels: ControlLabels; title: string };
}

export const TEMPLATES: readonly TemplateDef[] = [
  {
    id: 'bitwig-looper',
    name: 'Bitwig Looper',
    summary:
      'Implements docs/PACER-MAP.md: CC Trigger 127/0 on channel 16 — switches CC 102–111, footswitch jacks CC 112–115, expression pedals CC 116/117, preset-loaded CC 119. Preset name “LOOPS”.',
    defaultSlot: LOOPER_DEFAULT_SLOT,
    choices: {
      label: 'LED strategy',
      options: [
        {
          value: 'two-colour',
          label: 'Two-colour',
          description:
            'Documented behaviour: step 1 carries the LED (red for loop switches 1–4, white for the rest, off when idle). The extension blinks to show state.',
        },
        {
          value: 'multi-colour',
          label: 'Multi-colour (experimental)',
          description:
            'Hypothesis: steps 2–6 act as colour slots (CC 20–69) on the same LED — white, red, green, amber, blue, purple. Verify in the LED Lab first.',
        },
      ],
    },
    build: (choice) => {
      const mode: LooperLedMode = choice === 'multi-colour' ? 'multi-colour' : 'two-colour';
      return { preset: buildBitwigLooperPreset(mode), labels: LOOPER_LABELS, title: `Bitwig Looper (${mode})` };
    },
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
