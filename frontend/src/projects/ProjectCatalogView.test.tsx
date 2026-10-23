import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { importProject } from '../api/hakamapClient';
import AppProviders from '../app/AppProviders';

import ProjectCatalogView from './ProjectCatalogView';

vi.mock('../api/hakamapClient', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/hakamapClient')>()),
  chooseTransferPath: vi.fn().mockResolvedValue('selection-id'),
  getProjectCatalog: vi.fn().mockResolvedValue({ openProjectId: null, projects: [] }),
  importProject: vi.fn(),
}));

describe('プロジェクト一覧のインポート', () => {
  afterEach(cleanup);
  it.each([
    [
      'project-destination-exists',
      '選択した保存先には同じプロジェクトのフォルダがすでに存在するため、取り込めません。既存データの上書きを防ぐため、別の保存先を選択してください。',
    ],
    [
      'catalog-project-duplicate',
      '同じプロジェクトがすでにプロジェクト一覧に登録されているため、取り込めません。一覧から既存のプロジェクトを開いてください。',
    ],
  ])('%s の理由を表示する', async (code, message) => {
    vi.mocked(importProject).mockRejectedValueOnce(new Error(code));
    render(
      <AppProviders>
        <ProjectCatalogView onOpened={vi.fn()} />
      </AppProviders>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'インポート' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});
