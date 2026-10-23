import { describe, expect, it } from 'vitest';
import { hitGrave, MapTap } from './mapInteraction';
import { map } from './fixtures';

describe('墓所のタップ位置', () => {
  it('移動・拡大後の画面座標から墓所を特定する', () => {
    expect(hitGrave(map.graves, { x: 140, y: 60 }, { x: 100, y: 40, scale: 4 })).toBe(
      map.graves[0]!.id,
    );
  });
  it('回転後の矩形内だけを判定する', () => {
    expect(hitGrave(map.graves, { x: 40, y: 23 }, { x: 0, y: 0, scale: 1 })).toBe(
      map.graves[1]!.id,
    );
    expect(hitGrave(map.graves, { x: 31, y: 15 }, { x: 0, y: 0, scale: 1 })).toBeNull();
  });
  it('空白と未初期化の倍率では墓所を返さない', () => {
    expect(hitGrave(map.graves, { x: 25, y: 5 }, { x: 0, y: 0, scale: 1 })).toBeNull();
    expect(hitGrave(map.graves, { x: 10, y: 5 }, { x: 0, y: 0, scale: 0 })).toBeNull();
  });
});
describe('タップとジェスチャーの区別', () => {
  it('小さな指ぶれはタップとして認識する', () => {
    const tap = new MapTap();
    tap.begin({ x: 100, y: 100 }, 1);
    tap.move({ x: 102, y: 102 }, 1);
    expect(tap.end({ x: 103, y: 102 })).toBe(true);
  });
  it('ドラッグして元へ戻っても詳細を開かない', () => {
    const tap = new MapTap();
    tap.begin({ x: 100, y: 100 }, 1);
    tap.move({ x: 130, y: 100 }, 1);
    expect(tap.end({ x: 100, y: 100 })).toBe(false);
  });
  it('移動イベントなしでも離す位置が遠ければ開かない', () => {
    const tap = new MapTap();
    tap.begin({ x: 100, y: 100 }, 1);
    expect(tap.end({ x: 130, y: 100 })).toBe(false);
  });
  it('複数指のあと1本に戻っても詳細を開かない', () => {
    const tap = new MapTap();
    tap.begin({ x: 100, y: 100 }, 1);
    tap.move({ x: 100, y: 100 }, 2);
    tap.move({ x: 100, y: 100 }, 1);
    expect(tap.end({ x: 100, y: 100 })).toBe(false);
  });
  it('複数指で開始した操作とキャンセルされた操作では開かない', () => {
    const tap = new MapTap();
    tap.begin({ x: 100, y: 100 }, 2);
    expect(tap.end({ x: 100, y: 100 })).toBe(false);
    tap.begin({ x: 100, y: 100 }, 1);
    tap.cancel();
    expect(tap.end({ x: 100, y: 100 })).toBe(false);
    tap.begin({ x: 100, y: 100 }, 1);
    expect(tap.end({ x: 100, y: 100 })).toBe(true);
  });
});
