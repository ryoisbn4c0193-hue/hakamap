import {
  Box,
  Button,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';

import { getBackgroundTileManifest, type ProjectSnapshot } from '../api/hakamapClient';

import { graveLabel, type GraveLabelMode, type MapPoint, type MapRect } from './mapGeometry';
import MapOutputDialog from './MapOutputDialog';
import { PixiMapAdapter, type MapMode, type MapRenderModel } from './PixiMapAdapter';

type MapCanvasProps = {
  busy: boolean;
  labelMode: GraveLabelMode;
  onCreateArea: (rectangle: MapRect) => void;
  onBackgroundFieldChange: (
    field: 'x' | 'y' | 'rotation' | 'scaleX' | 'scaleY',
    value: number,
  ) => void;
  onCreateGrave: (rectangle: MapRect) => void;
  onMoveGraves: (graveIds: readonly string[], delta: MapPoint) => void;
  onNudgeGraves: (graveIds: readonly string[], delta: MapPoint) => void;
  onResizeGrave: (rectangle: MapRect) => void;
  onUpdateArea: (rectangle: MapRect) => void;
  onTransformBackground: (x: number, y: number) => void;
  onSelectionChange: (graveIds: readonly string[]) => void;
  onLabelModeChange: (mode: MapCanvasProps['labelMode']) => void;
  selectedIds: readonly string[];
  focusedGraveId?: string;
  snapshot: ProjectSnapshot;
};

function MapCanvas({
  busy,
  labelMode,
  onBackgroundFieldChange,
  onCreateArea,
  onCreateGrave,
  onMoveGraves,
  onNudgeGraves,
  onResizeGrave,
  onUpdateArea,
  onTransformBackground,
  onSelectionChange,
  onLabelModeChange,
  selectedIds,
  focusedGraveId,
  snapshot,
}: MapCanvasProps) {
  const host = useRef<HTMLDivElement>(null);
  const adapter = useRef<PixiMapAdapter | undefined>(undefined);
  const callbacks = useRef({
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
      selectedIds,
    }),
    [
      backgroundManifest.data,
      labelMode,
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
    void next.mount(element, {
      onCreateArea: (rectangle) => callbacks.current.onCreateArea(rectangle),
      onCreateGrave: (rectangle) => callbacks.current.onCreateGrave(rectangle),
      onMoveGraves: (ids, delta) => callbacks.current.onMoveGraves(ids, delta),
      onResizeGrave: (rectangle) => callbacks.current.onResizeGrave(rectangle),
      onUpdateArea: (rectangle) => callbacks.current.onUpdateArea(rectangle),
      onTransformBackground: (x, y) => callbacks.current.onTransformBackground(x, y),
      onSelectionChange: (ids) => callbacks.current.onSelectionChange(ids),
    });
    return () => {
      next.destroy();
      adapter.current = undefined;
    };
  }, []);

  useEffect(() => adapter.current?.update(model), [model]);
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
          <ToggleButton value="select">選択</ToggleButton>
          <ToggleButton value="editArea">エリア編集</ToggleButton>
          <ToggleButton value="createGrave">墓所作成</ToggleButton>
          <ToggleButton value="createArea">エリア作成</ToggleButton>
          <ToggleButton disabled={snapshot.background === null} value="transformBackground">
            背景移動
          </ToggleButton>
        </ToggleButtonGroup>
        <Button onClick={() => adapter.current?.zoom(1.2)} size="small">
          拡大
        </Button>
        <Button onClick={() => adapter.current?.zoom(1 / 1.2)} size="small">
          縮小
        </Button>
        <Button onClick={() => adapter.current?.fit()} size="small">
          全体表示
        </Button>
        <Button onClick={() => setOutputOpen(true)} size="small">
          印刷・出力
        </Button>
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
        {snapshot.background === null
          ? null
          : (['x', 'y', 'rotation', 'scaleX', 'scaleY'] as const).map((field) => (
              <TextField
                defaultValue={snapshot.background?.[field]}
                disabled={busy}
                slotProps={{ htmlInput: { step: field.startsWith('scale') ? 0.1 : 1 } }}
                key={`${snapshot.background?.assetId}-${field}-${snapshot.revision}`}
                label={
                  {
                    rotation: '回転',
                    scaleX: 'X倍率',
                    scaleY: 'Y倍率',
                    x: '背景X',
                    y: '背景Y',
                  }[field]
                }
                onBlur={(event) => {
                  const value = Number(event.target.value);
                  if (Number.isFinite(value)) onBackgroundFieldChange(field, value);
                }}
                size="small"
                sx={{ width: 76 }}
                type="number"
              />
            ))}
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
    </Box>
  );
}

export default MapCanvas;
