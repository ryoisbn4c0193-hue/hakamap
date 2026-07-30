import {
  Alert,
  AppBar,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import {
  chooseTransferPath,
  closeProject,
  exportProject,
  getBackups,
  getProjectSnapshot,
  requestApplicationExit,
  restoreBackup,
  saveProject,
} from './api/hakamapClient';
import EditorView from './editor/EditorView';
import ProjectCatalogView from './projects/ProjectCatalogView';
import { useUiStore } from './state/uiStore';
import './App.css';

function App() {
  const queryClient = useQueryClient();
  const toggleLeftPanel = useUiStore((state) => state.toggleLeftPanel);
  const toggleRightPanel = useUiStore((state) => state.toggleRightPanel);
  const resetEditor = useUiStore((state) => state.resetEditor);
  const [exitRequested, setExitRequested] = useState(false);
  const [editorVisible, setEditorVisible] = useState(false);
  const [openProjectId, setOpenProjectId] = useState<string>();
  const [closeConfirmationOpen, setCloseConfirmationOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [appMessage, setAppMessage] = useState<string>();
  const [backupsOpen, setBackupsOpen] = useState(false);
  const backups = useQuery({
    enabled: backupsOpen && openProjectId !== undefined,
    queryFn: () => {
      if (openProjectId === undefined) throw new Error('project-not-open');
      return getBackups(openProjectId);
    },
    queryKey: ['projectBackups', openProjectId],
  });

  const exitApplication = async () => {
    setExitRequested(true);
    try {
      await requestApplicationExit();
    } catch {
      setExitRequested(false);
    }
  };

  const leaveEditor = async (action: 'save' | 'discard') => {
    if (openProjectId !== undefined) {
      await closeProject(openProjectId, action);
    }
    setCloseConfirmationOpen(false);
    setEditorVisible(false);
    setOpenProjectId(undefined);
    resetEditor();
    await queryClient.invalidateQueries({ queryKey: ['projectCatalog'] });
  };

  return (
    <Box className="app-shell">
      <AppBar color="primary" elevation={1} position="static">
        <Toolbar variant="dense">
          <Typography component="h1" sx={{ flexGrow: 1 }} variant="h1">
            Hakamap
          </Typography>
          <Stack direction="row" spacing={1}>
            {editorVisible ? (
              <>
                <Button
                  color="inherit"
                  disabled={saving || openProjectId === undefined}
                  onClick={() => {
                    if (openProjectId === undefined) {
                      return;
                    }
                    setSaving(true);
                    setAppMessage(undefined);
                    void saveProject(openProjectId)
                      .then(() =>
                        queryClient.invalidateQueries({
                          queryKey: ['projectSnapshot', openProjectId],
                        }),
                      )
                      .catch(() =>
                        setAppMessage('保存できませんでした。編集内容は保持されています。'),
                      )
                      .finally(() => setSaving(false));
                  }}
                  size="small"
                >
                  {saving ? '保存中' : '保存'}
                </Button>
                <Button color="inherit" onClick={toggleLeftPanel} size="small">
                  エリア
                </Button>
                <Button color="inherit" onClick={toggleRightPanel} size="small">
                  プロパティ
                </Button>
                <Button
                  color="inherit"
                  disabled={saving || openProjectId === undefined}
                  onClick={() => {
                    if (openProjectId === undefined) return;
                    setSaving(true);
                    void getProjectSnapshot(openProjectId)
                      .then(async (snapshot) => {
                        if (snapshot.dirty) {
                          await saveProject(openProjectId);
                        }
                        const current = await getProjectSnapshot(openProjectId);
                        const selection = await chooseTransferPath('exportDestination');
                        if (selection !== undefined) {
                          await exportProject(openProjectId, current.revision, selection);
                        }
                      })
                      .catch(() => setAppMessage('エクスポートを完了できませんでした。'))
                      .finally(() => setSaving(false));
                  }}
                  size="small"
                >
                  エクスポート
                </Button>
                <Button color="inherit" onClick={() => setBackupsOpen(true)} size="small">
                  バックアップ
                </Button>
                <Button color="inherit" onClick={() => setCloseConfirmationOpen(true)} size="small">
                  プロジェクト一覧
                </Button>
              </>
            ) : null}
            <Button
              color="inherit"
              disabled={exitRequested}
              onClick={() => {
                void exitApplication();
              }}
              size="small"
            >
              Hakamapを終了
            </Button>
          </Stack>
        </Toolbar>
      </AppBar>
      {appMessage === undefined ? null : (
        <Alert onClose={() => setAppMessage(undefined)} severity="error">
          {appMessage}
        </Alert>
      )}

      {editorVisible && openProjectId !== undefined ? (
        <EditorView projectId={openProjectId} />
      ) : (
        <ProjectCatalogView
          onOpened={(projectId) => {
            setOpenProjectId(projectId);
            setEditorVisible(true);
          }}
        />
      )}

      <Dialog onClose={() => setCloseConfirmationOpen(false)} open={closeConfirmationOpen}>
        <DialogTitle>プロジェクトを閉じますか？</DialogTitle>
        <DialogContent>
          未保存の変更がある場合は、保存するか破棄するかを選択してください。
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCloseConfirmationOpen(false)}>キャンセル</Button>
          <Button
            color="error"
            onClick={() => {
              void leaveEditor('discard');
            }}
          >
            変更を破棄
          </Button>
          <Button
            onClick={() => {
              void leaveEditor('save');
            }}
            variant="contained"
          >
            保存して閉じる
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog fullWidth maxWidth="sm" onClose={() => setBackupsOpen(false)} open={backupsOpen}>
        <DialogTitle>バックアップと復元</DialogTitle>
        <DialogContent>
          {backups.data?.items.length === 0 ? (
            <Typography>利用できるバックアップはありません。</Typography>
          ) : null}
          <Stack spacing={1}>
            {backups.data?.items.map((backup) => (
              <Box key={backup.backupId}>
                <Typography>
                  {backup.backupType === 'automatic' ? '自動バックアップ' : '復元前の退避'}・
                  {new Date(backup.createdAt).toLocaleString()}
                </Typography>
                <Button
                  disabled={!backup.restorable || saving}
                  onClick={() => {
                    if (openProjectId === undefined || backups.data === undefined) return;
                    setSaving(true);
                    void restoreBackup(
                      openProjectId,
                      backups.data.revision,
                      backup.backupId,
                      backup.backupVersion,
                    )
                      .then(async () => {
                        await queryClient.invalidateQueries({
                          queryKey: ['projectSnapshot', openProjectId],
                        });
                        setBackupsOpen(false);
                      })
                      .catch(() => setAppMessage('バックアップを復元できませんでした。'))
                      .finally(() => setSaving(false));
                  }}
                >
                  復元
                </Button>
              </Box>
            ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBackupsOpen(false)}>閉じる</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

export default App;
