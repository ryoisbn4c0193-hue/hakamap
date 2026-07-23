import { Alert, AlertTitle, Box, Button } from '@mui/material';
import { Component, type ReactNode } from 'react';

type AppErrorBoundaryProps = {
  children: ReactNode;
};

type AppErrorBoundaryState = {
  hasError: boolean;
};

class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  public state: AppErrorBoundaryState = {
    hasError: false,
  };

  public static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true };
  }

  public componentDidCatch(): void {
    // Phase 4で個人情報を除外したアプリケーションログへ接続する。
  }

  public render(): ReactNode {
    if (this.state.hasError) {
      return (
        <Box component="main" sx={{ margin: 'auto', maxWidth: 640, padding: 3 }}>
          <Alert
            action={
              <Button color="inherit" onClick={() => window.location.reload()} size="small">
                再読み込み
              </Button>
            }
            severity="error"
          >
            <AlertTitle>画面を表示できませんでした</AlertTitle>
            入力内容を確認できないため、同じ操作を繰り返さず画面を再読み込みしてください。
          </Alert>
        </Box>
      );
    }

    return this.props.children;
  }
}

export default AppErrorBoundary;
