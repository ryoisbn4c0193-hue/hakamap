import { openDatabaseAsync } from 'expo-sqlite';
import { savedSchema, type SavedMap } from './archive';

const database = openDatabaseAsync('hakamap.db').then(async (db) => {
  await db.execAsync(
    'PRAGMA journal_mode = WAL; CREATE TABLE IF NOT EXISTS state (key TEXT PRIMARY KEY, value TEXT NOT NULL)',
  );
  return db;
});
export async function loadMap(): Promise<SavedMap | null> {
  const row = await (
    await database
  ).getFirstAsync<{ value: string }>('SELECT value FROM state WHERE key = ?', 'map');
  return row ? savedSchema.parse(JSON.parse(row.value)) : null;
}
export async function saveMap(value: SavedMap): Promise<void> {
  const serialized = JSON.stringify(savedSchema.parse(value));
  const db = await database;
  // 単一SQL文のトランザクションで全件置換する。失敗時は旧行を保持する。
  await db.runAsync(
    'INSERT INTO state(key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',
    'map',
    serialized,
  );
}
export async function loadNames(): Promise<boolean> {
  const row = await (
    await database
  ).getFirstAsync<{ value: string }>('SELECT value FROM state WHERE key = ?', 'names');
  return row?.value !== 'false';
}
export async function saveNames(value: boolean): Promise<void> {
  await (
    await database
  ).runAsync(
    'INSERT INTO state(key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',
    'names',
    String(value),
  );
}
