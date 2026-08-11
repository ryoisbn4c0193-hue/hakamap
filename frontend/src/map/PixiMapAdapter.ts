import { Application, Assets, Container, Graphics, Sprite, Text, type Texture } from 'pixi.js';

import {
  backgroundBounds,
  backgroundLocalToMap,
  fitViewport,
  hitTestRotated,
  inverseViewportScale,
  intersects,
  keepSnappedRectangleInsideArea,
  mapBoundsToBackgroundLocal,
  mapToBackgroundLocal,
  mapToRectangleLocal,
  normalizeRotation,
  normalizeRect,
  rotateBackgroundAroundCenter,
  rotatedRectangleCorners,
  rotatedRectangleBounds,
  rotatedRectanglesIntersect,
  rectangleLocalToMap,
  screenToMap,
  selectIntersecting,
  snapRectangle,
  snapRotation,
  zoomAt,
  type MapPoint,
  type MapRect,
  type Viewport,
} from './mapGeometry';

export type MapArea = MapRect &
  Readonly<{
    color: string;
    name: string;
    rotation: number;
    visible: boolean;
    displayOrder: number;
  }>;
export type MapGrave = MapRect & Readonly<{ label: string; rotation: number }>;
export type MapRenderModel = Readonly<{
  background?: MapBackground;
  areas: readonly MapArea[];
  graves: readonly MapGrave[];
  labelMode: 'managementNumber' | 'name' | 'both' | 'hidden';
  selectedIds: readonly string[];
}>;
export type MapBackground = Readonly<{
  assetId: string;
  height: number;
  maximumLevel: number;
  projectId: string;
  rotation: number;
  scaleX: number;
  scaleY: number;
  tileSize: number;
  width: number;
  x: number;
  y: number;
}>;
export type MapMode = 'select' | 'editArea' | 'createGrave' | 'createArea' | 'transformBackground';
export type MapCallbacks = Readonly<{
  onAreaSelectionChange: (areaId?: string) => void;
  onCreateArea: (rectangle: MapRect) => void;
  onCreateGrave: (rectangle: MapRect) => void;
  onMoveGraves: (graveIds: readonly string[], delta: MapPoint) => void;
  onResizeGrave: (rectangle: MapRect) => void;
  onUpdateArea: (rectangle: MapRect) => void;
  onTransformBackground: (background: MapBackground) => void;
  onSelectionChange: (graveIds: readonly string[]) => void;
}>;

type PointerOperation =
  | Readonly<{ kind: 'pan'; screen: MapPoint; viewport: Viewport }>
  | Readonly<{ additive: boolean; kind: 'select'; start: MapPoint }>
  | Readonly<{
      ids: readonly string[];
      kind: 'move';
      origin: MapPoint;
      rectangles: readonly MapRect[];
    }>
  | Readonly<{
      action: 'resize' | 'rotate';
      kind: 'resize';
      original: MapGrave;
      start: MapPoint;
      startAngle: number;
    }>
  | Readonly<{
      action: 'move' | 'resize' | 'rotate';
      kind: 'area';
      original: MapArea;
      start: MapPoint;
      startAngle: number;
    }>
  | Readonly<{
      action: 'move' | 'resize' | 'rotate';
      kind: 'background';
      original: MapBackground;
      start: MapPoint;
      startAngle: number;
    }>
  | Readonly<{ kind: 'create'; start: MapPoint }>;

const AREA_COLORS: Readonly<Record<string, number>> = {
  blue: 0x90caf9,
  green: 0xa5d6a7,
  orange: 0xffcc80,
  purple: 0xce93d8,
  red: 0xef9a9a,
  yellow: 0xfff59d,
};
const MAP_LABEL_FONT = '"Yu Gothic UI", "Yu Gothic", Meiryo, sans-serif';
export const SELECTED_GRAVE_COLOR = 0x00e5ff;
export const SELECTED_GRAVE_FILL_ALPHA = 0.45;
export const STATIC_MAP_APPLICATION_OPTIONS = Object.freeze({
  antialias: true,
  autoDensity: true,
  autoStart: false,
  background: '#f4f2eb',
});

export function displayResolution(devicePixelRatio: number): number {
  if (!Number.isFinite(devicePixelRatio)) return 1;
  return Math.min(2, Math.max(1, devicePixelRatio));
}

export function backgroundTileUrl(
  projectId: string,
  level: number,
  column: number,
  row: number,
): string {
  return `/api/v1/projects/${projectId}/background/tiles/${level}/${column}/${row}.png`;
}

