import { nextDelay, SseParser } from '../src/live/sse';

const bytes = (s: string) => new TextEncoder().encode(s);

describe('SseParser', () => {
  it('gives the data of each event, skipping comments', () => {
    const got: string[] = [];
    const p = new SseParser((d) => got.push(d));
    p.push(bytes(': ping\n\ndata:{"type":"hello","online":[]}\n\ndata: {"type":"typing"}\n\n'));
    expect(got).toEqual(['{"type":"hello","online":[]}', '{"type":"typing"}']);
  });

  it('joins chunks cut in a line and in a character of several bytes', () => {
    const got: string[] = [];
    const p = new SseParser((d) => got.push(d));
    const all = bytes('data:{"name":"Zawadi 🌍 Éa"}\r\n\r\n');
    // cut in the middle of the emoji (4 bytes) and of the É (2 bytes)
    const cuts = [7, 22, 24, 27, all.length];
    let from = 0;
    for (const to of cuts) {
      p.push(all.slice(from, to));
      from = to;
    }
    expect(got).toEqual(['{"name":"Zawadi 🌍 Éa"}']);
  });
});

describe('nextDelay', () => {
  it("follows Umoja's delays, and Retry-After first", () => {
    expect([0, 1, 2, 3, 4, 9].map((a) => nextDelay(a))).toEqual([1000, 2000, 5000, 10000, 30000, 30000]);
    const d = nextDelay(4, 5);
    expect(d).toBeGreaterThanOrEqual(5000);
    expect(d).toBeLessThan(6000);
  });
});
