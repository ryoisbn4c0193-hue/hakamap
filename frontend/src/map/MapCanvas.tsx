import {
  AspectRatio,
  DeleteOutlineOutlined,
  FitScreen,
  ImageOutlined,
  PrintOutlined,
  Redo,
  Undo,
  ZoomIn,
  ZoomOut,
} from '@mui/icons-material';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';

import { getBackgroundTileManifest, type ProjectSnapshot } from '../api/hakamapClient';

import {
  graveLabel,
  resetBackgroundAspectRatio,
  type GraveLabelMode,
  type MapPoint,
  type MapRect,
} from './mapGeometry';
import MapOutputDialog from './MapOutputDialog';
import {
  PixiMapAdapter,
  type MapBackground,
  type MapMode,
  type MapRenderModel,
} from './PixiMapAdapter';

type MapCanvasProps = {
  busy: boolean;
  canRedo: boolean;
  canUndo: boolean;
  labelMode: GraveLabelMode;
  onAreaNameChange: (areaId: string, name: string) => void;
  onChooseBackground: () => void;
  onCreateArea: (rectangle: MapRect) => void;
  onCreateGrave: (rectangle: MapRect) => void;
  onMoveGraves: (graveIds: readonly string[], delta: MapPoint) => void;
  onHistoryChange: (action: 'undo' | 'redo') => void;
  onNudgeGraves: (graveIds: readonly string[], delta: MapPoint) => void;
  onRemoveBackground: () => void;
  onResizeGrave: (rectangle: MapRect) => void;
  onUpdateArea: (rectangle: MapRect) => void;
  onTransformBackground: (background: MapBackground) => void;
  onSelectionChange: (graveIds: readonly string[]) => void;
  onLabelModeChange: (mode: MapCanvasProps['labelMode']) => void;
  selectedIds: readonly string[];
  searchHighlightedGraveId?: string;
  focusedGraveId?: string;
  snapshot: ProjectSnapshot;
};