export class PixiMapAdapter {
  private application?: Application;
  private callbacks?: MapCallbacks;
  private model: MapRenderModel = {
    areas: [],
    graves: [],
    labelMode: 'both',
    selectedIds: [],
  };
  private mode: MapMode = 'select';
  private operation?: PointerOperation;
  private preview?: MapRect;
  private backgroundPreview?: MapBackground;
  private guides: Readonly<{ x?: number; y?: number }> = {};
  private snapEnabled = true;
  private interactionEnabled = true;
  private textResolution = 1;
  private selectedAreaId?: string;
  private viewport: Viewport = { scale: 1, x: 40, y: 40 };
  private readonly viewportContainer = new Container();
  private readonly backgroundLayer = new Container();
  private readonly areaLayer = new Container();
  private readonly graveLayer = new Container();
  private readonly overlayLayer = new Container();
  private readonly backgroundSprites = new Map<string, Sprite>();
  private readonly backgroundTextures = new Map<string, Texture>();
  private readonly backgroundUrls = new Map<string, string>();
  private readonly backgroundLoadingKeys = new Set<string>();
  private requiredBackgroundKeys: ReadonlySet<string> = new Set();

  async mount(container: HTMLElement, callbacks: MapCallbacks): Promise<void> {
    this.destroy();
    this.callbacks = callbacks;
    this.textResolution = displayResolution(window.devicePixelRatio);
    const application = new Application();
    await application.init({
      ...STATIC_MAP_APPLICATION_OPTIONS,
      resizeTo: container,
      resolution: this.textResolution,
    });
    const probe = document.createElement('canvas');
    const gl = probe.getContext('webgl2') ?? probe.getContext('webgl');
    if (gl !== null && Number(gl.getParameter(gl.MAX_TEXTURE_SIZE)) < 1024) {
      application.destroy(true);
      throw new Error('background-texture-size-unsupported');
    }
    this.application = application;
    this.viewportContainer.addChild(
      this.backgroundLayer,
      this.areaLayer,
      this.graveLayer,
      this.overlayLayer,
    );
    application.stage.addChild(this.viewportContainer);
    container.appendChild(application.canvas);
    application.canvas.addEventListener('pointerdown', this.handlePointerDown);
    application.canvas.addEventListener('pointermove', this.handlePointerMove);
    application.canvas.addEventListener('pointerup', this.handlePointerUp);
    application.canvas.addEventListener('pointercancel', this.cancelOperation);
    application.canvas.addEventListener('wheel', this.handleWheel, { passive: false });
    this.fit();
  }

  update(model: MapRenderModel): void {
    this.model = model;
    this.preview = undefined;
    this.backgroundPreview = undefined;
    this.ensureMinimumZoom();
    this.render();
  }

  setMode(mode: MapMode): void {
    this.mode = mode;
    this.cancelOperation();
  }

  setSnapEnabled(enabled: boolean): void {
    this.snapEnabled = enabled;
    this.guides = {};
    this.render();
  }

  setInteractionEnabled(enabled: boolean): void {
    this.interactionEnabled = enabled;
    if (!enabled) this.cancelOperation();
  }

  zoom(factor: number): void {
    const center = {
      x: (this.application?.screen.width ?? 0) / 2,
      y: (this.application?.screen.height ?? 0) / 2,
    };
    this.viewport = zoomAt(this.viewport, center, factor, this.minimumZoom());
    this.render();
  }

  resize(width: number, height: number): void {
    if (this.application === undefined || width <= 0 || height <= 0) return;
    this.application.renderer.resize(width, height);
    this.ensureMinimumZoom();
    this.render();
  }

  fit(): void {
    if (this.application === undefined) return;
    this.viewport = fitViewport(
      this.fitRectangles(),
      this.application.screen.width,
      this.application.screen.height,
      this.model.background === undefined ? 40 : 0,
      this.model.background === undefined ? 0.1 : 0.001,
    );
    this.render();
  }

  focusGrave(graveId: string): void {
    if (this.application === undefined) return;
    const grave = this.model.graves.find(({ id }) => id === graveId);
    if (grave === undefined) return;
    const scale = Math.max(this.viewport.scale, 1);
    this.viewport = {
      scale,
      x: this.application.screen.width / 2 - (grave.x + grave.width / 2) * scale,
      y: this.application.screen.height / 2 - (grave.y + grave.height / 2) * scale,
    };
    this.render();
  }

  exportImage(range: 'current' | 'selectedArea' = 'current'): string | undefined {
    if (this.application === undefined) return undefined;
    const previousViewport = this.viewport;
    if (range === 'selectedArea') {
      const area = this.model.areas.find(({ id }) => id === this.selectedAreaId);
      if (area === undefined) return undefined;
      this.viewport = fitViewport(
        [area],
        this.application.screen.width,
        this.application.screen.height,
      );
    }
    const selectedIds = this.model.selectedIds;
    this.model = { ...this.model, selectedIds: [] };
    this.overlayLayer.visible = false;
    this.render();
    const image = this.application.canvas.toDataURL('image/png');
    this.model = { ...this.model, selectedIds };
    this.overlayLayer.visible = true;
    this.viewport = previousViewport;
    this.render();
    return image;
  }

