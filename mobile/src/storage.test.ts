import { afterAll, expect, it, vi } from 'vitest';

vi.mock('expo-sqlite', async () => {
  const { DatabaseSync } = await import('node:sqlite');
  const db = new DatabaseSync(':memory:');
  return {
    openDatabaseAsync: async () => ({
      execAsync: async (sql: string) => db.exec(sql),
      getFirstAsync: async (sql: string, ...params: string[]) => db.prepare(sql).get(...params),
      runAsync: async (sql: string, ...params: string[]) => db.prepare(sql).run(...params),
    }),
    testDatabase: db,
  };
});
import { loadMap, saveMap, loadNames, saveNames } from './storage';
import { map } from './fixtures';
import { readArchive } from './archive';
import { archive, windowsProject } from './archive-fixtures';

it('SQLiteで全件置換し、更新に失敗したら既存データを保持する', async () => {
  expect(await loadMap()).toBeNull();
  const old = { map, createdAt: '2026-10-23T01:00:00.000Z' };
  await saveMap(old);
  const { testDatabase } = (await import('expo-sqlite')) as unknown as {
    testDatabase: import('node:sqlite').DatabaseSync;
  };
  testDatabase.exec(
    "CREATE TRIGGER reject_replace BEFORE UPDATE ON state WHEN OLD.key = 'map' BEGIN SELECT RAISE(ABORT, 'failure'); END",
  );
  await expect(saveMap({ ...old, createdAt: '2026-10-24T01:00:00.000Z' })).rejects.toThrow();
  expect(await loadMap()).toEqual(old);
  testDatabase.exec('DROP TRIGGER reject_replace');
  await saveMap({ ...old, createdAt: '2026-10-24T01:00:00.000Z' });
  expect((await loadMap())?.createdAt).toBe('2026-10-24T01:00:00.000Z');
});
it('名前表示は初回オンで、地図更新と別に保持する', async () => {
  expect(await loadNames()).toBe(true);
  await saveNames(false);
  expect(await loadNames()).toBe(false);
});
it('Windows形式の取り込み結果だけをDBへ保存する', async () => {
  const data = readArchive(archive({ project: windowsProject() }));
  await saveMap(data);
  const saved = await loadMap();
  expect(saved?.map.graves).toEqual(map.graves);
  const { testDatabase } = (await import('expo-sqlite')) as unknown as {
    testDatabase: import('node:sqlite').DatabaseSync;
  };
  const row = testDatabase.prepare("SELECT value FROM state WHERE key='map'").get() as {
    value: string;
  };
  expect(row.value).not.toContain('参照に不要な備考');
  expect(row.value).not.toContain('参照に不要な戒名');
  expect(row.value).not.toContain('manifest.json');
});
afterAll(async () => {
  const { testDatabase } = (await import('expo-sqlite')) as unknown as {
    testDatabase: import('node:sqlite').DatabaseSync;
  };
  testDatabase.close();
});
