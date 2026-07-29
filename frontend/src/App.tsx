import {
  AppBar,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Paper,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { closeProject, requestApplicationExit } from './api/hakamapClient';
import ProjectCatalogView from './projects/ProjectCatalogView';
import { useUiStore } from './state/uiStore';
import './App.css';

function App() {
  const queryClient = useQueryClient();
  const leftPanelCollapsed = useUiStore((state) => state.leftPanelCollapsed);
  const rightPanelCollapsed = useUiStore((state) => state.rightPanelCollapsed);
  const toggleLeftPanel = useUiStore((state) => state.toggleLeftPanel);
  const toggleRightPanel = useUiStore((state) => state.toggleRightPanel);
  const [exitRequested, setExitRequested] = useState(false);
  const [editorVisible, setEditorVisible] = useState(false);
  const [closeConfirmationOpen, setCloseConfirmationOpen] = useState(false);

  const exitApplication = async () => {
    setExitRequested(true);
    try {
      await requestApplicationExit();
    } catch {
      setExitRequested(false);
    }
  };

  const leaveEditor = async (action: 'save' | 'discard') => {
    const catalog = queryClient.getQueryData<{ openProjectId: string | null }>(['projectCatalog']);
    if (catalog?.openProjectId !== null && catalog?.openProjectId !== undefined) {
      await closeProject(catalog.openProjectId, action);
    }
    setCloseConfirmationOpen(false);
    setEditorVisible(false);
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
                <Button color="inherit" onClick={toggleLeftPanel} size="small">
                  エリア
                </Button>
                <Button color="inherit" onClick={toggleRightPanel} size="small">
                  プロパティ
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

      {editorVisible ? (
        <Box
          className="editor-layout"
          component="main"
          sx={{
            gridTemplateColumns: `${leftPanelCollapsed ? 0 : 240}px minmax(320px, 1fr) ${
              rightPanelCollapsed ? 0 : 320
            }px`,
          }}
        >
          <Paper
            aria-hidden={leftPanelCollapsed}
            className="side-panel"
            component="aside"
            elevation={0}
            square
          >
            <Typography component="h2" variant="h2">
              エリアと管理状態
            </Typography>
            <Divider />
            <Typography color="text.secondary">プロジェクトを開くと一覧を表示します。</Typography>
          </Paper>

          <Box aria-label="墓地地図" className="map-placeholder" role="region">
            <Typography component="h2" variant="h2">
              地図
            </Typography>
            <Typography color="text.secondary">
              Phase 8でPixiJSの地図キャンバスを接続します。
            </Typography>
          </Box>

          <Paper
            aria-hidden={rightPanelCollapsed}
            className="side-panel"
            component="aside"
            elevation={0}
            square
          >
            <Typography component="h2" variant="h2">
              プロパティ
            </Typography>
            <Divider />
            <Typography color="text.secondary">墓所を選択してください。</Typography>
          </Paper>
        </Box>
      ) : (
        <ProjectCatalogView onOpened={() => setEditorVisible(true)} />
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
    </Box>
  );
}

export default App;