  destroy(): void {
    const canvas = this.application?.canvas;
    canvas?.removeEventListener('pointerdown', this.handlePointerDown);
    canvas?.removeEventListener('pointermove', this.handlePointerMove);
    canvas?.removeEventListener('pointerup', this.handlePointerUp);
    canvas?.removeEventListener('pointercancel', this.cancelOperation);
    canvas?.removeEventListener('wheel', this.handleWheel);
    this.application?.destroy(true, {
      children: true,
      context: true,
      style: true,
      texture: true,
      textureSource: true,
    });
    this.application = undefined;
    this.backgroundSprites.clear();
    this.backgroundTextures.clear();
    this.backgroundUrls.clear();
    this.backgroundLoadingKeys.clear();
    this.requiredBackgroundKeys = new Set();
    this.callbacks = undefined;
    this.operation = undefined;
    this.backgroundPreview = undefined;
  }

  private readonly localPoint = (event: PointerEvent | WheelEvent): MapPoint => {
    const bounds = this.application?.canvas.getBoundingClientRect();
    return { x: event.clientX - (bounds?.left ?? 0), y: event.clientY - (bounds?.top ?? 0) };
  };

  private rectangleHandleAction(
    rectangle: MapRect,
    point: MapPoint,
  ): 'resize' | 'rotate' | undefined {
    const handleDistance = 12 / this.viewport.scale;
    const resizeHandle = rectangleLocalToMap(
      { x: rectangle.width, y: rectangle.height },
      rectangle,
    );
    const rotationHandle = rectangleLocalToMap(
      { x: rectangle.width / 2, y: -28 / this.viewport.scale },
      rectangle,
    );
    const near = (handle: MapPoint) =>
      Math.hypot(point.x - handle.x, point.y - handle.y) <= handleDistance;
    if (near(rotationHandle)) return 'rotate';
    if (near(resizeHandle)) return 'resize';
    return undefined;
  }

  private readonly handlePointerDown = (event: PointerEvent): void => {
    if (this.application === undefined || !this.interactionEnabled) return;
    this.application.canvas.setPointerCapture(event.pointerId);
    const screen = this.localPoint(event);
    const map = screenToMap(screen, this.viewport);
    if (event.button === 1 || (event.button === 0 && event.shiftKey && event.altKey)) {
      this.operation = { kind: 'pan', screen, viewport: this.viewport };
      return;
    }
    if (event.button !== 0) return;
    if (this.mode === 'transformBackground') {
      const background = this.model.background;
      if (background !== undefined) {
        const resizeHandle = backgroundLocalToMap(
          { x: background.width, y: background.height },
          background,
        );
        const rotationHandle = backgroundLocalToMap(
          {
            x: background.width / 2,
            y: -28 / (background.scaleY * this.viewport.scale),
          },
          background,
        );
        const handleDistance = 12 / this.viewport.scale;
        const near = (point: MapPoint) =>
          Math.hypot(map.x - point.x, map.y - point.y) <= handleDistance;
        const local = mapToBackgroundLocal(map, background);
        const inside =
          local.x >= 0 &&
          local.x <= background.width &&
          local.y >= 0 &&
          local.y <= background.height;
        let action: 'move' | 'resize' | 'rotate' | undefined;
        if (near(rotationHandle)) action = 'rotate';
        else if (near(resizeHandle)) action = 'resize';
        else if (inside) action = 'move';
        if (action === undefined) return;
        const center = backgroundLocalToMap(
          { x: background.width / 2, y: background.height / 2 },
          background,
        );
        this.operation = {
          action,
          kind: 'background',
          original: background,
          start: map,
          startAngle: Math.atan2(map.y - center.y, map.x - center.x),
        };
      }
      return;
    }
    if (this.mode === 'editArea') {
      const visibleAreas = this.model.areas.filter(({ visible }) => visible);
      const selectedArea = visibleAreas.find(({ id }) => id === this.selectedAreaId);
      const selectedAction =
        selectedArea === undefined ? undefined : this.rectangleHandleAction(selectedArea, map);
      const areaId =
        selectedAction === undefined ? hitTestRotated(visibleAreas, map) : selectedArea?.id;
      this.selectedAreaId = areaId;
      this.callbacks?.onAreaSelectionChange(areaId);
      const area = visibleAreas.find(({ id }) => id === areaId);
      if (area !== undefined) {
        const action = selectedAction ?? 'move';
        this.operation = {
          action,
          kind: 'area',
          original: area,
          start: map,
          startAngle: Math.atan2(
            map.y - (area.y + area.height / 2),
            map.x - (area.x + area.width / 2),
          ),
        };
      }
      this.render();
      return;
    }
    if (this.mode !== 'select') {
      this.operation = { kind: 'create', start: map };
      return;
    }
    const selectedGrave =
      this.model.selectedIds.length === 1
        ? this.model.graves.find(({ id }) => id === this.model.selectedIds[0])
        : undefined;
    const selectedAction =
      selectedGrave === undefined ? undefined : this.rectangleHandleAction(selectedGrave, map);
    const graveId =
      selectedAction === undefined ? hitTestRotated(this.model.graves, map) : selectedGrave?.id;
    if (graveId !== undefined) {
      const grave = this.model.graves.find(({ id }) => id === graveId);
      if (
        grave !== undefined &&
        this.model.selectedIds.length === 1 &&
        this.model.selectedIds[0] === graveId
      ) {
        const action = selectedAction ?? this.rectangleHandleAction(grave, map);
        if (action !== undefined) {
          this.operation = {
            action,
            kind: 'resize',
            original: grave,
            start: map,
            startAngle: Math.atan2(
              map.y - (grave.y + grave.height / 2),
              map.x - (grave.x + grave.width / 2),
            ),
          };
          return;
        }
      }
      const ids = this.model.selectedIds.includes(graveId) ? this.model.selectedIds : [graveId];
      if (event.ctrlKey) {
        const toggled = this.model.selectedIds.includes(graveId)
          ? this.model.selectedIds.filter((id) => id !== graveId)
          : [...this.model.selectedIds, graveId];
        this.callbacks?.onSelectionChange(toggled);
        return;
      }
      this.callbacks?.onSelectionChange(ids);
      this.operation = {
        ids,
        kind: 'move',
        origin: map,
        rectangles: this.model.graves.filter(({ id }) => ids.includes(id)),
      };
      return;
    }
    this.operation = { additive: event.ctrlKey, kind: 'select', start: map };
  };

