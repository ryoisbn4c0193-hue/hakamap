import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  LinearProgress,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  Snackbar,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';

import {
  changeHistory,
  chooseAttachmentFiles,
  chooseBackgroundFile,
  confirmProjectCommand,
  executeProjectCommand,
  getGravePeople,
  getProjectHistory,
  getProjectSnapshot,
  HakamapApiError,
  searchGraves,
  type Grave,
  type GraveSearchPage,
  type Person,
  type PeoplePage,
} from '../api/hakamapClient';
import MapCanvas from '../map/MapCanvas';
import { useUiStore } from '../state/uiStore';

import type { MapRect } from '../map/mapGeometry';

type EditorViewProps = {
  projectId: string;
};

type GraveDraft = {
  managementNumber: string;
  name: string;
  notes: string;
};

type Notification = Readonly<{ message: string; severity: 'error' | 'info' | 'success' }>;

const errorMessages: Readonly<Record<string, string>> = {
  'area-color-in-use': '選択した色は別のエリアで使用されています。',
  'area-limit-exceeded': '登録できるエリア数の上限に達しています。',
  'area-name-duplicate': '同じ名前のエリアが既に存在します。別の名前を入力してください。',
  'area-overlap': 'エリア同士は重ねられません。位置またはサイズを調整してください。',
  'asset-count-exceeded': 'この墓所へ追加できる添付ファイル数の上限に達しています。',
  'asset-dimensions-exceeded': '画像の縦横サイズが上限を超えています。',
  'asset-format-unsupported': '対応していないファイル形式です。',
  'asset-size-exceeded': 'ファイル容量が上限を超えています。',
  'grave-business-key-duplicate':
    '同じエリア内で管理番号が重複しています。別の管理番号を入力してください。',
  'grave-overlap': '墓所同士は重ねられません。位置またはサイズを調整してください。',
  'invalid-area-name': 'エリア名を1～25文字で入力してください。',
  'invalid-grave-name': '墓所名は50文字以内で入力してください。',
  'invalid-grave-notes': '備考は1,000文字以内で入力してください。',
  'invalid-management-number': '管理番号は25文字以内で入力してください。',
  'invalid-map-rectangle': '作成範囲またはサイズが不正です。',
  'invalid-map-size': '幅と高さは0より大きい値にしてください。',
  'project-busy': 'プロジェクトは別の処理で使用中です。処理完了後に再試行してください。',
  'project-revision-conflict': '別の変更が反映されています。最新情報を読み込んでください。',
  'request-field-invalid': '入力値または操作内容が不正です。入力内容を確認してください。',
};

export function editingErrorMessage(error: unknown): string {
  if (error instanceof HakamapApiError) {
    if (error.code !== undefined) {
      return errorMessages[error.code] ?? `操作に失敗しました（${error.code}）。`;
    }
    return `サーバーが操作を受け付けませんでした（HTTP ${error.status}）。`;
  }
  return '操作中に予期しないエラーが発生しました。入力内容は保持されています。';
}

export function placementWarningMessage(code: string, count: number): string {
  const messages: Readonly<Record<string, string>> = {
    outside_area_bounds: `エリア範囲外の墓所が${count}件あります。`,
    unassigned: `所属エリアのない墓所が${count}件あります。`,
  };
  return messages[code] ?? `確認が必要な墓所が${count}件あります。`;
}

const emptyDraft: GraveDraft = { managementNumber: '', name: '', notes: '' };
const GRAVE_LIST_PAGE_SIZE = 200;

export function createAreaPayload(rectangle: MapRect, areaCount: number, clientRef: string) {
  return {
    clientRef,
    colorPreset: null,
    height: rectangle.height,
    name: `エリア ${areaCount + 1}`,
    visible: true,
    width: rectangle.width,
    x: rectangle.x,
    y: rectangle.y,
  };
}

function draftFrom(grave?: Grave): GraveDraft {
  return grave === undefined
    ? emptyDraft
    : {
        managementNumber: grave.managementNumber ?? '',
        name: grave.name ?? '',
        notes: grave.notes ?? '',
      };
}

