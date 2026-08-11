import { describe, expect, it } from 'vitest';

import { displayResolution } from './PixiMapAdapter';

describe('displayResolution', () => {
  it.each([
    [0.75, 1],
    [1, 1],
    [1.5, 1.5],
    [2, 2],
    [3, 2],
    [Number.NaN, 1],
  ])('端末倍率%sを描画解像度%sとして扱う', (devicePixelRatio, expected) => {
    expect(displayResolution(devicePixelRatio)).toBe(expected);
  });
});