  private readonly handlePointerMove = (event: PointerEvent): void => {
    if (this.operation === undefined) return;
    const screen = this.localPoint(event);
    const map = screenToMap(screen, this.viewport);
    if (this.operation.kind === 'pan') {
      this.viewport = {
        ...this.operation.viewport,
        x: this.operation.viewport.x + screen.x - this.operation.screen.x,
        y: this.operation.viewport.y + screen.y - this.operation.screen.y,
      };
    } else if (this.operation.kind === 'select' || this.operation.kind === 'create') {
      this.preview = normalizeRect(this.operation.start, map);
    } else if (this.operation.kind === 'resize') {
      if (this.operation.action === 'rotate') {
        const center = {
          x: this.operation.original.x + this.operation.original.width / 2,
          y: this.operation.original.y + this.operation.original.height / 2,
        };
        const angle = Math.atan2(map.y - center.y, map.x - center.x);
        const delta = ((angle - this.operation.startAngle) * 180) / Math.PI;
        this.preview = {
          ...this.operation.original,
          rotation: snapRotation(this.operation.original.rotation + delta),
        };
      } else {
        const local = mapToRectangleLocal(map, this.operation.original);
        const ratio = this.operation.original.width / this.operation.original.height;
        let width = Math.max(1, local.x);
        let height = Math.max(1, local.y);
        if (event.shiftKey) {
          if (
            Math.abs(width - this.operation.original.width) >=
            Math.abs(height - this.operation.original.height)
          )
            height = width / ratio;
          else width = height * ratio;
        }
        this.preview = { ...this.operation.original, width, height };
      }
    } else if (this.operation.kind === 'area') {
      const delta = { x: map.x - this.operation.start.x, y: map.y - this.operation.start.y };
      if (this.operation.action === 'rotate') {
        const center = {
          x: this.operation.original.x + this.operation.original.width / 2,
          y: this.operation.original.y + this.operation.original.height / 2,
        };
        const angle = Math.atan2(map.y - center.y, map.x - center.x);
        const rotationDelta = ((angle - this.operation.startAngle) * 180) / Math.PI;
        this.preview = {
          ...this.operation.original,
          rotation: snapRotation(this.operation.original.rotation + rotationDelta),
        };
      } else if (this.operation.action === 'resize') {
        const local = mapToRectangleLocal(map, this.operation.original);
        this.preview = {
          ...this.operation.original,
          height: Math.max(1, local.y),
          width: Math.max(1, local.x),
        };
      } else {
        let candidate = {
          ...this.operation.original,
          x: this.operation.original.x + delta.x,
          y: this.operation.original.y + delta.y,
        };
        if (this.snapEnabled) {
          const movingAreaId = this.operation.original.id;
          const targets = [
            ...this.model.areas.filter(({ id, visible }) => visible && id !== movingAreaId),
            ...this.model.graves,
          ];
          const snapped = snapRectangle(candidate, targets, this.viewport.scale);
          candidate = { ...candidate, ...snapped.rectangle };
          this.guides = { x: snapped.guideX, y: snapped.guideY };
        }
        this.preview = candidate;
      }
    } else if (this.operation.kind === 'background') {
      const { action, original } = this.operation;
      if (action === 'move') {
        this.backgroundPreview = {
          ...original,
          x: original.x + map.x - this.operation.start.x,
          y: original.y + map.y - this.operation.start.y,
        };
      } else if (action === 'resize') {
        const local = mapToBackgroundLocal(map, original);
        let scaleX = Math.max(0.001, (local.x / original.width) * original.scaleX);
        let scaleY = Math.max(0.001, (local.y / original.height) * original.scaleY);
        if (event.shiftKey) {
          const factor = Math.max(scaleX / original.scaleX, scaleY / original.scaleY);
          scaleX = original.scaleX * factor;
          scaleY = original.scaleY * factor;
        }
        this.backgroundPreview = { ...original, scaleX, scaleY };
      } else {
        const center = backgroundLocalToMap(
          { x: original.width / 2, y: original.height / 2 },
          original,
        );
        const angle = Math.atan2(map.y - center.y, map.x - center.x);
        const delta = ((angle - this.operation.startAngle) * 180) / Math.PI;
        this.backgroundPreview = rotateBackgroundAroundCenter(
          original,
          snapRotation(normalizeRotation(original.rotation + delta)),
        );
      }
    } else {
      let delta = {
        x: map.x - this.operation.origin.x,
        y: map.y - this.operation.origin.y,
      };
      if (event.shiftKey) {
        delta =
          Math.abs(delta.x) >= Math.abs(delta.y) ? { x: delta.x, y: 0 } : { x: 0, y: delta.y };
      }
      const base = this.operation.rectangles[0];
      if (base !== undefined) {
        const movingIds = this.operation.ids;
        let candidate: MapRect = { ...base, x: base.x + delta.x, y: base.y + delta.y };
        if (this.snapEnabled) {
          const targets = [
            ...this.model.graves.filter(({ id }) => !movingIds.includes(id)),
            ...this.model.areas.filter(({ visible }) => visible),
          ];
          const snapped = snapRectangle(candidate, targets, this.viewport.scale);
          candidate = keepSnappedRectangleInsideArea(
            candidate,
            snapped.rectangle,
            this.model.areas.filter(({ visible }) => visible),
          );
          this.guides = { x: snapped.guideX, y: snapped.guideY };
          delta = { x: candidate.x - base.x, y: candidate.y - base.y };
        }
        this.preview = { ...candidate, id: base.id };
      }
    }
    this.render();
  };

