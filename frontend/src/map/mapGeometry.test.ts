import { describe, expect, it } from 'vitest';

import {
  backgroundBounds,
  backgroundLocalToMap,
  fitViewport,
  graveLabel,
  hitTest,
  hitTestRotated,
  inverseViewportScale,
  intersects,
  keepSnappedRectangleInsideArea,
  mapToScreen,
  mapBoundsToBackgroundLocal,
  mapToBackgroundLocal,
  normalizeRect,
  normalizeRotation,
  resetBackgroundAspectRatio,
  rotateBackgroundAroundCenter,
  rotatedRectanglesIntersect,
  screenToMap,
  selectIntersecting,
  snapRectangle,
  snapRotation,
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

  it('does not zoom out below the supplied content-fit scale', () => {
    expect(zoomAt({ scale: 1, x: 0, y: 0 }, { x: 100, y: 100 }, 0.001, 0.025).scale).toBe(0.025);
  });

  it('fits a maximum-size background below the former ten-percent limit', () => {
    const viewport = fitViewport(
      [{ height: 30_000, id: 'background', width: 30_000, x: 0, y: 0 }],
      1_000,
      800,
      0,
      0.001,
    );
    expect(viewport.scale).toBeCloseTo(800 / 30_000);
  });

  it('keeps the map point below the pointer while zooming', () => {
    const pointer = { x: 320, y: 180 };
    const before = { scale: 1, x: 20, y: -30 };
    const mapPoint = screenToMap(pointer, before);
    expect(mapToScreen(mapPoint, zoomAt(before, pointer, 1.2))).toEqual(pointer);
  });

  it.each([0.1, 1, 8])('keeps text at a constant screen scale at zoom %s', (scale) => {
    expect(scale * inverseViewportScale(scale)).toBeCloseTo(1);
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

  it('uses the rotated outline for hit testing and overlap', () => {
    const rotated = { height: 4, id: 'rotated', rotation: 90, width: 20, x: 0, y: 0 };
    expect(hitTestRotated([rotated], { x: 10, y: 10 })).toBe('rotated');
    expect(hitTestRotated([rotated], { x: 1, y: 1 })).toBeUndefined();
    expect(
      rotatedRectanglesIntersect(rotated, {
        height: 3,
        id: 'other',
        rotation: 45,
        width: 3,
        x: 9,
        y: 8,
      }),
    ).toBe(true);
  });

  it('does not treat edge contact as an overlap', () => {
    const first = { id: 'a', x: 0, y: 0, width: 10, height: 10 };
    const second = { id: 'b', x: 10, y: 0, width: 10, height: 10 };
    expect(intersects(first, second)).toBe(true);
    expect(intersects(first, second, false)).toBe(false);
  });

  it('builds all four grave label modes', () => {
    expect(graveLabel('A-1', '山田家', 'managementNumber')).toBe('A-1');
    expect(graveLabel('A-1', '山田家', 'name')).toBe('山田家');
    expect(graveLabel('A-1', '山田家', 'both')).toBe('A-1 山田家');
    expect(graveLabel('A-1', '山田家', 'hidden')).toBe('');
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

  it('keeps a grave whose dragged center is inside an area on the closed inner edge', () => {
    const original = { height: 10, id: 'grave', width: 10, x: 91, y: 20 };
    const snapped = { ...original, x: 100 };
    expect(
      keepSnappedRectangleInsideArea(original, snapped, [
        { height: 100, id: 'area', width: 100, x: 0, y: 0 },
      ]),
    ).toEqual({ ...original, x: 90 });
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

  it.each([0, 45, 90])(
    'inverse transforms viewport corners for a background rotated %s degrees',
    (rotation) => {
      const background = {
        height: 100,
        rotation,
        scaleX: 2,
        scaleY: 0.5,
        width: 200,
        x: 30,
        y: -20,
      };
      const mapBounds = backgroundBounds(background);
      const local = mapBoundsToBackgroundLocal(mapBounds, background);
      expect(local.x).toBeLessThanOrEqual(0.000_001);
      expect(local.y).toBeLessThanOrEqual(0.000_001);
      expect(local.x + local.width).toBeGreaterThanOrEqual(background.width - 0.000_001);
      expect(local.y + local.height).toBeGreaterThanOrEqual(background.height - 0.000_001);
    },
  );

  it('converts rotated and independently scaled background points in both directions', () => {
    const background = {
      height: 100,
      rotation: 315,
      scaleX: 1.5,
      scaleY: 0.75,
      width: 200,
      x: -30,
      y: 40,
    };
    const local = { x: 120, y: 45 };
    const restored = mapToBackgroundLocal(backgroundLocalToMap(local, background), background);
    expect(restored.x).toBeCloseTo(local.x);
    expect(restored.y).toBeCloseTo(local.y);
  });

  it.each([
    [-2, 358],
    [362, 2],
    [-720, 0],
  ])('normalizes mouse rotation %s to %s degrees', (rotation, expected) => {
    expect(normalizeRotation(rotation)).toBe(expected);
  });

  it.each([
    [4, 0],
    [86, 90],
    [184, 180],
    [274, 270],
    [96, 96],
  ])('snaps rotation %s to %s degrees near right angles', (rotation, expected) => {
    expect(snapRotation(rotation)).toBe(expected);
  });

  it('keeps the background center fixed while rotating', () => {
    const background = {
      height: 100,
      rotation: 0,
      scaleX: 2,
      scaleY: 0.5,
      width: 200,
      x: 30,
      y: -20,
    };
    const center = backgroundLocalToMap({ x: 100, y: 50 }, background);
    const rotated = rotateBackgroundAroundCenter(background, -90);
    expect(rotated.rotation).toBe(270);
    const rotatedCenter = backgroundLocalToMap({ x: 100, y: 50 }, rotated);
    expect(rotatedCenter.x).toBeCloseTo(center.x);
    expect(rotatedCenter.y).toBeCloseTo(center.y);
  });

  it('keeps the background center fixed while restoring its aspect ratio', () => {
    const background = {
      height: 100,
      rotation: 45,
      scaleX: 2,
      scaleY: 0.5,
      width: 200,
      x: 30,
      y: -20,
    };
    const center = backgroundLocalToMap({ x: 100, y: 50 }, background);
    const restored = resetBackgroundAspectRatio(background);
    expect(restored.scaleY).toBe(2);
    const restoredCenter = backgroundLocalToMap({ x: 100, y: 50 }, restored);
    expect(restoredCenter.x).toBeCloseTo(center.x);
    expect(restoredCenter.y).toBeCloseTo(center.y);
  });
});
