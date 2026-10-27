import { describe, expect, it, vi } from 'vitest';

import { fitViewport, mapToScreen, screenToMap, type MapRect, type Viewport } from './mapGeometry';
import { PixiMapAdapter, type MapRenderModel } from './PixiMapAdapter';

const grave = {
  id: 'grave',
  x: -3000,
  y: 5000,
  width: 1000,
  height: 500,
  rotation: 0,
  label: '検証用',
};
const model: MapRenderModel = { areas: [], graves: [grave], labelMode: 'name', selectedIds: [] };
function setup() {
  const screen = { width: 800, height: 600 };
  const resize = vi.fn((width: number, height: number) => Object.assign(screen, { width, height }));
  const adapter = new PixiMapAdapter();
  // GPUを使う描画だけを置き換え、実際の初期表示・サイズ変更処理を検査する。
  Object.assign(adapter, {
    application: { screen, renderer: { resize } },
    canvasSize: { ...screen },
    render: vi.fn(),
  });
  return { adapter, screen, resize };
}
function viewport(adapter: PixiMapAdapter) {
  return (adapter as unknown as { viewport: Viewport }).viewport;
}
function expectVisible(adapter: PixiMapAdapter, rectangle: MapRect, width: number, height: number) {
  const camera = viewport(adapter);
  const start = mapToScreen(rectangle, camera);
  const end = mapToScreen(
    { x: rectangle.x + rectangle.width, y: rectangle.y + rectangle.height },
    camera,
  );
  expect(start.x).toBeGreaterThanOrEqual(-0.001);
  expect(start.y).toBeGreaterThanOrEqual(-0.001);
  expect(end.x).toBeLessThanOrEqual(width + 0.001);
  expect(end.y).toBeLessThanOrEqual(height + 0.001);
}

describe('起動時と最大化時の地図表示', () => {
  it('描画準備後にデータが届いても、離れた座標の墓所を初回から全体表示する', () => {
    const { adapter } = setup();
    adapter.update(model);
    expect(viewport(adapter)).toEqual(fitViewport([grave], 800, 600));
    expectVisible(adapter, grave, 800, 600);
  });

  it('全体表示中に横半分の画面から最大化しても全体が見える', () => {
    const { adapter, resize } = setup();
    adapter.update(model);
    adapter.resize(1600, 900);
    expect(resize).toHaveBeenCalledWith(1600, 900);
    expect(viewport(adapter)).toEqual(fitViewport([grave], 1600, 900));
    expectVisible(adapter, grave, 1600, 900);
  });

  it('背景のある地図も最大化後の領域へ収まる', () => {
    const { adapter } = setup();
    const background = {
      assetId: 'background',
      projectId: 'project',
      maximumLevel: 1,
      tileSize: 1024,
      x: -8000,
      y: 4000,
      width: 4000,
      height: 2000,
      scaleX: 1,
      scaleY: 1,
      rotation: 0,
    };
    adapter.update({ ...model, background });
    adapter.resize(1600, 900);
    expect(viewport(adapter)).toEqual(
      fitViewport([{ ...background, id: 'background' }], 1600, 900, 0, 0.001),
    );
    expectVisible(adapter, { ...background, id: 'background' }, 1600, 900);
  });

  it('手動拡大後のサイズ変更では見ていた地図の中心を保ち、全体表示で自動調整へ戻る', () => {
    const { adapter } = setup();
    adapter.update(model);
    adapter.zoom(4);
    const old = viewport(adapter);
    const center = screenToMap({ x: 400, y: 300 }, old);
    adapter.resize(1600, 900);
    expect(viewport(adapter).scale).toBe(old.scale);
    const after = screenToMap({ x: 800, y: 450 }, viewport(adapter));
    expect(after.x).toBeCloseTo(center.x);
    expect(after.y).toBeCloseTo(center.y);
    adapter.fit();
    adapter.resize(800, 600);
    expect(viewport(adapter)).toEqual(fitViewport([grave], 800, 600));
  });

  it('最小化などで表示領域がゼロになってもカメラを変更しない', () => {
    const { adapter, resize } = setup();
    adapter.update(model);
    const before = viewport(adapter);
    adapter.resize(0, 0);
    expect(viewport(adapter)).toEqual(before);
    expect(resize).not.toHaveBeenCalled();
  });
});