  private readonly handlePointerUp = (event: PointerEvent): void => {
    const operation = this.operation;
    if (operation === undefined) return;
    const map = screenToMap(this.localPoint(event), this.viewport);
    if (operation.kind === 'background' && this.backgroundPreview !== undefined) {
      this.callbacks?.onTransformBackground(this.backgroundPreview);
    } else if (operation.kind === 'select') {
      const selection = normalizeRect(operation.start, map);
      const ids =
        selection.width * this.viewport.scale < 3 && selection.height * this.viewport.scale < 3
          ? []
          : selectIntersecting(this.model.graves, selection);
      this.callbacks?.onSelectionChange(
        operation.additive ? [...new Set([...this.model.selectedIds, ...ids])] : ids,
      );
    } else if (operation.kind === 'create') {
      const rectangle = normalizeRect(operation.start, map);
      if (
        rectangle.width * this.viewport.scale >= 3 &&
        rectangle.height * this.viewport.scale >= 3
      ) {
        if (this.mode === 'createArea') this.callbacks?.onCreateArea(rectangle);
        else this.callbacks?.onCreateGrave(rectangle);
      }
    } else if (operation.kind === 'area' && this.preview !== undefined) {
      this.callbacks?.onUpdateArea(this.preview);
    } else if (operation.kind === 'resize' && this.preview !== undefined) {
      this.callbacks?.onResizeGrave(this.preview);
    } else if (operation.kind === 'move' && this.preview !== undefined) {
      const base = operation.rectangles[0];
      if (base !== undefined) {
        const delta = { x: this.preview.x - base.x, y: this.preview.y - base.y };
        if (delta.x !== 0 || delta.y !== 0) this.callbacks?.onMoveGraves(operation.ids, delta);
      }
    }
    this.operation = undefined;
    this.preview = undefined;
    this.backgroundPreview = undefined;
    this.guides = {};
    this.render();
  };

  private readonly cancelOperation = (): void => {
    this.operation = undefined;
    this.preview = undefined;
    this.backgroundPreview = undefined;
    this.guides = {};
    this.render();
  };

