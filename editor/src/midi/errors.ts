export type MidiErrorCode =
  | 'unsupported'
  | 'denied'
  | 'no-input'
  | 'no-output'
  | 'busy'
  | 'timeout'
  | 'aborted'
  | 'send-failed'
  | 'unknown';

export class MidiError extends Error {
  override name = 'MidiError';
  readonly code: MidiErrorCode;

  constructor(code: MidiErrorCode, message: string) {
    super(message);
    this.code = code;
  }
}

export function isAbortError(err: unknown): boolean {
  return err instanceof MidiError && err.code === 'aborted';
}
