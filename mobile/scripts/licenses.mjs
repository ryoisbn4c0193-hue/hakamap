import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';

export async function licenseNotices(projectRoot) {
  const lock = JSON.parse(await readFile(path.join(projectRoot, 'package-lock.json'), 'utf8'));
  const texts = [
    'Hakamap PWA 第三者ライセンス\n実行用依存のライセンスを記載します。ビルド用に含まれる依存も記載される場合があります。',
  ];
  for (const [location, metadata] of Object.entries(lock.packages)) {
    if (!location || metadata.dev) continue;
    const directory = path.join(projectRoot, location);
    const name = location.split('node_modules/').at(-1);
    let entries;
    try {
      entries = await readdir(directory);
    } catch (error) {
      // 別OS・CPU用の任意依存はインストールも配布もしない。
      if (error.code === 'ENOENT' && metadata.optional) continue;
      throw error;
    }
    const licenses = entries.filter((entry) => /^(licen[cs]e|copying|notice)(\.|$|-)/i.test(entry));
    const parts = [];
    for (const entry of licenses) {
      try {
        parts.push(await readFile(path.join(directory, entry), 'utf8'));
      } catch (error) {
        if (error.code !== 'EISDIR') throw error;
      }
    }
    texts.push(
      `${name} ${metadata.version}\nLicense: ${metadata.license ?? 'package metadata参照'}\n${parts.join('\n')}`,
    );
  }
  return texts.join('\n\n----------------------------------------\n\n');
}