function MapCanvas({
  busy,
  canRedo,
  canUndo,
  labelMode,
  onAreaNameChange,
  onChooseBackground,
  onCreateArea,
  onCreateGrave,
  onMoveGraves,
  onHistoryChange,
  onNudgeGraves,
  onRemoveBackground,
  onResizeGrave,
  onUpdateArea,
  onTransformBackground,
  onSelectionChange,
  onLabelModeChange,
  selectedIds,
  searchHighlightedGraveId,
  focusedGraveId,
  snapshot,
}: MapCanvasProps) {
  const [selectedAreaId, setSelectedAreaId] = useState<string>();
  const host = useRef<HTMLDivElement>(null);
  const adapter = useRef<PixiMapAdapter | undefined>(undefined);
  const fittedBackgroundId = useRef<string | undefined>(undefined);
  const callbacks = useRef({
    onAreaSelectionChange: (areaId?: string) => setSelectedAreaId(areaId),
    onCreateArea,
    onCreateGrave,
    onMoveGraves,
    onResizeGrave,
    onUpdateArea,
    onTransformBackground,
    onSelectionChange,
  });
  useEffect(() => {
    callbacks.current = {
      onAreaSelectionChange: (areaId?: string) => setSelectedAreaId(areaId),
      onCreateArea,
      onCreateGrave,
      onMoveGraves,
      onResizeGrave,
      onUpdateArea,
      onTransformBackground,
      onSelectionChange,
    };
  }, [
    onCreateArea,
    onCreateGrave,
    onMoveGraves,
    onResizeGrave,
    onSelectionChange,
    onTransformBackground,
    onUpdateArea,
  ]);
  const [mode, setMode] = useState<MapMode>('select');
  const [snap, setSnap] = useState(true);
  const [outputOpen, setOutputOpen] = useState(false);
  const [removeBackgroundOpen, setRemoveBackgroundOpen] = useState(false);
  const backgroundManifest = useQuery({
    enabled: snapshot.background !== null,
    queryFn: () => getBackgroundTileManifest(snapshot.projectId),
    queryKey: ['backgroundTileManifest', snapshot.projectId, snapshot.background?.assetId],
  });
  const model: MapRenderModel = useMemo(
    () => ({
      background:
        snapshot.background === null || backgroundManifest.data === undefined
          ? undefined
          : {
              ...snapshot.background,
              ...backgroundManifest.data,
              projectId: snapshot.projectId,
            },
      areas: snapshot.areas.map((area) => ({
        color: area.colorPreset,
        displayOrder: area.displayOrder,
        height: area.height,
        id: area.areaId,
        name: area.name,
        rotation: area.rotation,
        visible: area.visible,
        width: area.width,
        x: area.x,
        y: area.y,
      })),
      graves: snapshot.graves.map((grave, displayOrder) => ({
        displayOrder,
        height: grave.height,
        id: grave.graveId,
        label: graveLabel(grave.managementNumber, grave.name, labelMode),
        rotation: grave.rotation,
        width: grave.width,
        x: grave.x,
        y: grave.y,
      })),
      labelMode,
      searchHighlightedGraveId,
      selectedIds,
    }),
    [
      backgroundManifest.data,
      labelMode,
      searchHighlightedGraveId,
      selectedIds,
      snapshot.areas,
      snapshot.background,
      snapshot.graves,
      snapshot.projectId,
    ],
  );

  useEffect(() => {
    const element = host.current;
    if (element === null || import.meta.env.MODE === 'test') return undefined;
    const next = new PixiMapAdapter();
    adapter.current = next;
    const observer = new ResizeObserver(() => {
      next.resize(element.clientWidth, element.clientHeight);
    });
    observer.observe(element);
    void next
      .mount(element, {
        onAreaSelectionChange: (areaId) => callbacks.current.onAreaSelectionChange(areaId),
        onCreateArea: (rectangle) => callbacks.current.onCreateArea(rectangle),
        onCreateGrave: (rectangle) => callbacks.current.onCreateGrave(rectangle),
        onMoveGraves: (ids, delta) => callbacks.current.onMoveGraves(ids, delta),
        onResizeGrave: (rectangle) => callbacks.current.onResizeGrave(rectangle),
        onUpdateArea: (rectangle) => callbacks.current.onUpdateArea(rectangle),
        onTransformBackground: (background) => callbacks.current.onTransformBackground(background),
        onSelectionChange: (ids) => callbacks.current.onSelectionChange(ids),
      })
      .then(() => next.resize(element.clientWidth, element.clientHeight));
    return () => {
      observer.disconnect();
      next.destroy();
      adapter.current = undefined;
    };
  }, []);

  useEffect(() => adapter.current?.update(model), [model]);
  useEffect(() => {
    const backgroundId = model.background?.assetId;
    const current = adapter.current;
    if (
      current !== undefined &&
      backgroundId !== undefined &&
      fittedBackgroundId.current !== backgroundId
    ) {
      current.fit();
      fittedBackgroundId.current = backgroundId;
    }
  }, [model.background?.assetId]);
  useEffect(() => adapter.current?.setMode(mode), [mode]);
  useEffect(() => adapter.current?.setSnapEnabled(snap), [snap]);
  useEffect(() => adapter.current?.setInteractionEnabled(!busy), [busy]);
  useEffect(() => {
    if (focusedGraveId !== undefined) adapter.current?.focusGrave(focusedGraveId);
  }, [focusedGraveId]);

  return (
    <Box className="map-workspace">
      <Stack aria-label="地図操作" className="map-toolbar" direction="row" spacing={1} useFlexGap>
        <ToggleButtonGroup
          disabled={busy}
          exclusive
          onChange={(_, value: MapMode | null) => {
            if (value !== null) setMode(value);
          }}
          size="small"
          value={mode}
        >
          <ToggleButton value="select">墓所編集</ToggleButton>
          <ToggleButton value="editArea">エリア編集</ToggleButton>
          <ToggleButton value="createGrave">墓所作成</ToggleButton>
          <ToggleButton value="createArea">エリア作成</ToggleButton>
          <ToggleButton disabled={snapshot.background === null} value="transformBackground">
            背景移動
          </ToggleButton>
        </ToggleButtonGroup>
        <Tooltip title={snapshot.background === null ? '背景を追加' : '背景を差し替え'}>
          <span>
            <IconButton
              aria-label={snapshot.background === null ? '背景を追加' : '背景を差し替え'}
              disabled={busy}
              onClick={onChooseBackground}
              size="small"
            >
              <ImageOutlined fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="背景を削除">
          <span>
            <IconButton
              aria-label="背景を削除"
              color="error"
              disabled={busy || snapshot.background === null}
              onClick={() => setRemoveBackgroundOpen(true)}
              size="small"
            >
              <DeleteOutlineOutlined fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="背景の縦横比を戻す">
          <span>
            <IconButton
              aria-label="背景の縦横比を戻す"
              disabled={
                busy ||
                snapshot.background === null ||
                snapshot.background.scaleX === snapshot.background.scaleY
              }
              onClick={() => {
                if (model.background !== undefined) {
                  onTransformBackground(resetBackgroundAspectRatio(model.background));
                }
              }}
              size="small"
            >
              <AspectRatio fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="元に戻す">
          <span>
            <IconButton
              aria-label="元に戻す"
              disabled={busy || !canUndo}
              onClick={() => onHistoryChange('undo')}
              size="small"
            >
              <Undo fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="やり直す">
          <span>
            <IconButton
              aria-label="やり直す"
              disabled={busy || !canRedo}
              onClick={() => onHistoryChange('redo')}
              size="small"
            >
              <Redo fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="拡大">
          <IconButton aria-label="拡大" onClick={() => adapter.current?.zoom(1.2)} size="small">
            <ZoomIn fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title="縮小">
          <IconButton aria-label="縮小" onClick={() => adapter.current?.zoom(1 / 1.2)} size="small">
            <ZoomOut fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title="全体表示">
          <IconButton aria-label="全体表示" onClick={() => adapter.current?.fit()} size="small">
            <FitScreen fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title="印刷・出力">
          <IconButton aria-label="印刷・出力" onClick={() => setOutputOpen(true)} size="small">
            <PrintOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <FormControlLabel
          control={
            <Switch checked={snap} onChange={(_, checked) => setSnap(checked)} size="small" />
          }
          label="スナップ"
        />
        <ToggleButtonGroup
          exclusive
          onChange={(_, value: MapCanvasProps['labelMode'] | null) => {
            if (value !== null) onLabelModeChange(value);
          }}
          size="small"
          value={labelMode}
        >
          <ToggleButton value="managementNumber">管理番号</ToggleButton>
          <ToggleButton value="name">墓所名</ToggleButton>
          <ToggleButton value="both">両方</ToggleButton>
          <ToggleButton value="hidden">非表示</ToggleButton>
        </ToggleButtonGroup>
        {mode === 'editArea' && selectedAreaId !== undefined ? (
          <TextField
            defaultValue={
              snapshot.areas.find(({ areaId }) => areaId === selectedAreaId)?.name ?? ''
            }
            disabled={busy}
            key={`${selectedAreaId}-${snapshot.revision}`}
            label="エリア名"
            onBlur={(event) => {
              const name = event.target.value;
              const current = snapshot.areas.find(({ areaId }) => areaId === selectedAreaId)?.name;
              if (name !== current) onAreaNameChange(selectedAreaId, name);
            }}
            size="small"
          />
        ) : null}
      </Stack>
      <Box
        aria-label="墓地地図キャンバス"
        className="map-canvas"
        ref={host}
        role="application"
        tabIndex={0}
        onKeyDown={(event) => {
          if (busy && ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) {
            event.preventDefault();
            return;
          }
          if (event.key === '+' || event.key === '=') {
            adapter.current?.zoom(1.2);
            event.preventDefault();
          } else if (event.key === '-') {
            adapter.current?.zoom(1 / 1.2);
            event.preventDefault();
          } else if (event.key === '0') {
            adapter.current?.fit();
            event.preventDefault();
          } else if (
            ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key) &&
            selectedIds.length > 0
          ) {
            const amount = event.shiftKey ? 10 : 1;
            const delta = {
              x: event.key === 'ArrowLeft' ? -amount : event.key === 'ArrowRight' ? amount : 0,
              y: event.key === 'ArrowUp' ? -amount : event.key === 'ArrowDown' ? amount : 0,
            };
            onNudgeGraves(selectedIds, delta);
            event.preventDefault();
          }
        }}
      />
      <MapOutputDialog
        capture={(range) => adapter.current?.exportImage(range)}
        onClose={() => setOutputOpen(false)}
        open={outputOpen}
      />
      <Dialog onClose={() => setRemoveBackgroundOpen(false)} open={removeBackgroundOpen}>
        <DialogTitle>背景を削除しますか</DialogTitle>
        <DialogContent>
          エリアと墓所の位置は変わりません。削除後も「元に戻す」で復元できます。
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRemoveBackgroundOpen(false)}>キャンセル</Button>
          <Button
            color="error"
            onClick={() => {
              setRemoveBackgroundOpen(false);
              onRemoveBackground();
            }}
            variant="contained"
          >
            削除
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

export default MapCanvas;
