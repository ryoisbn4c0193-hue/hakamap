import { createHash } from 'node:crypto';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { crc32, deflateSync } from 'node:zlib';
import { licenseNotices } from './licenses.mjs';

const output = path.resolve(import.meta.dirname, '../dist-pwa');
// 外部画像を使わず、地図の記号をPNGアイコンへ描く。
function chunk(name, data) {
  const type = Buffer.from(name);
  const size = Buffer.alloc(4);
  size.writeUInt32BE(data.length);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(Buffer.concat([type, data])) >>> 0);
  return Buffer.concat([size, type, data, checksum]);
}
function icon(size) {
  const rows = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      const px = x / size,
        py = y / size;
      const cell =
        px > 0.23 &&
        px < 0.77 &&
        py > 0.23 &&
        py < 0.77 &&
        Math.floor((px - 0.23) / 0.18) % 3 !== 1 &&
        Math.floor((py - 0.23) / 0.18) % 3 !== 1;
      const at = y * (size * 4 + 1) + 1 + x * 4;
      rows.set(cell ? [255, 255, 255, 255] : [36, 155, 211, 255], at);
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size);
  header.writeUInt32BE(size, 4);
  header[8] = 8;
  header[9] = 6;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(rows)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}
await Promise.all(
  [192, 512].map((size) => writeFile(path.join(output, `icon-${size}.png`), icon(size))),
);
await writeFile(path.join(output, '.nojekyll'), '');
await writeFile(
  path.join(output, 'third-party-notices.txt'),
  await licenseNotices(path.resolve(import.meta.dirname, '..')),
);
async function files(directory, prefix = '') {
  const entries = await readdir(directory, { withFileTypes: true });
  const lists = await Promise.all(
    entries.map(async (entry) => {
      const name = prefix + entry.name;
      return entry.isDirectory() ? files(path.join(directory, entry.name), name + '/') : [name];
    }),
  );
  return lists.flat();
}
const entries = (await files(output))
  .filter((name) => name !== 'sw.js' && name !== '.nojekyll')
  .sort();
const hash = createHash('sha256');
for (const name of entries) {
  hash.update(name);
  hash.update(await readFile(path.join(output, name)));
}
const worker = (await readFile(new URL('./service-worker.js', import.meta.url), 'utf8'))
  .replace('__PRECACHE_FILES__', JSON.stringify(entries))
  .replace('__PRECACHE_VERSION__', JSON.stringify(hash.digest('hex').slice(0, 20)));
await writeFile(path.join(output, 'sw.js'), worker);
console.log(`PWAの同梱ファイルを準備しました（${entries.length}件）。`);
