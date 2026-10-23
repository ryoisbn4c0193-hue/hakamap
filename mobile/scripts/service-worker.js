/* 配布用ファイルだけをキャッシュし、取り込んだデータや外部通信は扱わない。 */
const FILES = __PRECACHE_FILES__;
const VERSION = __PRECACHE_VERSION__;
const PREFIX = `hakamap-shell:${self.registration.scope}:`;
const CACHE = PREFIX + VERSION;
const urls = FILES.map((file) => new URL(file, self.registration.scope).href);
const allowed = new Set(urls);
const index = new URL('index.html', self.registration.scope).href;
self.addEventListener('install', (event) => {
  event.waitUntil(
    (async () => {
      try {
        const cache = await caches.open(CACHE);
        await cache.addAll(urls.map((url) => new Request(url, { cache: 'reload' })));
      } catch (error) {
        await caches.delete(CACHE);
        throw error;
      }
    })(),
  );
});
self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys
          .filter((key) => key.startsWith(PREFIX) && key !== CACHE)
          .map((key) => caches.delete(key)),
      );
      await self.clients.claim();
    })(),
  );
});
self.addEventListener('message', (event) => {
  if (event.data?.type === 'ACTIVATE_UPDATE') event.waitUntil(self.skipWaiting());
});
self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  const root = new URL(self.registration.scope);
  if (url.origin !== root.origin || !url.pathname.startsWith(root.pathname)) return;
  const key = request.mode === 'navigate' ? index : request.url;
  if (!allowed.has(key)) return;
  event.respondWith(
    (async () => {
      const cache = await caches.open(CACHE);
      const stored = await cache.match(key);
      if (stored) return stored;
      // キャッシュ欠損時は通信できる場合だけ回復する。
      const response = await fetch(key);
      if (response.ok) await cache.put(key, response.clone());
      return response;
    })(),
  );
});
