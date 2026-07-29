import { describe, expect, it } from 'vitest';

import {
  fitViewport,
  hitTest,
  intersects,
  mapToScreen,
  normalizeRect,
  screenToMap,
  selectIntersecting,
  snapRectangle,
  zoomAt,
} from './mapGeometry';

describe('map geometry', () => {
  it.each([0.1, 1, 8])('converts negative coordinates at zoom %s', (scale) => {
    const viewport = { scale, x: 123, y: -44 };
    const point = { x: -250.5, y: 80.25 };
    const converted = screenToMap(mapToScreen(point, viewport), viewport);
    expect(converted.x).toBeCloseTo(point.x);
    expect(converted.y).toBeCloseTo(point.y);
  });

  it('keeps the map point below the pointer while zooming', () => {
    const pointer = { x: 320, y: 180 };
    const before = { scale: 1, x: 20, y: -30 };
    const mapPoint = screenToMap(pointer, before);
    expect(mapToScreen(mapPoint, zoomAt(before, pointer, 1.2))).toEqual(pointer);
  });

  it('normalizes all rectangle drag directions and selects edge contacts', () => {
    const selection = normalizeRect({ x: 20, y: 20 }, { x: 0, y: 0 });
    expect(
      selectIntersecting([{ id: 'grave', x: 20, y: 5, width: 10, height: 10 }], selection),
    ).toEqual(['grave']);
  });

  it('uses the top display order for inclusive hit testing', () => {
    const rectangles = [
      { id: 'back', x: 0, y: 0, width: 10, height: 10, displayOrder: 0 },
      { id: 'front', x: 0, y: 0, width: 10, height: 10, displayOrder: 1 },
    ];
    expect(hitTest(rectangles, { x: 10, y: 10 })).toBe('front');
  });

  it('does not treat edge contact as an overlap', () => {
    const first = { id: 'a', x: 0, y: 0, width: 10, height: 10 };
    const second = { id: 'b', x: 10, y: 0, width: 10, height: 10 };
    expect(intersects(first, second)).toBe(true);
    expect(intersects(first, second, false)).toBe(false);
  });

  it.each([
    [0.1, 80],
    [1, 8],
    [8, 1],
  ])('uses an eight screen-pixel snap distance at zoom %s', (scale, mapDistance) => {
    const result = snapRectangle(
      { id: 'moving', x: 10 + mapDistance, y: 0, width: 10, height: 10 },
      [{ id: 'target', x: 0, y: 0, width: 10, height: 10 }],
      scale,
    );
    expect(result.rectangle.x).toBe(10);
  });

  it('fits five thousand graves within the viewport in well under one second', () => {
    const graves = Array.from({ length: 5_000 }, (_, index) => ({
      id: String(index),
      x: (index % 100) * 12,
      y: Math.floor(index / 100) * 16,
      width: 10,
      height: 14,
    }));
    const started = performance.now();
    const viewport = fitViewport(graves, 1_920, 1_080);
    for (let index = 0; index < 100; index += 1) {
      hitTest(graves, { x: index * 12 + 1, y: 1 });
    }
    expect(performance.now() - started).toBeLessThan(1_000);
    const serializedModelBytes = new TextEncoder().encode(JSON.stringify(graves)).byteLength;
    expect(serializedModelBytes).toBeLessThan(1024 * 1024 * 1024);
    expect(viewport.scale).toBeGreaterThanOrEqual(0.1);
  });
});
