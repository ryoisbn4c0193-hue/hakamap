import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  chooseBackgroundFile,
  clearSessionForTest,
  exchangeBootstrapToken,
  executeProjectCommand,
  hakamapFetch,
  initializeSession,
} from './hakamapClient';

describe('hakamapClient', () => {
  afterEach(() => {
    clearSessionForTest();
    window.history.replaceState(null, '', '/');
    vi.restoreAllMocks();
  });

  it('URLフラグメントの起動トークンを除去して専用ヘッダーで交換する', async () => {
    window.history.replaceState(null, '', '/#bootstrap=one-time-token');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(null, { status: 204 }));

    await exchangeBootstrapToken();

    expect(window.location.hash).toBe('');
    const request = fetchMock.mock.calls[0];
    expect(request?.[0]).toBe('/bootstrap');
    expect(new Headers(request?.[1]?.headers).get('X-Hakamap-Bootstrap-Token')).toBe(
      'one-time-token',
    );
  });

  it('CSRFトークンをメモリで保持して変更要求へ付与する', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ authenticated: true }), {
          headers: {
            'Content-Type': 'application/json',
            'X-Hakamap-CSRF-Token': 'csrf-token',
          },
          status: 200,
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 202 }));

    await initializeSession();
    await hakamapFetch('/api/v1/application/exit', { method: 'POST' });

    const request = fetchMock.mock.calls[1];
    expect(request?.[0]).toBe('/api/v1/application/exit');
    expect(new Headers(request?.[1]?.headers).get('X-Hakamap-CSRF-Token')).toBe('csrf-token');
  });

  it('セッション失効後は変更要求を自動再送しない', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ authenticated: true }), {
          headers: {
            'Content-Type': 'application/json',
            'X-Hakamap-CSRF-Token': 'csrf-token',
          },
          status: 200,
        }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 401 }));

    await initializeSession();
    await hakamapFetch('/api/v1/application/exit', { method: 'POST' });

    await expect(hakamapFetch('/api/v1/application/exit', { method: 'POST' })).rejects.toThrow(
      'csrf-token-unavailable',
    );
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
  });

  it('背景取込用の単一ファイル選択を開始する', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ authenticated: true }), {
          headers: {
            'Content-Type': 'application/json',
            'X-Hakamap-CSRF-Token': 'csrf-token',
          },
          status: 200,
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            displayNames: ['墓地.png'],
            expiresAt: '2026-08-11T00:10:00Z',
            fileSelectionIds: ['22222222-2222-4222-8222-222222222222'],
            status: 'selected',
          }),
          { headers: { 'Content-Type': 'application/json' }, status: 200 },
        ),
      );

    await initializeSession();
    await expect(chooseBackgroundFile()).resolves.toBe('22222222-2222-4222-8222-222222222222');

    const request = fetchMock.mock.calls[1];
    expect(request?.[0]).toBe('/api/v1/file-selections');
    expect(JSON.parse(String(request?.[1]?.body))).toEqual({
      purpose: 'backgroundImport',
      selectionMode: 'singleFile',
    });
  });

  it('配置警告を通常エラーにせず確認待ち応答として返す', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ authenticated: true }), {
          headers: {
            'Content-Type': 'application/json',
            'X-Hakamap-CSRF-Token': 'csrf-token',
          },
          status: 200,
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            confirmationToken: 'confirmation-token',
            expiresAt: '2026-08-11T00:10:00Z',
            revision: 4,
            status: 'confirmationRequired',
            warnings: [{ code: 'outsideAreaBounds', count: 1 }],
          }),
          { headers: { 'Content-Type': 'application/json' }, status: 200 },
        ),
      );

    await initializeSession();
    await expect(executeProjectCommand('project', 4, 'moveGraves', {})).resolves.toMatchObject({
      confirmationToken: 'confirmation-token',
      status: 'confirmationRequired',
    });
  });
});
