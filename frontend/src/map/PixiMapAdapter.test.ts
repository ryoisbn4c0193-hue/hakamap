import { describe, expect, it } from 'vitest';

import {
  backgroundTileUrl,
  displayResolution,
  STATIC_MAP_APPLICATION_OPTIONS,
} from './PixiMapAdapter';

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

describe('backgroundTileUrl', () => {
  it('画像ローダーがPNGと判定できる拡張子付きURLを返す', () => {
    expect(backgroundTileUrl('project-id', 2, 3, 4)).toBe(
      '/api/v1/projects/project-id/background/tiles/2/3/4.png',
    );
  });
});

describe('STATIC_MAP_APPLICATION_OPTIONS', () => {
  it('状態変更時だけ手動描画する', () => {
    expect(STATIC_MAP_APPLICATION_OPTIONS.autoStart).toBe(false);
  });
});
