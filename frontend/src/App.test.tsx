import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import App from './App';
import AppProviders from './app/AppProviders';

describe('App', () => {
  it('3領域の編集画面を表示する', () => {
    render(
      <AppProviders>
        <App />
      </AppProviders>,
    );

    expect(screen.getByRole('heading', { name: 'Hakamap' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'エリアと管理状態' })).toBeInTheDocument();
    expect(screen.getByRole('region', { name: '墓地地図' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'プロパティ' })).toBeInTheDocument();
  });
});
