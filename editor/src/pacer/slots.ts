import { CURRENT_PRESET_INDEX, D6_INDEX, LAST_STORED_PRESET, SLOT_COUNT } from './constants';

export const BANKS = ['A', 'B', 'C', 'D'] as const;

/** 0..24 */
export const ALL_SLOTS: readonly number[] = Array.from({ length: SLOT_COUNT }, (_, i) => i);
/** 1..24 */
export const STORED_SLOTS: readonly number[] = ALL_SLOTS.slice(1);

export function isValidSlot(index: number): boolean {
  return Number.isInteger(index) && index >= CURRENT_PRESET_INDEX && index <= LAST_STORED_PRESET;
}

/** 0 → "CUR", 1 → "A1", 19 → "D1", 24 → "D6". */
export function slotLabel(index: number): string {
  if (index === CURRENT_PRESET_INDEX) return 'CUR';
  if (!isValidSlot(index)) return `#${index}`;
  const bank = BANKS[Math.floor((index - 1) / 6)];
  return `${bank}${((index - 1) % 6) + 1}`;
}

export function slotLongLabel(index: number): string {
  return index === CURRENT_PRESET_INDEX ? 'Current preset' : `Preset ${slotLabel(index)}`;
}

/** "D1" → 19, "cur"/"current" → 0; null when invalid. */
export function slotIndexFromLabel(label: string): number | null {
  const text = label.trim().toUpperCase();
  if (text === 'CUR' || text === 'CURRENT') return CURRENT_PRESET_INDEX;
  const m = /^([A-D])([1-6])$/.exec(text);
  if (!m) return null;
  return (m[1].charCodeAt(0) - 65) * 6 + Number(m[2]);
}

export function isD6(index: number): boolean {
  return index === D6_INDEX;
}
