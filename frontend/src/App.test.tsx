import { fireEvent, render, screen } from '@testing-library/react';
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

  it('保存場所を表示せずデフォルトプロジェクトを識別できる', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            openProjectId: null,
            projects: [
              {
                available: false,
                createdAt: '2026-08-11T00:00:00Z',
                defaultProject: true,
                locationLabel: '利用者には表示しない保存場所',
                name: '既定の墓地',
                projectId: '00000000-0000-4000-8000-000000000001',
                recoveryCandidate: false,
                state: 'active',
                updatedAt: '2026-08-11T00:00:00Z',
              },
            ],
          }),
          { headers: { 'Content-Type': 'application/json' }, status: 200 },
        ),
      ),
    );
    render(
      <AppProviders>
        <App />
      </AppProviders>,
    );

    expect(await screen.findByRole('heading', { name: '既定の墓地' })).toBeInTheDocument();
    expect(screen.getByText('デフォルト')).toBeInTheDocument();
    expect(screen.queryByText(/利用者には表示しない保存場所/)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'デフォルトにする' })).not.toBeInTheDocument();
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

  it('共通メニューから操作ガイドを表示する', async () => {
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

    fireEvent.click(screen.getByRole('button', { name: '操作ガイド' }));

    const guide = await screen.findByRole('dialog', { name: 'Hakamap 操作ガイド' });
    expect(screen.getByRole('table', { name: 'Hakamapの操作方法' })).toBeInTheDocument();
    expect(guide).toHaveTextContent('元に戻す・やり直す');
    expect(guide).toHaveTextContent('マウスの中ボタン');
    expect(guide).toHaveTextContent('Ctrl＋クリック');
  });
});
