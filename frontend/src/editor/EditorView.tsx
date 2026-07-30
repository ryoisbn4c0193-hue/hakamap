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
  executeProjectCommand,
  getGravePeople,
  getProjectHistory,
  getProjectSnapshot,
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

const emptyDraft: GraveDraft = { managementNumber: '', name: '', notes: '' };

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
  const [message, setMessage] = useState<string>();
  const [conflict, setConflict] = useState(false);

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
      if (error instanceof Error && error.message === 'api-request-failed-409') {
        setConflict(true);
      } else {
        setMessage('操作を完了できませんでした。入力内容を保持しています。');
      }
    },
    onSuccess: async () => {
      setMessage('変更を適用しました。');
      await refreshProject();
    },
  });

  const requestSelection = (graveId?: string) => {
    if (graveId === selectedGraveId) {
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

  return (
    <>
      {command.isPending ? <LinearProgress aria-label="操作を処理中" /> : null}
      {message === undefined ? null : (
        <Alert onClose={() => setMessage(undefined)} severity="info">
          {message}
        </Alert>
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
            {snapshot.data.graves.map((grave) => {
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
        </Paper>

        <MapCanvas
          busy={command.isPending}
          focusedGraveId={focusedGraveId ?? firstSearchGraveId}
          labelMode={labelMode}
          onBackgroundFieldChange={(field, value) => {
            const background = snapshot.data.background;
            if (background !== null && background[field] !== value) {
              mapCommand('transformBackground', {
                rotation: background.rotation,
                scaleX: background.scaleX,
                scaleY: background.scaleY,
                x: background.x,
                y: background.y,
                [field]: value,
              });
            }
          }}
          onCreateArea={(rectangle) =>
            mapCommand('createArea', {
              ...createPayload(rectangle),
              colorPreset: 'blue',
              name: `エリア ${snapshot.data.areas.length + 1}`,
              visible: true,
            })
          }
          onCreateGrave={(rectangle) => mapCommand('createGrave', createPayload(rectangle))}
          onMoveGraves={(graveIds, delta) =>
            mapCommand('moveGraves', {
              deltaX: delta.x,
              deltaY: delta.y,
              graveIds,
            })
          }
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
                visible: area.visible,
                width: rectangle.width,
                x: rectangle.x,
                y: rectangle.y,
              });
            }
          }}
          onTransformBackground={(x, y) => {
            const background = snapshot.data.background;
            if (background !== null) {
              mapCommand('transformBackground', {
                rotation: background.rotation,
                scaleX: background.scaleX,
                scaleY: background.scaleY,
                x,
                y,
              });
            }
          }}
          onSelectionChange={(graveIds) => {
            if (graveIds.length <= 1) requestSelection(graveIds[0]);
            else if (!draftDirty) selectMapIds(graveIds);
          }}
          onLabelModeChange={setLabelMode}
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
                disabled={command.isPending}
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
                  busy={command.isPending}
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
                  busy={command.isPending}
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
                  busy={command.isPending}
                  graveId={selectedGrave.graveId}
                  projectId={projectId}
                  onCommand={(commandType, payload) => command.mutate({ commandType, payload })}
                />
              ) : null}
              {tab === 'history' ? (
                <HistoryTab
                  busy={command.isPending}
                  onChange={async (action) => {
                    await changeHistory(projectId, action, snapshot.data.revision);
                    await refreshProject();
                  }}
                  projectId={projectId}
                />
              ) : null}
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

function HistoryTab({
  busy,
  onChange,
  projectId,
}: {
  busy: boolean;
  onChange: (action: 'undo' | 'redo') => Promise<void>;
  projectId: string;
}) {
  const history = useQuery({
    queryFn: () => getProjectHistory(projectId),
    queryKey: ['projectHistory', projectId],
  });
  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1}>
        <Button
          disabled={busy || !history.data?.historySummary.canUndo}
          onClick={() => void onChange('undo')}
        >
          元に戻す
        </Button>
        <Button
          disabled={busy || !history.data?.historySummary.canRedo}
          onClick={() => void onChange('redo')}
        >
          やり直す
        </Button>
      </Stack>
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
