export type MapPoint = Readonly<{ x: number; y: number }>;

export type MapRect = Readonly<{
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  displayOrder?: number;
}>;

export type Viewport = Readonly<{ scale: number; x: number; y: number }>;
export type BackgroundTransform = Readonly<{
  height: number;
  rotation: number;
  scaleX: number;
  scaleY: number;
  width: number;
  x: number;
  y: number;
}>;

export const MIN_ZOOM = 0.1;
export const MAX_ZOOM = 8;
export const SNAP_DISTANCE_PX = 8;
export type GraveLabelMode = 'managementNumber' | 'name' | 'both' | 'hidden';

export function graveLabel(
  managementNumber: string | null,
  name: string | null,
  mode: GraveLabelMode,
): string {
  const numberLabel = managementNumber ?? '未採番';
  const nameLabel = name ?? '';
  if (mode === 'managementNumber') return numberLabel;
  if (mode === 'name') return nameLabel;
  if (mode === 'both') return `${numberLabel} ${nameLabel}`.trim();
  return '';
}

export function clampZoom(scale: number): number {
  return Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, scale));
}

export function mapToScreen(point: MapPoint, viewport: Viewport): MapPoint {
  return {
    x: point.x * viewport.scale + viewport.x,
    y: point.y * viewport.scale + viewport.y,
  };
}

export function screenToMap(point: MapPoint, viewport: Viewport): MapPoint {
  return {
    x: (point.x - viewport.x) / viewport.scale,
    y: (point.y - viewport.y) / viewport.scale,
  };
}

export function zoomAt(viewport: Viewport, screenPoint: MapPoint, factor: number): Viewport {
  const mapPoint = screenToMap(screenPoint, viewport);
  const scale = clampZoom(viewport.scale * factor);
  return {
    scale,
    x: screenPoint.x - mapPoint.x * scale,
    y: screenPoint.y - mapPoint.y * scale,
  };
}

function rotate(point: MapPoint, radians: number): MapPoint {
  return {
    x: point.x * Math.cos(radians) - point.y * Math.sin(radians),
    y: point.x * Math.sin(radians) + point.y * Math.cos(radians),
  };
}

function bounds(points: readonly MapPoint[], id: string): MapRect {
  const left = Math.min(...points.map(({ x }) => x));
  const top = Math.min(...points.map(({ y }) => y));
  const right = Math.max(...points.map(({ x }) => x));
  const bottom = Math.max(...points.map(({ y }) => y));
  return { height: bottom - top, id, width: right - left, x: left, y: top };
}

export function backgroundBounds(background: BackgroundTransform): MapRect {
  const radians = (background.rotation * Math.PI) / 180;
  return bounds(
    [
      { x: 0, y: 0 },
      { x: background.width * background.scaleX, y: 0 },
      { x: 0, y: background.height * background.scaleY },
      {
        x: background.width * background.scaleX,
        y: background.height * background.scaleY,
      },
    ].map((point) => {
      const rotated = rotate(point, radians);
      return { x: rotated.x + background.x, y: rotated.y + background.y };
    }),
    'background',
  );
}

export function mapBoundsToBackgroundLocal(
  mapBounds: MapRect,
  background: BackgroundTransform,
): MapRect {
  const inverseRadians = (-background.rotation * Math.PI) / 180;
  return bounds(
    [
      { x: mapBounds.x, y: mapBounds.y },
      { x: mapBounds.x + mapBounds.width, y: mapBounds.y },
      { x: mapBounds.x, y: mapBounds.y + mapBounds.height },
      { x: mapBounds.x + mapBounds.width, y: mapBounds.y + mapBounds.height },
    ].map((point) => {
      const rotated = rotate(
        { x: point.x - background.x, y: point.y - background.y },
        inverseRadians,
      );
      return { x: rotated.x / background.scaleX, y: rotated.y / background.scaleY };
    }),
    'background-local',
  );
}

export function normalizeRect(first: MapPoint, second: MapPoint): MapRect {
  const x = Math.min(first.x, second.x);
  const y = Math.min(first.y, second.y);
  return {
    id: 'selection',
    x,
    y,
    width: Math.max(first.x, second.x) - x,
    height: Math.max(first.y, second.y) - y,
  };
}

