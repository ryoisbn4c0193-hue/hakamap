import { describe, expect, it } from 'vitest';

import {
  backgroundTileUrl,
  displayResolution,
  SELECTED_GRAVE_COLOR,
  SELECTED_GRAVE_FILL_ALPHA,
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

describe('選択墓所の表示', () => {
  it('状態色のシアンで半透明に塗り、背景との差を視認できる', () => {
    expect(SELECTED_GRAVE_COLOR).toBe(0x00e5ff);
    expect(SELECTED_GRAVE_FILL_ALPHA).toBeGreaterThanOrEqual(0.4);
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
