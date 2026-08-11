import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AppProviders from '../app/AppProviders';
import { useUiStore } from '../state/uiStore';

import EditorView from './EditorView';

const projectId = '11111111-1111-4111-8111-111111111111';
const firstGraveId = '22222222-2222-4222-8222-222222222222';
const secondGraveId = '33333333-3333-4333-8333-333333333333';

const snapshot = {
  areas: [],
  assets: [],
  background: null,
  capabilities: { canEdit: true, canRedo: false, canSave: true, canUndo: false },
  dirty: false,
  graveStates: [
    {
      areaId: null,
      completionStatus: 'incomplete',
      graveId: firstGraveId,
      incompleteReasons: ['managementNumberMissing'],
      warnings: [],
    },
    {
      areaId: null,
      completionStatus: 'incomplete',
      graveId: secondGraveId,
      incompleteReasons: ['managementNumberMissing'],
      warnings: [],
    },
  ],
  graves: [
    {
      graveId: firstGraveId,
      height: 10,
      managementNumber: null,
      name: '第一墓所',
      notes: null,
      rotation: 0,
      updatedAt: '2026-07-30T00:00:00Z',
      width: 10,
      x: 0,
      y: 0,
    },
    {
      graveId: secondGraveId,
      height: 10,
      managementNumber: null,
      name: '第二墓所',
      notes: null,
      rotation: 0,
      updatedAt: '2026-07-30T00:00:00Z',
      width: 10,
      x: 20,
      y: 0,
    },
  ],
  historySummary: { canRedo: false, canUndo: false, redoCount: 0, undoCount: 0 },
  project: {
    createdAt: '2026-07-30T00:00:00Z',
    name: '中央墓地',
    projectId,
    updatedAt: '2026-07-30T00:00:00Z',
  },
  projectId,
  revision: 0,
};

describe('EditorView', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    useUiStore.getState().resetEditor();
  });

  it('三領域と四つのプロパティタブを表示する', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(snapshot), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        }),
      ),
    );
    render(
      <AppProviders>
        <EditorView projectId={projectId} />
      </AppProviders>,
    );

    expect(await screen.findByRole('heading', { name: 'エリアと管理状態' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '元に戻す' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'やり直す' })).toBeDisabled();
    fireEvent.click(screen.getByText(/第一墓所/));
    expect(screen.getByRole('tab', { name: '基本情報' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '人物' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '添付' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '履歴' })).toBeInTheDocument();
    expect(screen.getByLabelText('墓所名')).toHaveValue('第一墓所');
  });

  it('未適用入力がある選択変更では破棄確認を表示する', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(snapshot), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        }),
      ),
    );
    render(
      <AppProviders>
        <EditorView projectId={projectId} />
      </AppProviders>,
    );

    fireEvent.click(await screen.findByText(/第一墓所/));
    fireEvent.change(screen.getByLabelText('墓所名'), { target: { value: '入力途中' } });
    fireEvent.click(screen.getByText(/第二墓所/));
    expect(screen.getByRole('dialog', { name: '入力中の変更を破棄しますか' })).toBeInTheDocument();
    expect(screen.getByLabelText('墓所名')).toHaveValue('入力途中');
  });

  it('閉じたプロパティパネルは墓所選択時に再表示する', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(snapshot), {
          headers: { 'Content-Type': 'application/json' },
          status: 200,
        }),
      ),
    );
    useUiStore.setState({ rightPanelCollapsed: true });
    render(
      <AppProviders>
        <EditorView projectId={projectId} />
      </AppProviders>,
    );

    fireEvent.click(await screen.findByText(/第一墓所/));

    expect(screen.getByRole('heading', { name: 'プロパティ' }).closest('aside')).toHaveAttribute(
      'aria-hidden',
      'false',
    );
  });
});
