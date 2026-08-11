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
import { useEffect, useState } from 'react';

import { subscribeFileSelectionActivity } from './api/fileSelectionActivity';
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
  const [helpOpen, setHelpOpen] = useState(false);
  const [fileSelectionActive, setFileSelectionActive] = useState(false);
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
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve());
      });
      await requestApplicationExit();
      window.close();
    } catch {
      setExitRequested(false);
    }
  };

  useEffect(() => subscribeFileSelectionActivity(setFileSelectionActive), []);

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

  if (exitRequested) {
    return (
      <Box
        component="main"
        sx={{ alignItems: 'center', display: 'flex', minHeight: '100vh', justifyContent: 'center' }}
      >
        <Stack spacing={2} sx={{ maxWidth: 480, p: 3, textAlign: 'center' }}>
          <Typography component="h1" variant="h1">
            Hakamapを終了しました
          </Typography>
          <Typography>
            このページが自動で閉じない場合は、ブラウザのタブを閉じてください。
          </Typography>
        </Stack>
      </Box>
    );
  }

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
            <Button color="inherit" onClick={() => setHelpOpen(true)} size="small">
              操作ガイド
            </Button>
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

      <Dialog open={fileSelectionActive}>
        <DialogTitle>ファイル選択画面を表示しています</DialogTitle>
        <DialogContent>
          Windowsのファイル選択画面で選択またはキャンセルしてください。選択が終わるまで、
          Hakamapの画面は操作できません。
        </DialogContent>
      </Dialog>

      <Dialog fullWidth maxWidth="md" onClose={() => setHelpOpen(false)} open={helpOpen}>
        <DialogTitle>Hakamap 操作ガイド</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={3}>
            <Box component="section">
              <Typography component="h2" gutterBottom variant="h2">
                元に戻す・やり直す
              </Typography>
              <Typography>
                地図上部の「元に戻す」でプロジェクト全体の直前のデータ変更を取り消し、
                「やり直す」で取り消した変更を再適用できます。墓所を選択する必要はありません。
                ズーム、地図の表示移動、検索、選択は元に戻す対象ではありません。変更履歴は、
                墓所を選択したときに右側へ表示される「履歴」タブで確認できます。
              </Typography>
            </Box>
            <Box component="section">
              <Typography component="h2" gutterBottom variant="h2">
                地図の拡大・縮小・表示移動
              </Typography>
              <Typography>
                「拡大」「縮小」ボタン、マウスホイール、または＋／－キーで倍率を変更します。
                「全体表示」または0キーで全体へ戻ります。地図を上下左右へ動かすときは、
                マウスの中ボタンを押しながらドラッグするか、Spaceキーを押しながら 左ドラッグします。
              </Typography>
            </Box>
            <Box component="section">
              <Typography component="h2" gutterBottom variant="h2">
                墓所の選択・移動
              </Typography>
              <Typography>
                クリックで1件選択、Ctrl＋クリックで選択の追加・解除ができます。空白から
                ドラッグすると矩形内の墓所を選択し、Ctrl＋ドラッグで現在の選択へ追加します。
                選択した墓所はドラッグ、矢印キーで1単位、Shift＋矢印キーで10単位移動できます。
              </Typography>
            </Box>
            <Box component="section">
              <Typography component="h2" gutterBottom variant="h2">
                編集モードと保存
              </Typography>
              <Typography>
                地図上部の「選択」「エリア編集」「墓所作成」「エリア作成」から操作を選びます。
                プロパティの入力は「適用」で編集状態へ反映し、上部の「保存」でファイルへ
                保存します。保存後も、プロジェクトを開いている間は履歴を元に戻せます。
              </Typography>
            </Box>
            <Box component="section">
              <Typography component="h2" gutterBottom variant="h2">
                ファイル選択・終了・再表示
              </Typography>
              <Typography>
                Windowsのファイル選択画面が開いている間はHakamap画面を操作できません。
                アプリを終了するときは「Hakamapを終了」を使用します。ブラウザのタブだけを
                閉じた場合は、デスクトップまたはスタートメニューのHakamapショートカットから
                画面を開き直せます。
              </Typography>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setHelpOpen(false)} variant="contained">
            閉じる
          </Button>
        </DialogActions>
      </Dialog>

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
