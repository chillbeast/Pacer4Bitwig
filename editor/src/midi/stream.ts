/** Largest SysEx accepted before the frame is considered garbage. */
const MAX_SYSEX_BYTES = 64 * 1024;

function dataLength(status: number): number {
  if (status >= 0x80 && status < 0xc0) return 2;
  if (status >= 0xc0 && status < 0xe0) return 1;
  if (status >= 0xe0 && status < 0xf0) return 2;
  if (status === 0xf1 || status === 0xf3) return 1;
  if (status === 0xf2) return 2;
  return 0;
}

/**
 * Turns arbitrary chunks of incoming MIDI bytes into complete messages.
 *
 * Web MIDI normally delivers one complete message per event, but drivers may split long SysEx
 * or deliver several messages at once. Handles SysEx spanning chunks, several messages per chunk,
 * realtime bytes interleaved inside SysEx, and running status.
 */
export class MidiStreamParser {
  private sysex: number[] | null = null;
  private pending: number[] = [];
  private needed = 0;
  private runningStatus = 0;
  /** SysEx frames discarded because they were interrupted or too large. */
  droppedSysex = 0;

  push(chunk: ArrayLike<number>): Uint8Array[] {
    const out: Uint8Array[] = [];
    for (let i = 0; i < chunk.length; i++) {
      const b = chunk[i] & 0xff;

      if (b >= 0xf8) {
        out.push(Uint8Array.of(b)); // realtime: may appear anywhere
        continue;
      }
      if (b === 0xf0) {
        if (this.sysex) this.droppedSysex++;
        this.sysex = [b];
        this.pending = [];
        this.runningStatus = 0;
        continue;
      }
      if (b === 0xf7) {
        if (this.sysex) {
          this.sysex.push(b);
          out.push(Uint8Array.from(this.sysex));
          this.sysex = null;
        }
        continue;
      }
      if (this.sysex) {
        if (b < 0x80) {
          this.sysex.push(b);
          if (this.sysex.length > MAX_SYSEX_BYTES) {
            this.sysex = null;
            this.droppedSysex++;
          }
          continue;
        }
        // a status byte interrupts an unterminated SysEx
        this.sysex = null;
        this.droppedSysex++;
      }

      if (b >= 0x80) {
        this.pending = [b];
        this.needed = dataLength(b);
        this.runningStatus = b < 0xf0 ? b : 0;
        if (this.needed === 0) {
          out.push(Uint8Array.from(this.pending));
          this.pending = [];
        }
        continue;
      }

      // data byte
      if (this.pending.length === 0) {
        if (!this.runningStatus) continue; // stray data
        this.pending = [this.runningStatus];
        this.needed = dataLength(this.runningStatus);
      }
      this.pending.push(b);
      if (this.pending.length - 1 >= this.needed) {
        out.push(Uint8Array.from(this.pending));
        this.pending = [];
      }
    }
    return out;
  }

  reset(): void {
    this.sysex = null;
    this.pending = [];
    this.needed = 0;
    this.runningStatus = 0;
  }
}
