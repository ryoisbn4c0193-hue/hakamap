export type MapPoint = Readonly<{ x: number; y: number }>;

export type MapRect = Readonly<{
  id: string;
  x: number;
  y: number;
  width: number;
  height: number;
  displayOrder?: number;
  rotation?: number;
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

export function inverseViewportScale(viewportScale: number): number {
  return 1 / viewportScale;
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

export function backgroundLocalToMap(point: MapPoint, background: BackgroundTransform): MapPoint {
  const rotated = rotate(
    { x: point.x * background.scaleX, y: point.y * background.scaleY },
    (background.rotation * Math.PI) / 180,
  );
  return { x: rotated.x + background.x, y: rotated.y + background.y };
}

export function mapToBackgroundLocal(point: MapPoint, background: BackgroundTransform): MapPoint {
  const rotated = rotate(
    { x: point.x - background.x, y: point.y - background.y },
    (-background.rotation * Math.PI) / 180,
  );
  return { x: rotated.x / background.scaleX, y: rotated.y / background.scaleY };
}

export function normalizeRotation(rotation: number): number {
  return ((rotation % 360) + 360) % 360;
}

export function snapRotation(rotation: number, threshold = 5): number {
  const normalized = normalizeRotation(rotation);
  const nearest = Math.round(normalized / 90) * 90;
  const distance = Math.abs(normalizeRotation(normalized - nearest + 180) - 180);
  return distance <= threshold ? normalizeRotation(nearest) : normalized;
}

export function rotateBackgroundAroundCenter<T extends BackgroundTransform>(
  background: T,
  rotation: number,
): T {
  const center = backgroundLocalToMap(
    { x: background.width / 2, y: background.height / 2 },
    background,
  );
  const normalized = normalizeRotation(rotation);
  const radians = (normalized * Math.PI) / 180;
  const scaledCenter = {
    x: (background.width * background.scaleX) / 2,
    y: (background.height * background.scaleY) / 2,
  };
  const rotatedCenter = rotate(scaledCenter, radians);
  return {
    ...background,
    rotation: normalized,
    x: center.x - rotatedCenter.x,
    y: center.y - rotatedCenter.y,
  };
}

export function resetBackgroundAspectRatio<T extends BackgroundTransform>(background: T): T {
  const center = backgroundLocalToMap(
    { x: background.width / 2, y: background.height / 2 },
    background,
  );
  const updated = { ...background, scaleY: background.scaleX };
  const updatedCenter = backgroundLocalToMap(
    { x: background.width / 2, y: background.height / 2 },
    updated,
  );
  return {
    ...updated,
    x: updated.x + center.x - updatedCenter.x,
    y: updated.y + center.y - updatedCenter.y,
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
  return bounds(
    [
      { x: 0, y: 0 },
      { x: background.width, y: 0 },
      { x: 0, y: background.height },
      { x: background.width, y: background.height },
    ].map((point) => backgroundLocalToMap(point, background)),
    'background',
  );
}

export function mapBoundsToBackgroundLocal(
  mapBounds: MapRect,
  background: BackgroundTransform,
): MapRect {
  return bounds(
    [
      { x: mapBounds.x, y: mapBounds.y },
      { x: mapBounds.x + mapBounds.width, y: mapBounds.y },
      { x: mapBounds.x, y: mapBounds.y + mapBounds.height },
      { x: mapBounds.x + mapBounds.width, y: mapBounds.y + mapBounds.height },
    ].map((point) => mapToBackgroundLocal(point, background)),
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

export function rectangleLocalToMap(
  point: MapPoint,
  rectangle: MapRect,
  rotation = rectangle.rotation ?? 0,
): MapPoint {
  const center = { x: rectangle.x + rectangle.width / 2, y: rectangle.y + rectangle.height / 2 };
  const rotated = rotate(
    { x: point.x - rectangle.width / 2, y: point.y - rectangle.height / 2 },
    (rotation * Math.PI) / 180,
  );
  return { x: center.x + rotated.x, y: center.y + rotated.y };
}

export function mapToRectangleLocal(
  point: MapPoint,
  rectangle: MapRect,
  rotation = rectangle.rotation ?? 0,
): MapPoint {
  const center = { x: rectangle.x + rectangle.width / 2, y: rectangle.y + rectangle.height / 2 };
  const rotated = rotate(
    { x: point.x - center.x, y: point.y - center.y },
    (-rotation * Math.PI) / 180,
  );
  return { x: rotated.x + rectangle.width / 2, y: rotated.y + rectangle.height / 2 };
}

export function rotatedRectangleCorners(rectangle: MapRect): readonly MapPoint[] {
  return [
    rectangleLocalToMap({ x: 0, y: 0 }, rectangle),
    rectangleLocalToMap({ x: rectangle.width, y: 0 }, rectangle),
    rectangleLocalToMap({ x: rectangle.width, y: rectangle.height }, rectangle),
    rectangleLocalToMap({ x: 0, y: rectangle.height }, rectangle),
  ];
}

export function rotatedRectangleBounds(rectangle: MapRect): MapRect {
  return bounds(rotatedRectangleCorners(rectangle), rectangle.id);
}

export function containsRotated(rectangle: MapRect, point: MapPoint): boolean {
  const local = mapToRectangleLocal(point, rectangle);
  return local.x >= 0 && local.x <= rectangle.width && local.y >= 0 && local.y <= rectangle.height;
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

function projectionsOverlap(
  first: readonly MapPoint[],
  second: readonly MapPoint[],
  axis: MapPoint,
  touching: boolean,
): boolean {
  const project = (points: readonly MapPoint[]) => {
    const values = points.map((point) => point.x * axis.x + point.y * axis.y);
    return { maximum: Math.max(...values), minimum: Math.min(...values) };
  };
  const firstProjection = project(first);
  const secondProjection = project(second);
  return touching
    ? firstProjection.minimum <= secondProjection.maximum &&
        secondProjection.minimum <= firstProjection.maximum
    : firstProjection.minimum < secondProjection.maximum &&
        secondProjection.minimum < firstProjection.maximum;
}

export function rotatedRectanglesIntersect(
  first: MapRect,
  second: MapRect,
  touching = true,
): boolean {
  const firstCorners = rotatedRectangleCorners(first);
  const secondCorners = rotatedRectangleCorners(second);
  const axes = [firstCorners, secondCorners].flatMap((corners) =>
    [0, 1].map((index) => {
      const start = corners[index];
      const end = corners[index + 1];
      const edge = { x: end.x - start.x, y: end.y - start.y };
      const length = Math.hypot(edge.x, edge.y);
      return { x: -edge.y / length, y: edge.x / length };
    }),
  );
  return axes.every((axis) => projectionsOverlap(firstCorners, secondCorners, axis, touching));
}

export function hitTest(rectangles: readonly MapRect[], point: MapPoint): string | undefined {
  return [...rectangles]
    .sort(
      (first, second) =>
        (second.displayOrder ?? 0) - (first.displayOrder ?? 0) || second.id.localeCompare(first.id),
    )
    .find((rectangle) => contains(rectangle, point))?.id;
}

export function hitTestRotated(
  rectangles: readonly MapRect[],
  point: MapPoint,
): string | undefined {
  return [...rectangles]
    .sort(
      (first, second) =>
        (second.displayOrder ?? 0) - (first.displayOrder ?? 0) || second.id.localeCompare(first.id),
    )
    .find((rectangle) => containsRotated(rectangle, point))?.id;
}

export function selectIntersecting(
  rectangles: readonly MapRect[],
  selection: MapRect,
): readonly string[] {
  return rectangles
    .filter((rectangle) => rotatedRectanglesIntersect(rectangle, selection))
    .map(({ id }) => id);
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

export function keepSnappedRectangleInsideArea(
  original: MapRect,
  snapped: MapRect,
  areas: readonly MapRect[],
): MapRect {
  const center = { x: original.x + original.width / 2, y: original.y + original.height / 2 };
  const area = areas.find(
    (candidate) =>
      contains(candidate, center) &&
      original.width <= candidate.width &&
      original.height <= candidate.height,
  );
  if (area === undefined) return snapped;
  return {
    ...snapped,
    x: Math.min(Math.max(snapped.x, area.x), area.x + area.width - snapped.width),
    y: Math.min(Math.max(snapped.y, area.y), area.y + area.height - snapped.height),
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
