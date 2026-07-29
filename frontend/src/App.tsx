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
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { closeProject, requestApplicationExit, saveProject } from './api/hakamapClient';
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
    </Box>
  );
}

export default App;
