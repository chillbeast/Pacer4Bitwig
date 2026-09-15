/** Any Pacer port. */
export const PACER_PORT_PATTERN = /pacer/i;
/**
 * The Pacer's second USB port (Nektar DAW integration): "MIDIIN2 (PACER)" / "MIDIOUT2 (PACER)" on Windows,
 * "PACER MIDI2" / "PACER Port 2" elsewhere.
 */
export const PACER_SECONDARY_PORT_PATTERN = /\bMIDI(?:IN|OUT)?\s*2\b|\bport\s*2\b/i;

export function isPacerPort(name: string | null | undefined): boolean {
  return !!name && PACER_PORT_PATTERN.test(name);
}

/** Port 1 of the Pacer — the one that speaks SysEx and carries the user preset messages. */
export function isPacerPrimaryPort(name: string | null | undefined): boolean {
  return !!name && PACER_PORT_PATTERN.test(name) && !PACER_SECONDARY_PORT_PATTERN.test(name);
}

export interface NamedPort {
  readonly name: string | null;
  readonly state?: string;
}

/** Prefer the port with the remembered name, otherwise the Pacer's port 1. */
export function pickPort<T extends NamedPort>(ports: readonly T[], preferredName?: string | null): T | undefined {
  const live = ports.filter((p) => p.state !== 'disconnected');
  if (preferredName) {
    const match = live.find((p) => p.name === preferredName);
    if (match) return match;
  }
  return live.find((p) => isPacerPrimaryPort(p.name));
}
