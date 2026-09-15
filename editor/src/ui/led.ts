import { LED_COLOR_DEFAULT, LED_COLOR_OFF, ledColorInfo, type Led } from '../pacer';
import type { CSSProperties } from 'react';

export type LedAppearance = 'off' | 'lit' | 'dim' | 'default';

export function ledAppearance(color: number): LedAppearance {
  if (color === LED_COLOR_OFF) return 'off';
  if (color === LED_COLOR_DEFAULT) return 'default';
  return ledColorInfo(color).dim ? 'dim' : 'lit';
}

/** CSS custom property carrying the LED colour. */
export function ledVars(color: number): CSSProperties {
  return { ['--led' as string]: ledColorInfo(color).hex };
}

export function previewColor(led: Led | null | undefined, mode: 'on' | 'off'): number {
  if (!led) return LED_COLOR_OFF;
  return mode === 'on' ? led.onColor : led.offColor;
}
