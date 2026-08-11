import {
  BackupOutlined,
  FileDownloadOutlined,
  FolderOpenOutlined,
  HelpOutlineOutlined,
  MapOutlined,
  PowerSettingsNew,
  SaveOutlined,
  Tune,
} from '@mui/icons-material';
import {
  Alert,
  AppBar,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Toolbar,
  Tooltip,
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
  HakamapApiError,
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
    setSaving(true);
    setAppMessage(undefined);
    try {
      if (openProjectId !== undefined) {
        await closeProject(openProjectId, action);
      }
      setCloseConfirmationOpen(false);
      setEditorVisible(false);
      setOpenProjectId(undefined);
      resetEditor();
      await queryClient.invalidateQueries({ queryKey: ['projectCatalog'] });
    } catch (error) {
      setAppMessage(
        error instanceof HakamapApiError && error.code === 'asset-staging-cleanup-failed'
          ? '一時ファイルを削除できないため、プロジェクトを閉じられませんでした。ファイルを使用中のアプリを閉じて再試行してください。'
          : 'プロジェクトを閉じられませんでした。編集内容は保持されています。',
      );
    } finally {
      setSaving(false);
    }
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
    <Box className={`app-shell${editorVisible ? ' app-shell--editor' : ''}`}>
      <AppBar className="app-header" color="primary" elevation={0} position="static">
        <Toolbar className="app-header__toolbar">
          <Box className="app-brand">
            <Typography component="h1" variant="h1">
              Hakamap
            </Typography>
            <Typography className="app-brand__caption">墓地管理</Typography>
          </Box>
          <Stack className="app-navigation" direction="row" spacing={0.5}>
            {editorVisible ? (
              <>
                <Tooltip title={saving ? '保存中' : '保存'}>
                  <span>
                    <IconButton
                      aria-label={saving ? '保存中' : '保存'}
                      color="inherit"
                      disabled={saving || openProjectId === undefined}
                      onClick={() => {
                        if (openProjectId === undefined) return;
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
                      <SaveOutlined fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title="エリアパネル">
                  <IconButton
                    aria-label="エリアパネル"
                    color="inherit"
                    onClick={toggleLeftPanel}
                    size="small"
                  >
                    <MapOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="プロパティパネル">
                  <IconButton
                    aria-label="プロパティパネル"
                    color="inherit"
                    onClick={toggleRightPanel}
                    size="small"
                  >
                    <Tune fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="エクスポート">
                  <span>
                    <IconButton
                      aria-label="エクスポート"
                      color="inherit"
                      disabled={saving || openProjectId === undefined}
                      onClick={() => {
                        if (openProjectId === undefined) return;
                        setSaving(true);
                        void getProjectSnapshot(openProjectId)
                          .then(async (snapshot) => {
                            if (snapshot.dirty) await saveProject(openProjectId);
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
                      <FileDownloadOutlined fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title="バックアップ">
                  <IconButton
                    aria-label="バックアップ"
                    color="inherit"
                    onClick={() => setBackupsOpen(true)}
                    size="small"
                  >
                    <BackupOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="プロジェクト一覧">
                  <IconButton
                    aria-label="プロジェクト一覧"
                    color="inherit"
                    onClick={() => setCloseConfirmationOpen(true)}
                    size="small"
                  >
                    <FolderOpenOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>
            ) : null}
            <Tooltip title="操作ガイド">
              <IconButton
                aria-label="操作ガイド"
                color="inherit"
                onClick={() => setHelpOpen(true)}
                size="small"
              >
                <HelpOutlineOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Hakamapを終了">
              <span>
                <IconButton
                  aria-label="Hakamapを終了"
                  color="inherit"
                  disabled={exitRequested}
                  onClick={() => void exitApplication()}
                  size="small"
                >
                  <PowerSettingsNew fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
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
          <TableContainer component={Paper} variant="outlined">
            <Table aria-label="Hakamapの操作方法">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: 700, width: '28%' }}>操作</TableCell>
                  <TableCell sx={{ fontWeight: 700 }}>方法</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                <TableRow>
                  <TableCell component="th" scope="row">
                    元に戻す・やり直す
                  </TableCell>
                  <TableCell>
                    地図上部の「元に戻す」「やり直す」を使用します。墓所の選択は不要です。ズーム、表示移動、検索、選択は対象外です。
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    拡大・縮小
                  </TableCell>
                  <TableCell>
                    「拡大」「縮小」、マウスホイール、または＋／－キーを使用します。
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    全体表示
                  </TableCell>
                  <TableCell>「全体表示」または0キーで地図全体へ戻ります。</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    地図の表示移動
                  </TableCell>
                  <TableCell>
                    マウスの中ボタンを押しながらドラッグするか、Spaceキーを押しながら左ドラッグします。
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    背景画像の移動
                  </TableCell>
                  <TableCell>「背景移動」を選び、画像内をドラッグします。</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    背景画像の拡縮・回転
                  </TableCell>
                  <TableCell>
                    右下の四角で拡縮し、Shiftキーで縦横比を維持します。上辺中央の丸で中心を基準に回転し、90度単位の近くでは吸着します。
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    墓所の選択
                  </TableCell>
                  <TableCell>
                    クリックで1件、Ctrl＋クリックで追加・解除します。空白からのドラッグで矩形選択し、Ctrl＋ドラッグで現在の選択へ追加します。
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    墓所の移動
                  </TableCell>
                  <TableCell>
                    ドラッグまたは矢印キーで1単位移動します。Shift＋矢印キーでは10単位移動します。
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    編集・保存
                  </TableCell>
                  <TableCell>
                    地図上部で編集モードを選びます。プロパティの「適用」で編集へ反映し、上部の「保存」でファイルへ保存します。
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    ファイル選択
                  </TableCell>
                  <TableCell>
                    Windowsのファイル選択画面で選択またはキャンセルします。表示中はHakamap画面を操作できません。
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell component="th" scope="row">
                    終了・再表示
                  </TableCell>
                  <TableCell>
                    終了は「Hakamapを終了」を使用します。タブだけを閉じた場合は、Hakamapのショートカットから再表示できます。
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </TableContainer>
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
          <Button disabled={saving} onClick={() => setCloseConfirmationOpen(false)}>
            キャンセル
          </Button>
          <Button
            color="error"
            disabled={saving}
            onClick={() => {
              void leaveEditor('discard');
            }}
          >
            {saving ? '処理中' : '変更を破棄'}
          </Button>
          <Button
            disabled={saving}
            onClick={() => {
              void leaveEditor('save');
            }}
            variant="contained"
          >
            {saving ? '処理中' : '保存して閉じる'}
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
