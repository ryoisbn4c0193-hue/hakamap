import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '../dist-pwa');
const base = (process.env.HAKAMAP_BASE_PATH ?? '').replace(/\/$/, '') + '/';
const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.json': 'application/json',
  '.webmanifest': 'application/manifest+json',
  '.png': 'image/png',
  '.txt': 'text/plain; charset=utf-8',
};
createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    if (!pathname.startsWith(base)) {
      response.writeHead(404).end();
      return;
    }
    const relative = pathname.slice(base.length) || 'index.html';
    const target = path.resolve(root, relative);
    if (!target.startsWith(root + path.sep)) {
      response.writeHead(403).end();
      return;
    }
    const data = await readFile(target);
    response
      .writeHead(200, {
        'Content-Type': types[path.extname(target)] ?? 'application/octet-stream',
        'Cache-Control': 'no-cache',
      })
      .end(data);
  } catch {
    response.writeHead(404).end();
  }
}).listen(4173, '127.0.0.1', () => console.log('PWA確認用サーバー: http://127.0.0.1:4173' + base));
