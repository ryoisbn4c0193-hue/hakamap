import { savedSchema, type SavedMap } from './archive';

let opening: Promise<IDBDatabase> | undefined;
function database(): Promise<IDBDatabase> {
  if (!opening)
    opening = new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open('hakamap-reference', 1);
      request.onupgradeneeded = () => request.result.createObjectStore('state');
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error('ほかの画面を閉じて再起動してください。'));
      request.onsuccess = () => {
        const db = request.result;
        db.onversionchange = () => {
          db.close();
          opening = undefined;
        };
        resolve(db);
      };
    }).catch((error: unknown) => {
      opening = undefined;
      throw error;
    });
  return opening;
}
async function read(key: string): Promise<unknown> {
  const db = await database();
  return new Promise((resolve, reject) => {
    const tx = db.transaction('state', 'readonly');
    const request = tx.objectStore('state').get(key);
    tx.oncomplete = () => resolve(request.result);
    tx.onabort = () => reject(tx.error ?? new Error('読み込みに失敗しました。'));
    tx.onerror = () => reject(tx.error);
  });
}
async function write(key: string, value: unknown): Promise<void> {
  const db = await database();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction('state', 'readwrite');
    tx.objectStore('state').put(value, key);
    // 要求の成功ではなくトランザクション完了後に画面を更新する。
    tx.oncomplete = () => resolve();
    tx.onabort = () => reject(tx.error ?? new Error('保存に失敗しました。'));
    tx.onerror = () => reject(tx.error);
  });
}
export async function loadMap(): Promise<SavedMap | null> {
  const row = await read('map');
  return row === undefined ? null : savedSchema.parse(JSON.parse(String(row)));
}
export async function saveMap(value: SavedMap): Promise<void> {
  await write('map', JSON.stringify(savedSchema.parse(value)));
  // 永続化が許可されなくても保存自体の結果は変えない。
  void navigator.storage?.persist?.().catch(() => false);
}
export async function loadNames(): Promise<boolean> {
  return (await read('names')) !== false;
}
export async function saveNames(value: boolean): Promise<void> {
  await write('names', value);
}
