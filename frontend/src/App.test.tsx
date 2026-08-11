import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { withFileSelectionActivity } from './api/fileSelectionActivity';
import App from './App';
import AppProviders from './app/AppProviders';

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('初回のプロジェクト管理画面を表示する', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ openProjectId: null, projects: [] }), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        }),
      ),
    );
    render(
      <AppProviders>
        <App />
      </AppProviders>,
    );

    expect(screen.getByRole('heading', { name: 'Hakamap' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'プロジェクト' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新規作成' })).toBeInTheDocument();
    expect(await screen.findByText('プロジェクトはまだありません。')).toBeInTheDocument();
  });

  it('ファイル選択中は画面操作を止める案内を表示する', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ openProjectId: null, projects: [] }), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        }),
      ),
    );
    render(
      <AppProviders>
        <App />
      </AppProviders>,
    );
    let complete: (() => void) | undefined;
    const selection = withFileSelectionActivity(
      () =>
        new Promise<void>((resolve) => {
          complete = resolve;
        }),
    );

    expect(await screen.findByRole('dialog')).toHaveTextContent(
      '選択が終わるまで、 Hakamapの画面は操作できません。',
    );
    complete?.();
    await selection;
  });
});