  private readonly handleWheel = (event: WheelEvent): void => {
    event.preventDefault();
    this.viewport = zoomAt(
      this.viewport,
      this.localPoint(event),
      event.deltaY < 0 ? 1.2 : 1 / 1.2,
      this.minimumZoom(),
    );
    this.render();
  };

  private fitRectangles(): MapRect[] {
    if (this.model.background !== undefined) {
      return [backgroundBounds(this.model.background)];
    }
    return [
      ...this.model.areas.filter(({ visible }) => visible).map(rotatedRectangleBounds),
      ...this.model.graves.map(rotatedRectangleBounds),
    ];
  }

  private minimumZoom(): number {
    if (this.application === undefined) return 0.1;
    return fitViewport(
      this.fitRectangles(),
      this.application.screen.width,
      this.application.screen.height,
      this.model.background === undefined ? 40 : 0,
      this.model.background === undefined ? 0.1 : 0.001,
    ).scale;
  }

  private ensureMinimumZoom(): void {
    if (this.application === undefined) return;
    const center = {
      x: this.application.screen.width / 2,
      y: this.application.screen.height / 2,
    };
    this.viewport = zoomAt(this.viewport, center, 1, this.minimumZoom());
  }

  private render(): void {
    if (this.application === undefined) return;
    this.viewportContainer.position.set(this.viewport.x, this.viewport.y);
    this.viewportContainer.scale.set(this.viewport.scale);
    this.renderBackground();
    this.areaLayer.removeChildren().forEach((child) => child.destroy());
    this.graveLayer.removeChildren().forEach((child) => child.destroy());
    this.overlayLayer.removeChildren().forEach((child) => child.destroy());
    const viewportBounds: MapRect = {
      height: this.application.screen.height / this.viewport.scale,
      id: 'viewport',
      width: this.application.screen.width / this.viewport.scale,
      x: -this.viewport.x / this.viewport.scale,
      y: -this.viewport.y / this.viewport.scale,
    };
    this.model.areas
      .filter(({ visible }) => visible)
      .sort(
        (first, second) =>
          first.displayOrder - second.displayOrder || first.id.localeCompare(second.id),
      )
      .forEach((area) => {
        const textScale = inverseViewportScale(this.viewport.scale);
        const selected = area.id === this.selectedAreaId;
        const graphic = new Graphics()
          .rect(area.x, area.y, area.width, area.height)
          .fill({ alpha: 0.22, color: AREA_COLORS[area.color] ?? 0x90caf9 })
          .stroke({
            color: selected ? 0x1565c0 : (AREA_COLORS[area.color] ?? 0x1976d2),
            width: (selected ? 2 : 1) / this.viewport.scale,
          });
        graphic.pivot.set(area.x + area.width / 2, area.y + area.height / 2);
        graphic.position.set(area.x + area.width / 2, area.y + area.height / 2);
        graphic.rotation = (area.rotation * Math.PI) / 180;
        const label = new Text({
          resolution: this.textResolution,
          style: {
            fill: 0x17212b,
            fontFamily: MAP_LABEL_FONT,
            fontSize: 14,
            fontWeight: '600',
            stroke: { color: 0xffffff, width: 3 },
          },
          text: area.name,
        });
        label.scale.set(textScale);
        const areaTopLeft = rectangleLocalToMap({ x: 0, y: 0 }, area);
        label.position.set(
          areaTopLeft.x + 3 / this.viewport.scale,
          areaTopLeft.y + 2 / this.viewport.scale,
        );
        this.areaLayer.addChild(graphic, label);
        if (selected && this.mode === 'editArea') {
          this.renderRectangleHandles(area);
        }
      });
    const visibleGraves = this.model.graves.filter((grave) =>
      intersects(rotatedRectangleBounds(grave), viewportBounds),
    );
    const graveGraphics = new Graphics();
    visibleGraves.forEach((grave) => {
      const selected = this.model.selectedIds.includes(grave.id);
      const overlapping =
        this.preview?.id === grave.id &&
        this.model.graves.some(
          (other) =>
            other.id !== grave.id &&
            rotatedRectanglesIntersect(this.preview as MapRect, other, false),
        );
      const corners = rotatedRectangleCorners(grave);
      graveGraphics
        .moveTo(corners[0].x, corners[0].y)
        .lineTo(corners[1].x, corners[1].y)
        .lineTo(corners[2].x, corners[2].y)
        .lineTo(corners[3].x, corners[3].y)
        .closePath()
        .fill({
          alpha: selected ? SELECTED_GRAVE_FILL_ALPHA : 1,
          color: selected ? SELECTED_GRAVE_COLOR : 0xfafafa,
        })
        .stroke({
          color: overlapping ? 0xd32f2f : selected ? SELECTED_GRAVE_COLOR : 0x455a64,
          width: (selected ? 3 : 1) / this.viewport.scale,
        });
      if (
        grave.label.length > 0 &&
        this.model.labelMode !== 'hidden' &&
        this.viewport.scale >= 0.5 &&
        visibleGraves.length <= 300
      ) {
        const textScale = inverseViewportScale(this.viewport.scale);
        const label = new Text({
          resolution: this.textResolution,
          style: {
            fill: 0x17212b,
            fontFamily: MAP_LABEL_FONT,
            fontSize: 14,
            fontWeight: '600',
            stroke: { color: 0xffffff, width: 3 },
          },
          text: grave.label,
        });
        label.scale.set(textScale);
        label.position.set(
          grave.x + grave.width / 2 - label.width / 2,
          grave.y + grave.height / 2 - label.height / 2,
        );
        this.graveLayer.addChild(label);
      }
      if (selected && this.model.selectedIds.length === 1) {
        this.renderRectangleHandles(grave);
      }
    });
    this.graveLayer.addChildAt(graveGraphics, 0);
    if (this.preview !== undefined) {
      const corners = rotatedRectangleCorners(this.preview);
      this.overlayLayer.addChild(
        new Graphics()
          .moveTo(corners[0].x, corners[0].y)
          .lineTo(corners[1].x, corners[1].y)
          .lineTo(corners[2].x, corners[2].y)
          .lineTo(corners[3].x, corners[3].y)
          .closePath()
          .fill({ alpha: 0.12, color: 0x1976d2 })
          .stroke({ color: 0x1976d2, width: 1 / this.viewport.scale }),
      );
    }
    this.renderBackgroundHandles();
    const width = this.application.screen.width / this.viewport.scale;
    const height = this.application.screen.height / this.viewport.scale;
    if (this.guides.x !== undefined) {
      this.overlayLayer.addChild(
        new Graphics()
          .moveTo(this.guides.x, -this.viewport.y / this.viewport.scale)
          .lineTo(this.guides.x, height - this.viewport.y / this.viewport.scale)
          .stroke({ color: 0xe91e63, width: 1 / this.viewport.scale }),
      );
    }
    if (this.guides.y !== undefined) {
      this.overlayLayer.addChild(
        new Graphics()
          .moveTo(-this.viewport.x / this.viewport.scale, this.guides.y)
          .lineTo(width - this.viewport.x / this.viewport.scale, this.guides.y)
          .stroke({ color: 0xe91e63, width: 1 / this.viewport.scale }),
      );
    }
    this.application.render();
  }

