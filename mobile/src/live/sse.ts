/**
 * Server-Sent Events read from a stream of bytes: chunks cut anywhere (in a line, in a character of several bytes).
 * Gives the data of each event; comments (": ping") and empty events are skipped.
 */
export class SseParser {
  private text = '';
  private pending: number[] = [];

  constructor(private readonly onData: (data: string) => void) {}

  push(chunk: Uint8Array | string): void {
    this.text += typeof chunk === 'string' ? chunk : this.decode(chunk);
    this.text = this.text.replace(/\r\n?/g, '\n');
    let end: number;
    while ((end = this.text.indexOf('\n\n')) >= 0) {
      const block = this.text.slice(0, end);
      this.text = this.text.slice(end + 2);
      const data = block
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(line.startsWith('data: ') ? 6 : 5))
        .join('\n');
      if (data) {
        this.onData(data);
      }
    }
  }

  /** UTF-8, keeping the bytes of a character cut at the end of a chunk for the next one */
  private decode(bytes: Uint8Array): string {
    const all = this.pending.length ? Uint8Array.from([...this.pending, ...bytes]) : bytes;
    let end = all.length;
    // a character of 2 to 4 bytes started in the last 3 bytes and not finished: kept
    for (let back = 1; back <= Math.min(3, all.length); back++) {
      const b = all[all.length - back];
      if ((b & 0xc0) === 0xc0) {
        const need = b >= 0xf0 ? 4 : b >= 0xe0 ? 3 : 2;
        if (back < need) {
          end = all.length - back;
        }
        break;
      }
      if ((b & 0x80) === 0) {
        break;
      }
    }
    this.pending = Array.from(all.slice(end));
    return utf8(all.subarray(0, end));
  }
}

function utf8(bytes: Uint8Array): string {
  if (typeof TextDecoder !== 'undefined') {
    return new TextDecoder('utf-8').decode(bytes);
  }
  let out = '';
  for (let i = 0; i < bytes.length; ) {
    const b = bytes[i];
    let code: number;
    if (b < 0x80) {
      code = b;
      i += 1;
    } else if (b < 0xe0) {
      code = ((b & 0x1f) << 6) | (bytes[i + 1] & 0x3f);
      i += 2;
    } else if (b < 0xf0) {
      code = ((b & 0x0f) << 12) | ((bytes[i + 1] & 0x3f) << 6) | (bytes[i + 2] & 0x3f);
      i += 3;
    } else {
      code = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3f) << 12) | ((bytes[i + 2] & 0x3f) << 6) | (bytes[i + 3] & 0x3f);
      i += 4;
    }
    out += String.fromCodePoint(code);
  }
  return out;
}

/** Umoja's delays between two attempts (1, 2, 5, 10, then 30 s), unless Oro said when (Retry-After) */
export function nextDelay(attempt: number, retryAfterSeconds?: number): number {
  if (retryAfterSeconds && retryAfterSeconds > 0) {
    // spread a little: thousands of phones come back at once after a restart
    return retryAfterSeconds * 1000 + Math.floor(Math.random() * 1000);
  }
  return [1000, 2000, 5000, 10000, 30000][Math.min(attempt, 4)];
}