function EditorView({ projectId }: EditorViewProps) {
  const queryClient = useQueryClient();
  const selectedGraveId = useUiStore((state) => state.selectedGraveId);
  const selectedMapIds = useUiStore((state) => state.selectedMapIds);
  const labelMode = useUiStore((state) => state.labelMode);
  const selectGrave = useUiStore((state) => state.selectGrave);
  const selectMapIds = useUiStore((state) => state.selectMapIds);
  const setLabelMode = useUiStore((state) => state.setLabelMode);
  const tab = useUiStore((state) => state.propertyTab);
  const setTab = useUiStore((state) => state.setPropertyTab);
  const leftCollapsed = useUiStore((state) => state.leftPanelCollapsed);
  const rightCollapsed = useUiStore((state) => state.rightPanelCollapsed);
  const snapshot = useQuery({
    queryFn: () => getProjectSnapshot(projectId),
    queryKey: ['projectSnapshot', projectId],
  });
  const [searchInput, setSearchInput] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [graveListLimit, setGraveListLimit] = useState(GRAVE_LIST_PAGE_SIZE);
  const [focusedGraveId, setFocusedGraveId] = useState<string>();
  const searchResults = useInfiniteQuery<
    GraveSearchPage,
    Error,
    InfiniteData<GraveSearchPage>,
    readonly unknown[],
    string | undefined
  >({
    enabled: searchKeyword.length > 0,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => searchGraves(projectId, searchKeyword, pageParam),
    queryKey: ['graveSearch', projectId, searchKeyword],
  });
  const firstSearchGraveId = searchResults.data?.pages[0]?.items[0]?.graveId;
  const selectedGrave = snapshot.data?.graves.find((grave) => grave.graveId === selectedGraveId);
  const [draft, setDraft] = useState<GraveDraft>(emptyDraft);
  const [draftSourceId, setDraftSourceId] = useState<string>();
  const [pendingSelection, setPendingSelection] = useState<string>();
  const [notification, setNotification] = useState<Notification>();
  const [conflict, setConflict] = useState(false);
  const [pendingConfirmation, setPendingConfirmation] = useState<{
    confirmationToken: string;
    revision: number;
    warnings: readonly { code: string; count: number }[];
  }>();

  useEffect(() => {
    if (notification === undefined) return undefined;
    const timer = window.setTimeout(() => setNotification(undefined), 5_000);
    return () => window.clearTimeout(timer);
  }, [notification]);

  if (selectedGrave?.graveId !== draftSourceId) {
    setDraft(draftFrom(selectedGrave));
    setDraftSourceId(selectedGrave?.graveId);
  }

  const draftDirty = useMemo(() => {
    const saved = draftFrom(selectedGrave);
    return (
      draft.managementNumber !== saved.managementNumber ||
      draft.name !== saved.name ||
      draft.notes !== saved.notes
    );
  }, [draft, selectedGrave]);

  const refreshProject = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['projectSnapshot', projectId] }),
      queryClient.invalidateQueries({ queryKey: ['projectHistory', projectId] }),
      queryClient.invalidateQueries({ queryKey: ['gravePeople', projectId] }),
    ]);
  };

  const command = useMutation({
    mutationFn: async ({ commandType, payload }: { commandType: string; payload: unknown }) => {
      if (snapshot.data === undefined) {
        throw new Error('snapshot-unavailable');
      }
      return executeProjectCommand(projectId, snapshot.data.revision, commandType, payload);
    },
    onError: (error) => {
      void queryClient.invalidateQueries({ queryKey: ['projectSnapshot', projectId] });
      if (error instanceof HakamapApiError && error.status === 409) {
        setConflict(true);
        setNotification({ message: editingErrorMessage(error), severity: 'error' });
      } else {
        setNotification({ message: editingErrorMessage(error), severity: 'error' });
      }
    },
    onSuccess: async (response) => {
      if (response.status === 'confirmationRequired') {
        setPendingConfirmation(response);
        return;
      }
      setNotification({ message: '変更を適用しました。', severity: 'success' });
      await refreshProject();
    },
  });
  const confirmation = useMutation({
    mutationFn: async () => {
      if (pendingConfirmation === undefined) throw new Error('confirmation-unavailable');
      return confirmProjectCommand(
        projectId,
        pendingConfirmation.confirmationToken,
        pendingConfirmation.revision,
      );
    },
    onError: (error) => {
      setPendingConfirmation(undefined);
      setNotification({
        message: editingErrorMessage(error),
        severity: 'error',
      });
    },
    onSuccess: async () => {
      setPendingConfirmation(undefined);
      setNotification({ message: '変更を適用しました。', severity: 'success' });
      await refreshProject();
    },
  });
  const historyChange = useMutation({
    mutationFn: async (action: 'undo' | 'redo') => {
      if (snapshot.data === undefined) throw new Error('snapshot-unavailable');
      await changeHistory(projectId, action, snapshot.data.revision);
    },
    onError: (error) => setNotification({ message: editingErrorMessage(error), severity: 'error' }),
    onSuccess: refreshProject,
  });

  const requestSelection = (graveId?: string) => {
    if (graveId === selectedGraveId) {
      selectGrave(graveId);
      return;
    }
    if (draftDirty) {
      setPendingSelection(graveId ?? '');
      return;
    }
    selectGrave(graveId);
  };

  const mapCommand = (commandType: string, payload: unknown) => {
    command.mutate({ commandType, payload });
  };

  const chooseBackground = async () => {
    try {
      const fileSelectionId = await chooseBackgroundFile();
      if (fileSelectionId === undefined) return;
      const background = snapshot.data?.background;
      command.mutate({
        commandType: 'setBackground',
        payload: {
          fileSelectionId,
          rotation: background?.rotation ?? 0,
          scaleX: background?.scaleX ?? 1,
          scaleY: background?.scaleY ?? 1,
          x: background?.x ?? 0,
          y: background?.y ?? 0,
        },
      });
    } catch (error) {
      setNotification({ message: editingErrorMessage(error), severity: 'error' });
    }
  };

  const createPayload = (rectangle: MapRect) => ({
    clientRef: crypto.randomUUID(),
    height: rectangle.height,
    width: rectangle.width,
    x: rectangle.x,
    y: rectangle.y,
  });

  if (snapshot.isPending) {
    return <CircularProgress aria-label="編集データを読み込み中" />;
  }
  if (snapshot.isError || snapshot.data === undefined) {
    return <Alert severity="error">プロジェクトの編集データを読み込めませんでした。</Alert>;
  }

  const states = new Map(snapshot.data.graveStates.map((state) => [state.graveId, state]));
  const unassigned = snapshot.data.graveStates.filter((state) => state.areaId === null).length;
  const unnumbered = snapshot.data.graves.filter((grave) => grave.managementNumber === null).length;
  const incomplete = snapshot.data.graveStates.filter(
    (state) => state.completionStatus !== 'complete',
  ).length;
  const editingBusy = command.isPending || confirmation.isPending || historyChange.isPending;

  return (
    <>
      {editingBusy ? <LinearProgress aria-label="操作を処理中" /> : null}
      {notification === undefined ? null : (
        <Snackbar
          anchorOrigin={{ horizontal: 'center', vertical: 'top' }}
          autoHideDuration={5_000}
          onClose={(_, reason) => {
            if (reason !== 'clickaway') setNotification(undefined);
          }}
          open
        >
          <Alert
            onClose={() => setNotification(undefined)}
            severity={notification.severity}
            variant="filled"
          >
            {notification.message}
          </Alert>
        </Snackbar>
      )}
      <Box
        className="editor-layout"
        component="main"
        sx={{
          gridTemplateColumns: `${leftCollapsed ? 0 : 250}px minmax(320px, 1fr) ${
            rightCollapsed ? 0 : 360
          }px`,
        }}
      >
        <Paper
          aria-hidden={leftCollapsed}
          className="side-panel"
          component="aside"
          elevation={0}
          square
        >
          <Typography component="h2" variant="h2">
            エリアと管理状態
          </Typography>
          <Stack
            component="form"
            onSubmit={(event) => {
              event.preventDefault();
              setFocusedGraveId(undefined);
              setSearchKeyword(searchInput);
            }}
            spacing={1}
          >
            <TextField
              label="墓所を検索"
              onChange={(event) => setSearchInput(event.target.value)}
              size="small"
              slotProps={{ htmlInput: { maxLength: 200 } }}
              value={searchInput}
            />
            <Stack direction="row" spacing={1}>
              <Button disabled={searchInput.trim().length === 0} type="submit" variant="contained">
                検索
              </Button>
              <Button
                onClick={() => {
                  setSearchInput('');
                  setSearchKeyword('');
                  setFocusedGraveId(undefined);
                }}
              >
                クリア
              </Button>
            </Stack>
          </Stack>
          {searchKeyword.length === 0 ? null : (
            <Box>
              <Typography component="h3" variant="subtitle2">
                検索結果 {searchResults.data?.pages[0]?.totalCount ?? 0}件
              </Typography>
              <List aria-label="検索結果" dense sx={{ maxHeight: 260, overflow: 'auto' }}>
                {searchResults.data?.pages.flatMap((page) =>
                  page.items.map((result) => (
                    <ListItemButton
                      key={result.graveId}
                      onClick={() => {
                        requestSelection(result.graveId);
                        setFocusedGraveId(result.graveId);
                      }}
                      selected={result.graveId === selectedGraveId}
                    >
                      <ListItemText
                        primary={`${result.managementNumber ?? '未採番'} ${result.graveName ?? ''}`}
                        secondary={result.areaName ?? '未割当'}
                      />
                    </ListItemButton>
                  )),
                )}
              </List>
              {searchResults.hasNextPage ? (
                <Button
                  disabled={searchResults.isFetchingNextPage}
                  onClick={() => void searchResults.fetchNextPage()}
                  size="small"
                >
                  続きを表示
                </Button>
              ) : null}
            </Box>
          )}
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            <Chip label={`すべて ${snapshot.data.graves.length}`} />
            <Chip label={`未割当 ${unassigned}`} />
            <Chip label={`未採番 ${unnumbered}`} />
            <Chip label={`情報未完成 ${incomplete}`} />
          </Box>
          <Divider />
          <List aria-label="墓所一覧" dense>
            {snapshot.data.graves.slice(0, graveListLimit).map((grave) => {
              const state = states.get(grave.graveId);
              return (
                <ListItemButton
                  key={grave.graveId}
                  onClick={() => requestSelection(grave.graveId)}
                  selected={grave.graveId === selectedGraveId}
                >
                  <ListItemText
                    primary={`${grave.managementNumber ?? '未採番'} ${grave.name ?? ''}`}
                    secondary={state?.completionStatus === 'complete' ? undefined : '情報未完成'}
                  />
                </ListItemButton>
              );
            })}
          </List>
          {graveListLimit < snapshot.data.graves.length ? (
            <Button
              onClick={() => setGraveListLimit((current) => current + GRAVE_LIST_PAGE_SIZE)}
              size="small"
            >
              続きを表示（{Math.min(graveListLimit, snapshot.data.graves.length)}／
              {snapshot.data.graves.length}）
            </Button>
          ) : null}
        </Paper>

        <MapCanvas
          busy={editingBusy}
          canRedo={snapshot.data.historySummary.canRedo}
          canUndo={snapshot.data.historySummary.canUndo}
          focusedGraveId={focusedGraveId ?? firstSearchGraveId}
          labelMode={labelMode}
          onAreaNameChange={(areaId, name) => {
            const area = snapshot.data.areas.find((candidate) => candidate.areaId === areaId);
            if (area !== undefined) {
              mapCommand('updateArea', {
                areaId,
                colorPreset: area.colorPreset,
                height: area.height,
                name,
                rotation: area.rotation,
                visible: area.visible,
                width: area.width,
                x: area.x,
                y: area.y,
              });
            }
          }}
          onChooseBackground={() => void chooseBackground()}
          onCreateArea={(rectangle) =>
            mapCommand(
              'createArea',
              createAreaPayload(rectangle, snapshot.data.areas.length, crypto.randomUUID()),
            )
          }
          onCreateGrave={(rectangle) => mapCommand('createGrave', createPayload(rectangle))}
          onMoveGraves={(graveIds, delta) =>
            mapCommand('moveGraves', {
              deltaX: delta.x,
              deltaY: delta.y,
              graveIds,
            })
          }
          onHistoryChange={(action) => historyChange.mutate(action)}
          onRemoveBackground={() => mapCommand('removeBackground', {})}
          onNudgeGraves={(graveIds, delta) =>
            mapCommand('moveGraves', {
              deltaX: delta.x,
              deltaY: delta.y,
              graveIds,
            })
          }
          onResizeGrave={(rectangle) =>
            mapCommand('resizeGrave', {
              graveId: rectangle.id,
              height: rectangle.height,
              rotation: rectangle.rotation ?? 0,
              width: rectangle.width,
              x: rectangle.x,
              y: rectangle.y,
            })
          }
          onUpdateArea={(rectangle) => {
            const area = snapshot.data.areas.find(({ areaId }) => areaId === rectangle.id);
            if (area !== undefined) {
              mapCommand('updateArea', {
                areaId: area.areaId,
                colorPreset: area.colorPreset,
                height: rectangle.height,
                name: area.name,
                rotation: rectangle.rotation ?? area.rotation,
                visible: area.visible,
                width: rectangle.width,
                x: rectangle.x,
                y: rectangle.y,
              });
            }
          }}
          onTransformBackground={(background) => {
            mapCommand('transformBackground', {
              rotation: background.rotation,
              scaleX: background.scaleX,
              scaleY: background.scaleY,
              x: background.x,
              y: background.y,
            });
          }}
          onSelectionChange={(graveIds) => {
            if (graveIds.length <= 1) requestSelection(graveIds[0]);
            else if (!draftDirty) selectMapIds(graveIds);
          }}
          onLabelModeChange={setLabelMode}
          searchHighlightedGraveId={
            searchKeyword.length === 0 ? undefined : (focusedGraveId ?? firstSearchGraveId)
          }
          selectedIds={selectedMapIds}
          snapshot={snapshot.data}
        />

        <Paper
          aria-hidden={rightCollapsed}
          className="side-panel property-panel"
          component="aside"
          elevation={0}
          square
        >
          <Typography component="h2" variant="h2">
            プロパティ
          </Typography>
          {selectedMapIds.length > 1 ? (
            <Stack spacing={2}>
              <Typography>{selectedMapIds.length}件の墓所を選択中</Typography>
              <Button
                color="error"
                disabled={editingBusy}
                onClick={() => mapCommand('deleteGraves', { graveIds: selectedMapIds })}
                variant="outlined"
              >
                一括削除
              </Button>
            </Stack>
          ) : selectedGrave === undefined ? (
            <Typography color="text.secondary">墓所を選択してください。</Typography>
          ) : (
            <>
              <Tabs
                aria-label="墓所プロパティ"
                onChange={(_, value: typeof tab) => setTab(value)}
                scrollButtons="auto"
                value={tab}
                variant="scrollable"
              >
                <Tab label="基本情報" value="basic" />
                <Tab label="人物" value="people" />
                <Tab label="添付" value="assets" />
                <Tab label="履歴" value="history" />
              </Tabs>
              {tab === 'basic' ? (
                <BasicTab
                  busy={editingBusy}
                  draft={draft}
                  onChange={setDraft}
                  onReset={() => setDraft(draftFrom(selectedGrave))}
                  onSave={() =>
                    command.mutate({
                      commandType: 'updateGraveInfo',
                      payload: { graveId: selectedGrave.graveId, ...draft },
                    })
                  }
                />
              ) : null}
              {tab === 'people' ? (
                <PeopleTab
                  busy={editingBusy}
                  graveId={selectedGrave.graveId}
                  onCommand={(commandType, payload) => command.mutate({ commandType, payload })}
                  projectId={projectId}
                />
              ) : null}
              {tab === 'assets' ? (
                <AssetsTab
                  assets={snapshot.data.assets
                    .filter((asset) => asset.graveId === selectedGrave.graveId)
                    .sort((left, right) => (left.displayOrder ?? 0) - (right.displayOrder ?? 0))}
                  busy={editingBusy}
                  graveId={selectedGrave.graveId}
                  projectId={projectId}
                  onCommand={(commandType, payload) => command.mutate({ commandType, payload })}
                />
              ) : null}
              {tab === 'history' ? <HistoryTab projectId={projectId} /> : null}
            </>
          )}
        </Paper>
      </Box>

      <Dialog open={pendingSelection !== undefined}>
        <DialogTitle>入力中の変更を破棄しますか</DialogTitle>
        <DialogContent>墓所の選択を変更すると、まだ適用していない入力を破棄します。</DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingSelection(undefined)}>入力を続ける</Button>
          <Button
            color="error"
            onClick={() => {
              selectGrave(pendingSelection === '' ? undefined : pendingSelection);
              setPendingSelection(undefined);
            }}
          >
            破棄して選択
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={conflict}>
        <DialogTitle>別の更新が反映されています</DialogTitle>
        <DialogContent>
          入力内容は保持されています。最新情報を読み込み、内容を確認して再度適用してください。
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConflict(false)}>入力を確認する</Button>
          <Button
            onClick={() => {
              void refreshProject().then(() => setConflict(false));
            }}
            variant="contained"
          >
            最新情報を読み込む
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={pendingConfirmation !== undefined}>
        <DialogTitle>配置に関する警告があります</DialogTitle>
        <DialogContent>
          {pendingConfirmation?.warnings.map((warning) => (
            <Typography key={warning.code}>
              {placementWarningMessage(warning.code, warning.count)}
            </Typography>
          ))}
          警告を確認したうえで配置できます。
        </DialogContent>
        <DialogActions>
          <Button
            disabled={confirmation.isPending}
            onClick={() => setPendingConfirmation(undefined)}
          >
            キャンセル
          </Button>
          <Button
            disabled={confirmation.isPending}
            onClick={() => confirmation.mutate()}
            variant="contained"
          >
            このまま配置
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

