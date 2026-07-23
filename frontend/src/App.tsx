import { AppBar, Box, Button, Divider, Paper, Stack, Toolbar, Typography } from '@mui/material';

import { useUiStore } from './state/uiStore';
import './App.css';

function App() {
  const leftPanelCollapsed = useUiStore((state) => state.leftPanelCollapsed);
  const rightPanelCollapsed = useUiStore((state) => state.rightPanelCollapsed);
  const toggleLeftPanel = useUiStore((state) => state.toggleLeftPanel);
  const toggleRightPanel = useUiStore((state) => state.toggleRightPanel);

  return (
    <Box className="app-shell">
      <AppBar color="primary" elevation={1} position="static">
        <Toolbar variant="dense">
          <Typography component="h1" sx={{ flexGrow: 1 }} variant="h1">
            Hakamap
          </Typography>
          <Stack direction="row" spacing={1}>
            <Button color="inherit" onClick={toggleLeftPanel} size="small">
              エリア
            </Button>
            <Button color="inherit" onClick={toggleRightPanel} size="small">
              プロパティ
            </Button>
          </Stack>
        </Toolbar>
      </AppBar>

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
    </Box>
  );
}

export default App;