  private renderBackground(): void {
    const background = this.backgroundPreview ?? this.model.background;
    if (background === undefined || this.application === undefined) {
      this.requiredBackgroundKeys = new Set();
      this.releaseBackgroundTiles(new Set());
      return;
    }
    const level = Math.min(
      background.maximumLevel,
      Math.max(0, Math.round(Math.log2(1 / Math.max(this.viewport.scale, 0.0001)))),
    );
    const levelScale = 2 ** level;
    const visible = mapBoundsToBackgroundLocal(
      {
        height: this.application.screen.height / this.viewport.scale,
        id: 'viewport',
        width: this.application.screen.width / this.viewport.scale,
        x: -this.viewport.x / this.viewport.scale,
        y: -this.viewport.y / this.viewport.scale,
      },
      background,
    );
    const tileMapSize = background.tileSize * levelScale;
    const maximumColumn = Math.ceil(background.width / tileMapSize) - 1;
    const maximumRow = Math.ceil(background.height / tileMapSize) - 1;
    const firstColumn = Math.max(0, Math.floor(visible.x / tileMapSize) - 1);
    const lastColumn = Math.min(
      maximumColumn,
      Math.floor((visible.x + visible.width) / tileMapSize) + 1,
    );
    const firstRow = Math.max(0, Math.floor(visible.y / tileMapSize) - 1);
    const lastRow = Math.min(
      maximumRow,
      Math.floor((visible.y + visible.height) / tileMapSize) + 1,
    );
    const required = new Set<string>();
    this.requiredBackgroundKeys = required;
    for (let row = firstRow; row <= lastRow; row += 1) {
      for (let column = firstColumn; column <= lastColumn; column += 1) {
        const key = `${background.assetId}:${level}:${column}:${row}`;
        required.add(key);
        if (
          !this.backgroundSprites.has(key) &&
          !this.backgroundTextures.has(key) &&
          !this.backgroundLoadingKeys.has(key)
        ) {
          const url = backgroundTileUrl(background.projectId, level, column, row);
          this.backgroundUrls.set(key, url);
          this.backgroundLoadingKeys.add(key);
          void Assets.load<Texture>({ format: 'png', src: url })
            .then((texture) => {
              if (this.application === undefined || !this.requiredBackgroundKeys.has(key)) {
                void Assets.unload(url).catch(() => undefined);
                return;
              }
              const sprite = new Sprite(texture);
              sprite.position.set(column * tileMapSize, row * tileMapSize);
              sprite.width = Math.min(tileMapSize, background.width - column * tileMapSize);
              sprite.height = Math.min(tileMapSize, background.height - row * tileMapSize);
              this.backgroundTextures.set(key, texture);
              this.backgroundSprites.set(key, sprite);
              this.backgroundLayer.addChild(sprite);
              this.application?.render();
            })
            .catch(() => {
              this.backgroundUrls.delete(key);
            })
            .finally(() => {
              this.backgroundLoadingKeys.delete(key);
            });
        }
      }
    }
    this.backgroundLayer.position.set(background.x, background.y);
    this.backgroundLayer.scale.set(background.scaleX, background.scaleY);
    this.backgroundLayer.rotation = (background.rotation * Math.PI) / 180;
    this.releaseBackgroundTiles(required);
  }