function BasicTab({
  busy,
  draft,
  onChange,
  onReset,
  onSave,
}: {
  busy: boolean;
  draft: GraveDraft;
  onChange: (draft: GraveDraft) => void;
  onReset: () => void;
  onSave: () => void;
}) {
  return (
    <Stack component="form" onSubmit={(event) => event.preventDefault()} spacing={2}>
      <TextField
        label="管理番号"
        onChange={(event) => onChange({ ...draft, managementNumber: event.target.value })}
        value={draft.managementNumber}
      />
      <TextField
        label="墓所名"
        onChange={(event) => onChange({ ...draft, name: event.target.value })}
        value={draft.name}
      />
      <TextField
        label="備考"
        minRows={4}
        multiline
        onChange={(event) => onChange({ ...draft, notes: event.target.value })}
        value={draft.notes}
      />
      <Stack direction="row" spacing={1}>
        <Button disabled={busy} onClick={onReset}>
          元に戻す
        </Button>
        <Button disabled={busy} onClick={onSave} variant="contained">
          適用
        </Button>
      </Stack>
    </Stack>
  );
}

function PeopleTab({
  busy,
  graveId,
  projectId,
  onCommand,
}: {
  busy: boolean;
  graveId: string;
  projectId: string;
  onCommand: (type: string, payload: unknown) => void;
}) {
  const queryClient = useQueryClient();
  const people = useInfiniteQuery<
    PeoplePage,
    Error,
    InfiniteData<PeoplePage>,
    readonly unknown[],
    string | undefined
  >({
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => getGravePeople(projectId, graveId, pageParam),
    queryKey: ['gravePeople', projectId, graveId],
  });
  useEffect(() => {
    const cached = queryClient
      .getQueryCache()
      .findAll({ queryKey: ['gravePeople', projectId] })
      .filter((query) => query.queryKey[2] !== graveId)
      .sort((left, right) => right.state.dataUpdatedAt - left.state.dataUpdatedAt);
    cached.slice(4).forEach((query) => queryClient.removeQueries({ queryKey: query.queryKey }));
  }, [graveId, projectId, queryClient]);
  const [editing, setEditing] = useState<Person | 'new'>();
  const [name, setName] = useState('');
  const [posthumousName, setPosthumousName] = useState('');
  const items = people.data?.pages.flatMap((page) => page.items) ?? [];
  const openDialog = (person: Person | 'new') => {
    setEditing(person);
    setName(person === 'new' ? '' : (person.name ?? ''));
    setPosthumousName(person === 'new' ? '' : (person.posthumousName ?? ''));
  };
  return (
    <Stack spacing={2}>
      <Button disabled={busy} onClick={() => openDialog('new')} variant="outlined">
        人物を追加
      </Button>
      <VirtualPeopleList
        busy={busy}
        hasNextPage={people.hasNextPage}
        items={items}
        loadNext={() => void people.fetchNextPage()}
        onDelete={(personId) => onCommand('deletePerson', { personId })}
        onEdit={openDialog}
      />
      <Dialog onClose={() => setEditing(undefined)} open={editing !== undefined}>
        <DialogTitle>{editing === 'new' ? '人物を追加' : '人物を編集'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="氏名"
              onChange={(event) => setName(event.target.value)}
              value={name}
            />
            <TextField
              label="戒名"
              onChange={(event) => setPosthumousName(event.target.value)}
              value={posthumousName}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(undefined)}>キャンセル</Button>
          <Button
            disabled={name.trim().length === 0 && posthumousName.trim().length === 0}
            onClick={() => {
              onCommand(
                editing === 'new' ? 'createPerson' : 'updatePerson',
                editing === 'new'
                  ? {
                      clientRef: crypto.randomUUID(),
                      graveId,
                      name,
                      posthumousName,
                    }
                  : { personId: editing?.personId, name, posthumousName },
              );
              setEditing(undefined);
              setName('');
              setPosthumousName('');
            }}
            variant="contained"
          >
            {editing === 'new' ? '追加' : '更新'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function VirtualPeopleList({
  busy,
  hasNextPage,
  items,
  loadNext,
  onDelete,
  onEdit,
}: {
  busy: boolean;
  hasNextPage: boolean;
  items: Person[];
  loadNext: () => void;
  onDelete: (personId: string) => void;
  onEdit: (person: Person) => void;
}) {
  const rowHeight = 112;
  const viewportHeight = 360;
  const [scrollTop, setScrollTop] = useState(0);
  const start = Math.max(0, Math.floor(scrollTop / rowHeight) - 2);
  const end = Math.min(items.length, Math.ceil((scrollTop + viewportHeight) / rowHeight) + 2);
  return (
    <Box
      aria-label="人物一覧"
      onScroll={(event) => {
        const target = event.currentTarget;
        setScrollTop(target.scrollTop);
        if (hasNextPage && target.scrollTop + target.clientHeight >= target.scrollHeight - 80) {
          loadNext();
        }
      }}
      sx={{ height: viewportHeight, overflowY: 'auto', position: 'relative' }}
    >
      <Box sx={{ height: items.length * rowHeight, position: 'relative' }}>
        {items.slice(start, end).map((person, offset) => (
          <Paper
            key={person.personId}
            sx={{
              height: rowHeight - 8,
              left: 0,
              p: 1,
              position: 'absolute',
              right: 0,
              top: (start + offset) * rowHeight,
            }}
            variant="outlined"
          >
            <Typography>{person.name ?? '氏名未入力'}</Typography>
            <Typography color="text.secondary">{person.posthumousName ?? '戒名未入力'}</Typography>
            <Button disabled={busy} onClick={() => onEdit(person)} size="small">
              編集
            </Button>
            <Button
              color="error"
              disabled={busy}
              onClick={() => onDelete(person.personId)}
              size="small"
            >
              削除
            </Button>
          </Paper>
        ))}
      </Box>
    </Box>
  );
}

function AssetsTab({
  assets,
  busy,
  graveId,
  projectId,
  onCommand,
}: {
  assets: Array<{
    assetId: string;
    displayName: string | null;
    mediaType: string;
    sizeBytes: number;
    displayOrder: number | null;
  }>;
  busy: boolean;
  graveId: string;
  projectId: string;
  onCommand: (type: string, payload: unknown) => void;
}) {
  const [preview, setPreview] = useState<(typeof assets)[number]>();
  const move = (index: number, direction: -1 | 1) => {
    const reordered = [...assets];
    const target = index + direction;
    if (target < 0 || target >= reordered.length) return;
    [reordered[index], reordered[target]] = [reordered[target]!, reordered[index]!];
    onCommand('reorderAttachments', {
      graveId,
      orderedAssetIds: reordered.map(({ assetId }) => assetId),
    });
  };
  return (
    <Stack spacing={2}>
      <Button
        disabled={busy}
        onClick={() => {
          void chooseAttachmentFiles().then((fileSelectionIds) => {
            if (fileSelectionIds.length > 0) {
              onCommand('addAttachments', { fileSelectionIds, graveId });
            }
          });
        }}
        variant="outlined"
      >
        添付を追加
      </Button>
      {assets.map((asset, index) => (
        <Paper key={asset.assetId} sx={{ p: 1 }} variant="outlined">
          <Typography>{asset.displayName ?? '添付ファイル'}</Typography>
          <Typography color="text.secondary">
            {asset.mediaType}・{Math.ceil(asset.sizeBytes / 1024)}KB
          </Typography>
          <Button onClick={() => setPreview(asset)} size="small">
            プレビュー
          </Button>
          <Button disabled={busy || index === 0} onClick={() => move(index, -1)} size="small">
            上へ
          </Button>
          <Button
            disabled={busy || index === assets.length - 1}
            onClick={() => move(index, 1)}
            size="small"
          >
            下へ
          </Button>
          <Button
            color="error"
            disabled={busy}
            onClick={() => onCommand('deleteAttachment', { assetId: asset.assetId })}
            size="small"
          >
            削除
          </Button>
        </Paper>
      ))}
      <Dialog
        fullWidth
        maxWidth="md"
        onClose={() => setPreview(undefined)}
        open={preview !== undefined}
      >
        <DialogTitle>{preview?.displayName ?? '添付ファイル'}</DialogTitle>
        <DialogContent>
          {preview === undefined ? null : (
            <Box
              alt={preview.displayName ?? '添付ファイルのプレビュー'}
              component="img"
              src={`/api/v1/projects/${projectId}/assets/${preview.assetId}/content`}
              sx={{ display: 'block', maxHeight: '70vh', maxWidth: '100%', mx: 'auto' }}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPreview(undefined)}>閉じる</Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function HistoryTab({ projectId }: { projectId: string }) {
  const history = useQuery({
    queryFn: () => getProjectHistory(projectId),
    queryKey: ['projectHistory', projectId],
  });
  return (
    <Stack spacing={2}>
      {history.data?.items.map((item) => (
        <Box key={item.commandId}>
          <Typography>{item.commandType}</Typography>
          <Typography color="text.secondary" variant="caption">
            {new Date(item.commandTimestamp).toLocaleString()}・
            {item.applied ? '適用中' : 'Undo済み'}
            {item.savedMarker ? '・ここまで保存済み' : ''}
          </Typography>
        </Box>
      ))}
    </Stack>
  );
}

export default EditorView;