export function contains(rect: MapRect, point: MapPoint): boolean {
  return (
    point.x >= rect.x &&
    point.x <= rect.x + rect.width &&
    point.y >= rect.y &&
    point.y <= rect.y + rect.height
  );
}

export function intersects(first: MapRect, second: MapRect, touching = true): boolean {
  const compare = touching
    ? (left: number, right: number) => left <= right
    : (left: number, right: number) => left < right;
  return (
    compare(first.x, second.x + second.width) &&
    compare(second.x, first.x + first.width) &&
    compare(first.y, second.y + second.height) &&
    compare(second.y, first.y + first.height)
  );
}

export function hitTest(rectangles: readonly MapRect[], point: MapPoint): string | undefined {
  return [...rectangles]
    .sort(
      (first, second) =>
        (second.displayOrder ?? 0) - (first.displayOrder ?? 0) || second.id.localeCompare(first.id),
    )
    .find((rectangle) => contains(rectangle, point))?.id;
}

export function selectIntersecting(
  rectangles: readonly MapRect[],
  selection: MapRect,
): readonly string[] {
  return rectangles.filter((rectangle) => intersects(rectangle, selection)).map(({ id }) => id);
}

type SnapAxis = Readonly<{ value: number; kind: 'edge' | 'corner' }>;

export type SnapResult = Readonly<{
  rectangle: MapRect;
  guideX?: number;
  guideY?: number;
}>;

function axes(rectangle: MapRect, horizontal: boolean): readonly SnapAxis[] {
  const start = horizontal ? rectangle.x : rectangle.y;
  const size = horizontal ? rectangle.width : rectangle.height;
  return [
    { kind: 'corner', value: start },
    { kind: 'edge', value: start + size / 2 },
    { kind: 'corner', value: start + size },
  ];
}

function bestCorrection(
  moving: MapRect,
  targets: readonly MapRect[],
  horizontal: boolean,
  threshold: number,
): Readonly<{ correction: number; guide: number }> | undefined {
  const candidates = axes(moving, horizontal).flatMap((source) =>
    targets.flatMap((target) =>
      axes(target, horizontal).map((destination) => ({
        correction: destination.value - source.value,
        destination,
        source,
        target,
      })),
    ),
  );
  return candidates
    .filter(({ correction }) => Math.abs(correction) <= threshold)
    .sort(
      (first, second) =>
        Math.abs(first.correction) - Math.abs(second.correction) ||
        Number(second.source.kind === 'corner' && second.destination.kind === 'corner') -
          Number(first.source.kind === 'corner' && first.destination.kind === 'corner') ||
        (first.target.displayOrder ?? 0) - (second.target.displayOrder ?? 0) ||
        first.target.id.localeCompare(second.target.id),
    )
    .map(({ correction, destination }) => ({ correction, guide: destination.value }))[0];
}

export function snapRectangle(
  moving: MapRect,
  targets: readonly MapRect[],
  viewportScale: number,
): SnapResult {
  const threshold = SNAP_DISTANCE_PX / viewportScale;
  const horizontal = bestCorrection(moving, targets, true, threshold);
  const vertical = bestCorrection(moving, targets, false, threshold);
  return {
    rectangle: {
      ...moving,
      x: moving.x + (horizontal?.correction ?? 0),
      y: moving.y + (vertical?.correction ?? 0),
    },
    guideX: horizontal?.guide,
    guideY: vertical?.guide,
  };
}

export function fitViewport(
  rectangles: readonly MapRect[],
  width: number,
  height: number,
  padding = 40,
): Viewport {
  if (rectangles.length === 0) {
    return { scale: 1, x: width / 2, y: height / 2 };
  }
  const left = Math.min(...rectangles.map(({ x }) => x));
  const top = Math.min(...rectangles.map(({ y }) => y));
  const right = Math.max(...rectangles.map(({ x, width: itemWidth }) => x + itemWidth));
  const bottom = Math.max(...rectangles.map(({ y, height: itemHeight }) => y + itemHeight));
  const contentWidth = Math.max(1, right - left);
  const contentHeight = Math.max(1, bottom - top);
  const scale = clampZoom(
    Math.min((width - padding * 2) / contentWidth, (height - padding * 2) / contentHeight),
  );
  return {
    scale,
    x: (width - contentWidth * scale) / 2 - left * scale,
    y: (height - contentHeight * scale) / 2 - top * scale,
  };
}
