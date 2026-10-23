import { createHash } from 'node:crypto';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { generateImageAsync } from '@expo/image-utils';
import { licenseNotices } from './licenses.mjs';

const output = path.resolve(import.meta.dirname, '../dist-pwa');
// Windows版と同じ原画像から、端末ごとの必要サイズだけを生成する。
const projectRoot = path.resolve(import.meta.dirname, '..');
const iconSource = path.resolve(projectRoot, '../packaging/windows/assets/hakamap-icon.png');
await Promise.all(
  [180, 192, 512].map(async (size) => {
    const { source } = await generateImageAsync(
      { projectRoot, cacheType: 'pwa-icons' },
      { src: iconSource, width: size, height: size, resizeMode: 'contain', format: 'png' },
    );
    await writeFile(path.join(output, `hakamap-icon-${size}.png`), source);
  }),
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
