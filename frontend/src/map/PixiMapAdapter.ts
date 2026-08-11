import { Application, Assets, Container, Graphics, Sprite, Text, type Texture } from 'pixi.js';

import {
  backgroundBounds,
  fitViewport,
  hitTest,
  inverseViewportScale,
  intersects,
  mapBoundsToBackgroundLocal,
  normalizeRect,
  screenToMap,
  selectIntersecting,
  snapRectangle,
  zoomAt,
  type MapPoint,
  type MapRect,
  type Viewport,
} from './mapGeometry';

export type MapArea = MapRect &
  Readonly<{ color: string; name: string; visible: boolean; displayOrder: number }>;
export type MapGrave = MapRect & Readonly<{ label: string; rotation: number }>;
export type MapRenderModel = Readonly<{
  background?: Readonly<{
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
  areas: readonly MapArea[];
  graves: readonly MapGrave[];
  labelMode: 'managementNumber' | 'name' | 'both' | 'hidden';
  selectedIds: readonly string[];
}>;
export type MapMode = 'select' | 'editArea' | 'createGrave' | 'createArea' | 'transformBackground';
export type MapCallbacks = Readonly<{
  onCreateArea: (rectangle: MapRect) => void;
  onCreateGrave: (rectangle: MapRect) => void;
  onMoveGraves: (graveIds: readonly string[], delta: MapPoint) => void;
  onResizeGrave: (rectangle: MapRect) => void;
  onUpdateArea: (rectangle: MapRect) => void;
  onTransformBackground: (x: number, y: number) => void;
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
  | Readonly<{ kind: 'resize'; original: MapRect; start: MapPoint }>
  | Readonly<{ kind: 'area'; original: MapRect; start: MapPoint }>
  | Readonly<{ kind: 'background'; original: MapPoint; start: MapPoint }>
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

export function displayResolution(devicePixelRatio: number): number {
  if (!Number.isFinite(devicePixelRatio)) return 1;
  return Math.min(2, Math.max(1, devicePixelRatio));
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
  private requiredBackgroundKeys: ReadonlySet<string> = new Set();

  async mount(container: HTMLElement, callbacks: MapCallbacks): Promise<void> {
    this.destroy();
    this.callbacks = callbacks;
    this.textResolution = displayResolution(window.devicePixelRatio);
    const application = new Application();
    await application.init({
      antialias: true,
      autoDensity: true,
      background: '#f4f2eb',
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
    this.viewport = zoomAt(this.viewport, center, factor);
    this.render();
  }

  resize(width: number, height: number): void {
    if (this.application === undefined || width <= 0 || height <= 0) return;
    this.application.renderer.resize(width, height);
    this.render();
  }

  fit(): void {
    if (this.application === undefined) return;
    const background =
      this.model.background === undefined ? [] : [backgroundBounds(this.model.background)];
    this.viewport = fitViewport(
      [...background, ...this.model.areas.filter(({ visible }) => visible), ...this.model.graves],
      this.application.screen.width,
      this.application.screen.height,
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
    this.requiredBackgroundKeys = new Set();
    this.callbacks = undefined;
    this.operation = undefined;
  }

  private readonly localPoint = (event: PointerEvent | WheelEvent): MapPoint => {
    const bounds = this.application?.canvas.getBoundingClientRect();
    return { x: event.clientX - (bounds?.left ?? 0), y: event.clientY - (bounds?.top ?? 0) };
  };

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
        this.operation = {
          kind: 'background',
          original: { x: background.x, y: background.y },
          start: map,
        };
      }
      return;
    }
    if (this.mode === 'editArea') {
      const visibleAreas = this.model.areas.filter(({ visible }) => visible);
      const areaId = hitTest(visibleAreas, map);
      this.selectedAreaId = areaId;
      const area = visibleAreas.find(({ id }) => id === areaId);
      if (area !== undefined) this.operation = { kind: 'area', original: area, start: map };
      this.render();
      return;
    }
    if (this.mode !== 'select') {
      this.operation = { kind: 'create', start: map };
      return;
    }
    const graveId = hitTest(this.model.graves, map);
    if (graveId !== undefined) {
      const grave = this.model.graves.find(({ id }) => id === graveId);
      const handleDistance = 8 / this.viewport.scale;
      if (
        grave !== undefined &&
        this.model.selectedIds.length === 1 &&
        this.model.selectedIds[0] === graveId &&
        Math.abs(map.x - (grave.x + grave.width)) <= handleDistance &&
        Math.abs(map.y - (grave.y + grave.height)) <= handleDistance
      ) {
        this.operation = { kind: 'resize', original: grave, start: map };
        return;
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
      const delta = { x: map.x - this.operation.start.x, y: map.y - this.operation.start.y };
      const ratio = this.operation.original.width / this.operation.original.height;
      let width = Math.max(1, this.operation.original.width + delta.x);
      let height = Math.max(1, this.operation.original.height + delta.y);
      if (event.shiftKey) {
        if (Math.abs(delta.x) >= Math.abs(delta.y)) height = width / ratio;
        else width = height * ratio;
      }
      this.preview = { ...this.operation.original, width, height };
    } else if (this.operation.kind === 'area') {
      this.preview = {
        ...this.operation.original,
        x: this.operation.original.x + map.x - this.operation.start.x,
        y: this.operation.original.y + map.y - this.operation.start.y,
      };
    } else if (this.operation.kind === 'background') {
      this.preview = {
        height: 0,
        id: 'background',
        width: 0,
        x: this.operation.original.x + map.x - this.operation.start.x,
        y: this.operation.original.y + map.y - this.operation.start.y,
      };
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
          candidate = snapped.rectangle;
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
    if (operation.kind === 'background' && this.preview !== undefined) {
      this.callbacks?.onTransformBackground(this.preview.x, this.preview.y);
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
    this.guides = {};
    this.render();
  };

  private readonly cancelOperation = (): void => {
    this.operation = undefined;
    this.preview = undefined;
    this.guides = {};
    this.render();
  };

  private readonly handleWheel = (event: WheelEvent): void => {
    event.preventDefault();
    this.viewport = zoomAt(this.viewport, this.localPoint(event), event.deltaY < 0 ? 1.2 : 1 / 1.2);
    this.render();
  };

  private render(): void {
    if (this.application === undefined) return;
    this.viewportContainer.position.set(this.viewport.x, this.viewport.y);
    this.viewportContainer.scale.set(this.viewport.scale);
    this.renderBackground();
    this.areaLayer.removeChildren().forEach((child) => child.destroy());
    this.graveLayer.removeChildren().forEach((child) => child.destroy());
    this.overlayLayer.removeChildren().forEach((child) => child.destroy());
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
        label.position.set(area.x + 3 / this.viewport.scale, area.y + 2 / this.viewport.scale);
        this.areaLayer.addChild(graphic, label);
      });
    const viewportBounds: MapRect = {
      height: this.application.screen.height / this.viewport.scale,
      id: 'viewport',
      width: this.application.screen.width / this.viewport.scale,
      x: -this.viewport.x / this.viewport.scale,
      y: -this.viewport.y / this.viewport.scale,
    };
    this.model.graves.forEach((grave) => {
      const selected = this.model.selectedIds.includes(grave.id);
      const overlapping =
        this.preview?.id === grave.id &&
        this.model.graves.some(
          (other) => other.id !== grave.id && intersects(this.preview as MapRect, other, false),
        );
      const graphic = new Graphics()
        .rect(grave.x, grave.y, grave.width, grave.height)
        .fill({ color: 0xfafafa })
        .stroke({
          color: overlapping ? 0xd32f2f : selected ? 0x1565c0 : 0x455a64,
          width: (selected ? 2 : 1) / this.viewport.scale,
        });
      this.graveLayer.addChild(graphic);
      if (
        grave.label.length > 0 &&
        this.model.labelMode !== 'hidden' &&
        this.viewport.scale >= 0.5 &&
        intersects(grave, viewportBounds)
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
        const size = 8 / this.viewport.scale;
        this.overlayLayer.addChild(
          new Graphics()
            .rect(grave.x + grave.width - size / 2, grave.y + grave.height - size / 2, size, size)
            .fill({ color: 0xffffff })
            .stroke({ color: 0x1565c0, width: 1 / this.viewport.scale }),
        );
      }
    });
    if (this.preview !== undefined) {
      this.overlayLayer.addChild(
        new Graphics()
          .rect(this.preview.x, this.preview.y, this.preview.width, this.preview.height)
          .fill({ alpha: 0.12, color: 0x1976d2 })
          .stroke({ color: 0x1976d2, width: 1 / this.viewport.scale }),
      );
    }
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
    const background = this.model.background;
    if (background === undefined || this.application === undefined) {
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
    for (let row = firstRow; row <= lastRow; row += 1) {
      for (let column = firstColumn; column <= lastColumn; column += 1) {
        const key = `${background.assetId}:${level}:${column}:${row}`;
        required.add(key);
        if (!this.backgroundSprites.has(key) && !this.backgroundTextures.has(key)) {
          const url = `/api/v1/projects/${background.projectId}/background/tiles/${level}/${column}/${row}`;
          this.backgroundUrls.set(key, url);
          void Assets.load<Texture>(url).then((texture) => {
            if (this.application === undefined || !this.requiredBackgroundKeys.has(key)) {
              texture.destroy(true);
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
          });
        }
      }
    }
    this.backgroundLayer.position.set(background.x, background.y);
    this.backgroundLayer.scale.set(background.scaleX, background.scaleY);
    this.backgroundLayer.rotation = (background.rotation * Math.PI) / 180;
    this.requiredBackgroundKeys = required;
    this.releaseBackgroundTiles(required);
  }

  private releaseBackgroundTiles(required: ReadonlySet<string>): void {
    this.backgroundSprites.forEach((sprite, key) => {
      if (!required.has(key)) {
        this.backgroundLayer.removeChild(sprite);
        sprite.destroy();
        this.backgroundSprites.delete(key);
        this.backgroundTextures.get(key)?.destroy(true);
        this.backgroundTextures.delete(key);
        const url = this.backgroundUrls.get(key);
        if (url !== undefined) void Assets.unload(url);
        this.backgroundUrls.delete(key);
      }
    });
  }
}
