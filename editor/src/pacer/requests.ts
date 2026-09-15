import {
  CMD_GET,
  CMD_OFFSET,
  CONTROL_BY_KEY,
  OBJ_ALL,
  TARGET_BACKUP,
  TARGET_PRESET,
  type ControlKey,
} from './constants';
import { buildPacerMessage, isPacerMessage } from './sysex';

/** GET a full preset (name, controls, LEDs, preset MIDI). LEDs are only returned this way. */
export function requestPreset(index: number): Uint8Array {
  return buildPacerMessage([CMD_GET, TARGET_PRESET, index, OBJ_ALL]);
}

/** GET a single control (steps and mode only — no LED configuration). */
export function requestControl(index: number, control: ControlKey): Uint8Array {
  return buildPacerMessage([CMD_GET, TARGET_PRESET, index, CONTROL_BY_KEY[control].obj]);
}

/** GET every stored preset (target preset, idx 0x7F). */
export function requestAllPresets(): Uint8Array {
  return buildPacerMessage([CMD_GET, TARGET_PRESET, 0x7f]);
}

/** GET a full backup: all presets followed by the global configuration. */
export function requestFullBackup(): Uint8Array {
  return buildPacerMessage([CMD_GET, TARGET_BACKUP]);
}

export function isGetRequest(msg: ArrayLike<number>): boolean {
  return isPacerMessage(msg) && msg[CMD_OFFSET] === CMD_GET;
}
