import type { Box } from './model';

export type Point = { x: number; y: number };
export type Camera = Point & { scale: number };

/** 画面座標を地図へ戻し、描画と同じ中心回転で墓所を判定する。 */
export function hitGrave(graves: Box[], point: Point, camera: Camera): string | null {
  if (!Number.isFinite(camera.scale) || camera.scale <= 0) return null;
  const x = (point.x - camera.x) / camera.scale;
  const y = (point.y - camera.y) / camera.scale;
  for (let index = graves.length - 1; index >= 0; index -= 1) {
    const grave = graves[index]!;
    const angle = (-grave.rotation * Math.PI) / 180;
    const dx = x - (grave.x + grave.width / 2);
    const dy = y - (grave.y + grave.height / 2);
    const localX = dx * Math.cos(angle) - dy * Math.sin(angle);
    const localY = dx * Math.sin(angle) + dy * Math.cos(angle);
    if (Math.abs(localX) <= grave.width / 2 + 1e-8 && Math.abs(localY) <= grave.height / 2 + 1e-8)
      return grave.id;
  }
  return null;
}

/** 一度でも移動・複数指・キャンセルになった操作はタップへ戻さない。 */
export class MapTap {
  private origin: Point | null = null;
  private eligible = false;

  begin(point: Point, touches: number): void {
    this.origin = point;
    this.eligible = touches === 1;
  }

  move(point: Point, touches: number): void {
    if (
      !this.origin ||
      touches !== 1 ||
      Math.hypot(point.x - this.origin.x, point.y - this.origin.y) > 10
    )
      this.eligible = false;
  }

  end(point: Point): boolean {
    const result =
      this.eligible &&
      this.origin !== null &&
      Math.hypot(point.x - this.origin.x, point.y - this.origin.y) <= 10;
    this.cancel();
    return result;
  }

  cancel(): void {
    this.origin = null;
    this.eligible = false;
  }
}
