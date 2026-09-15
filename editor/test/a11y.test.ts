import { describe, expect, it } from 'vitest';
import { trapTarget } from '../src/ui/Dialog';
import { LAYOUT, neighbour, type ItemId } from '../src/ui/PacerDevice';

const ARROWS = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'];

describe('hardware view keyboard navigation', () => {
  const items = Object.keys(LAYOUT) as ItemId[];

  it('reaches every switch, jack and the display from any starting point using arrow keys only', () => {
    for (const start of items) {
      const seen = new Set<ItemId>([start]);
      const queue: ItemId[] = [start];
      while (queue.length > 0) {
        const current = queue.shift()!;
        for (const key of ARROWS) {
          const next = neighbour(current, key);
          if (next && !seen.has(next)) {
            seen.add(next);
            queue.push(next);
          }
        }
      }
      expect([...seen].sort()).toEqual([...items].sort());
    }
  });

  it('moves between rows the way the hardware is laid out', () => {
    expect(neighbour('SW2', 'ArrowUp')).toBe('SWA');
    expect(neighbour('SWA', 'ArrowDown')).toBe('SW2');
    expect(neighbour('SW1', 'ArrowRight')).toBe('SW2');
    expect(neighbour('SWD', 'ArrowRight')).toBe('preset');
    expect(neighbour('SW6', 'ArrowRight')).toBeNull();
  });
});

describe('dialog focus trap', () => {
  it('wraps Tab at the ends and leaves the middle to the browser', () => {
    expect(trapTarget(4, 5, false)).toBe(0);
    expect(trapTarget(0, 5, true)).toBe(4);
    expect(trapTarget(2, 5, false)).toBeNull();
    expect(trapTarget(2, 5, true)).toBeNull();
  });

  it('pulls focus back in when it is outside the dialog', () => {
    expect(trapTarget(-1, 3, false)).toBe(0);
    expect(trapTarget(-1, 3, true)).toBe(2);
    expect(trapTarget(-1, 0, false)).toBeNull();
  });
});
