import 'fake-indexeddb/auto';
import { expect, it, vi } from 'vitest';
import { loadMap, saveMap, loadNames, saveNames } from './storage.web';
import { archive, windowsProject } from './archive-fixtures';
import { readArchive } from './archive';

it('IndexedDBに参照情報だけを保存し、表示設定を地図と別に保持する', async () => {
  expect(await loadMap()).toBeNull();
  expect(await loadNames()).toBe(true);
  const data = readArchive(archive({ project: windowsProject() }));
  await saveMap(data);
  await saveNames(false);
  expect(await loadMap()).toEqual(data);
  expect(await loadNames()).toBe(false);
  expect(JSON.stringify(await loadMap())).not.toContain('参照に不要な備考');
  expect(JSON.stringify(await loadMap())).not.toContain('参照に不要な戒名');
  await saveMap({ ...data, createdAt: '2026-10-24T01:00:00.000Z' });
  expect(await loadNames()).toBe(false);
});
it('保存トランザクションが中断されたら旧データを保持する', async () => {
  const old = await loadMap();
  const transaction = IDBDatabase.prototype.transaction;
  const mock = vi.spyOn(IDBDatabase.prototype, 'transaction').mockImplementation(function (
    this: IDBDatabase,
    ...args: Parameters<IDBDatabase['transaction']>
  ) {
    const tx = transaction.apply(this, args);
    if (args[1] === 'readwrite') queueMicrotask(() => tx.abort());
    return tx;
  });
  try {
    await expect(saveMap({ ...old!, createdAt: '2026-10-25T01:00:00.000Z' })).rejects.toThrow();
  } finally {
    mock.mockRestore();
  }
  expect(await loadMap()).toEqual(old);
});
it('新しいモジュールからも同じ保存データと設定を読める', async () => {
  const expected = await loadMap();
  vi.resetModules();
  const fresh = await import('./storage.web');
  expect(await fresh.loadMap()).toEqual(expected);
  expect(await fresh.loadNames()).toBe(false);
});