  private renderBackgroundHandles(): void {
    const background = this.backgroundPreview ?? this.model.background;
    if (this.mode !== 'transformBackground' || background === undefined) return;
    const corners = [
      backgroundLocalToMap({ x: 0, y: 0 }, background),
      backgroundLocalToMap({ x: background.width, y: 0 }, background),
      backgroundLocalToMap({ x: background.width, y: background.height }, background),
      backgroundLocalToMap({ x: 0, y: background.height }, background),
    ];
    const resizeHandle = corners[2];
    const topCenter = backgroundLocalToMap({ x: background.width / 2, y: 0 }, background);
    const rotationHandle = backgroundLocalToMap(
      {
        x: background.width / 2,
        y: -28 / (background.scaleY * this.viewport.scale),
      },
      background,
    );
    const size = 10 / this.viewport.scale;
    const outline = new Graphics()
      .moveTo(corners[0].x, corners[0].y)
      .lineTo(corners[1].x, corners[1].y)
      .lineTo(corners[2].x, corners[2].y)
      .lineTo(corners[3].x, corners[3].y)
      .closePath()
      .stroke({ color: 0x1565c0, width: 2 / this.viewport.scale })
      .moveTo(topCenter.x, topCenter.y)
      .lineTo(rotationHandle.x, rotationHandle.y)
      .stroke({ color: 0x1565c0, width: 1 / this.viewport.scale });
    const resize = new Graphics()
      .rect(resizeHandle.x - size / 2, resizeHandle.y - size / 2, size, size)
      .fill({ color: 0xffffff })
      .stroke({ color: 0x1565c0, width: 1 / this.viewport.scale });
    const rotation = new Graphics()
      .circle(rotationHandle.x, rotationHandle.y, size / 2)
      .fill({ color: 0xffffff })
      .stroke({ color: 0x1565c0, width: 1 / this.viewport.scale });
    this.overlayLayer.addChild(outline, resize, rotation);
  }

  private renderRectangleHandles(rectangle: MapRect): void {
    const resizeHandle = rectangleLocalToMap(
      { x: rectangle.width, y: rectangle.height },
      rectangle,
    );
    const topCenter = rectangleLocalToMap({ x: rectangle.width / 2, y: 0 }, rectangle);
    const rotationHandle = rectangleLocalToMap(
      { x: rectangle.width / 2, y: -28 / this.viewport.scale },
      rectangle,
    );
    const size = 10 / this.viewport.scale;
    this.overlayLayer.addChild(
      new Graphics()
        .moveTo(topCenter.x, topCenter.y)
        .lineTo(rotationHandle.x, rotationHandle.y)
        .stroke({ color: 0x1565c0, width: 1 / this.viewport.scale }),
      new Graphics()
        .rect(resizeHandle.x - size / 2, resizeHandle.y - size / 2, size, size)
        .fill({ color: 0xffffff })
        .stroke({ color: 0x1565c0, width: 1 / this.viewport.scale }),
      new Graphics()
        .circle(rotationHandle.x, rotationHandle.y, size / 2)
        .fill({ color: 0xffffff })
        .stroke({ color: 0x1565c0, width: 1 / this.viewport.scale }),
    );
  }

  private releaseBackgroundTiles(required: ReadonlySet<string>): void {
    this.backgroundSprites.forEach((sprite, key) => {
      if (!required.has(key)) {
        this.backgroundLayer.removeChild(sprite);
        sprite.destroy();
        this.backgroundSprites.delete(key);
        this.backgroundTextures.delete(key);
        const url = this.backgroundUrls.get(key);
        if (url !== undefined) void Assets.unload(url).catch(() => undefined);
        this.backgroundUrls.delete(key);
      }
    });
  }
}
