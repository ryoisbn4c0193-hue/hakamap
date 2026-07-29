import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { exchangeBootstrapToken, initializeSession } from './api/hakamapClient';
import App from './App';
import AppProviders from './app/AppProviders';
import './index.css';

async function start() {
  try {
    await exchangeBootstrapToken();
    await initializeSession();
  } catch {
    // 認証エラーは画面を表示した上で、API操作時に利用者へ案内する。
  }
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <AppProviders>
        <App />
      </AppProviders>
    </StrictMode>,
  );
}

void start();
