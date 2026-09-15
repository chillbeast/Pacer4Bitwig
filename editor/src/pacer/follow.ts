/**
 * "Hardware follow": find the control whose step 1 produced an incoming MIDI message.
 */
import { CONTROLS, MSG, type ControlKey } from './constants';
import type { Preset, Step } from './model';

type Pattern =
  | { kind: 'cc'; channel: number | null; controller: number }
  | { kind: 'note'; channel: number | null; note: number }
  | { kind: 'program'; channel: number | null; min: number; max: number }
  | { kind: 'pitch'; channel: number | null }
  | { kind: 'pressure'; channel: number | null }
  | { kind: 'nrpn'; channel: number | null; msb: number; lsb: number }
  | { kind: 'mmc'; device: number; command: number };

/** The MIDI a step sends, as a pattern. `globalChannel` resolves steps set to channel 0 (null = any channel). */
export function stepPattern(step: Step, globalChannel: number | null): Pattern | null {
  if (!step.active || step.msgType === MSG.OFF) return null;
  const channel = step.channel >= 1 && step.channel <= 16 ? step.channel : globalChannel;
  const [d1, d2, d3] = step.data;
  switch (step.msgType) {
    case MSG.SW_CC_TRIGGER:
    case MSG.SW_CC_TOGGLE:
    case MSG.SW_CC_STEP:
    case MSG.AD_CC:
      return { kind: 'cc', channel, controller: d1 };
    case MSG.SW_NOTE:
    case MSG.SW_NOTE_TOGGLE:
      return { kind: 'note', channel, note: d1 };
    case MSG.SW_PROGRAM_BANK:
      return { kind: 'program', channel, min: d1, max: d1 };
    case MSG.SW_PROGRAM_STEP:
      return { kind: 'program', channel, min: Math.min(d2, d3), max: Math.max(d2, d3) };
    case MSG.AD_PITCH_BEND:
      return { kind: 'pitch', channel };
    case MSG.AD_AFTERTOUCH:
      return { kind: 'pressure', channel };
    case MSG.SW_NRPN_COARSE:
    case MSG.SW_NRPN_FINE:
    case MSG.AD_NRPN_COARSE:
    case MSG.AD_NRPN_FINE:
      return { kind: 'nrpn', channel, msb: d3, lsb: d2 };
    case MSG.SW_MMC:
      return { kind: 'mmc', device: d1, command: d2 };
    default:
      return null; // relay, preset/step navigation: no MIDI output to follow
  }
}

type Event =
  | { kind: 'cc'; channel: number; controller: number; value: number }
  | { kind: 'note'; channel: number; note: number }
  | { kind: 'program'; channel: number; program: number }
  | { kind: 'pitch'; channel: number }
  | { kind: 'pressure'; channel: number }
  | { kind: 'nrpn'; channel: number; msb: number; lsb: number }
  | { kind: 'mmc'; device: number; command: number };

const channelOk = (pattern: number | null, channel: number) => pattern === null || pattern === channel;

function matches(p: Pattern, e: Event): boolean {
  switch (p.kind) {
    case 'cc':
      return e.kind === 'cc' && channelOk(p.channel, e.channel) && e.controller === p.controller;
    case 'note':
      return e.kind === 'note' && channelOk(p.channel, e.channel) && e.note === p.note;
    case 'program':
      return e.kind === 'program' && channelOk(p.channel, e.channel) && e.program >= p.min && e.program <= p.max;
    case 'pitch':
      return e.kind === 'pitch' && channelOk(p.channel, e.channel);
    case 'pressure':
      return e.kind === 'pressure' && channelOk(p.channel, e.channel);
    case 'nrpn':
      return e.kind === 'nrpn' && channelOk(p.channel, e.channel) && e.msb === p.msb && e.lsb === p.lsb;
    case 'mmc':
      return e.kind === 'mmc' && (p.device === 127 || e.device === 127 || p.device === e.device) && e.command === p.command;
  }
}

/** Exact data beats ranges / channel-only patterns; an explicit channel beats "any channel". */
function specificity(p: Pattern): number {
  const exact = p.kind === 'program' ? (p.min === p.max ? 2 : 1) : p.kind === 'pitch' || p.kind === 'pressure' ? 1 : 2;
  const channel = p.kind === 'mmc' || p.channel !== null ? 1 : 0;
  return exact * 2 + channel;
}

export interface Follower {
  /** The single control whose step 1 matches the message, or null (no match / ambiguous / not followable). */
  match(preset: Preset | null, message: Uint8Array, globalChannel?: number | null): ControlKey | null;
  reset(): void;
}

export function createFollower(): Follower {
  const nrpnMsb = new Map<number, number>();

  const toEvent = (m: Uint8Array): Event | null => {
    const status = m[0];
    if (status === 0xf0) {
      // MMC: F0 7F <device> 06 <command> F7
      if (m.length >= 6 && m[1] === 0x7f && m[3] === 0x06) return { kind: 'mmc', device: m[2], command: m[4] };
      return null;
    }
    if (status < 0x80 || status >= 0xf0) return null;
    const channel = (status & 0x0f) + 1;
    switch (status & 0xf0) {
      case 0x90:
      case 0x80:
        return m.length >= 3 ? { kind: 'note', channel, note: m[1] } : null;
      case 0xb0: {
        if (m.length < 3) return null;
        if (m[1] === 99) nrpnMsb.set(channel, m[2]);
        if (m[1] === 98 && nrpnMsb.has(channel)) return { kind: 'nrpn', channel, msb: nrpnMsb.get(channel)!, lsb: m[2] };
        return { kind: 'cc', channel, controller: m[1], value: m[2] };
      }
      case 0xc0:
        return m.length >= 2 ? { kind: 'program', channel, program: m[1] } : null;
      case 0xd0:
        return { kind: 'pressure', channel };
      case 0xe0:
        return { kind: 'pitch', channel };
      default:
        return null;
    }
  };

  return {
    match(preset, message, globalChannel = null) {
      const event = toEvent(message);
      if (!preset || !event) return null;
      let found: ControlKey | null = null;
      let best = -1;
      let tie = false;
      for (const def of CONTROLS) {
        const pattern = stepPattern(preset.controls[def.key].steps[0], globalChannel);
        if (!pattern || !matches(pattern, event)) continue;
        const score = specificity(pattern);
        if (score > best) {
          best = score;
          found = def.key;
          tie = false;
        } else if (score === best) {
          tie = true;
        }
      }
      return tie ? null : found; // ambiguous among equally specific matches
    },
    reset() {
      nrpnMsb.clear();
    },
  };
}
