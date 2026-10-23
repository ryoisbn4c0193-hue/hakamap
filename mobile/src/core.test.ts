import { describe, expect, it } from 'vitest';
import { bounds, search } from './model';
import { readArchive } from './archive';
import {
  archive,
  windowsProject,
  backgroundProject,
  backgroundPath,
  png,
  createdAt,
  attachmentId,
  attachmentPath,
} from './archive-fixtures';
import { hash } from './archive';

import { map, projectId } from './fixtures';
const first = map.graves[0]!.id,
  second = map.graves[1]!.id;
describe('検索対象と正規化', () => {
  it('墓所名と人物氏名を選別する', () => {
    expect(search(map, '佐藤', 'grave')).toEqual([second]);
    expect(search(map, '佐藤', 'person')).toEqual([first]);
    expect(search(map, '佐藤', 'all')).toEqual([first, second]);
  });
  it('複数人物が一致しても墓所は1件になる', () =>
    expect(search(map, '佐藤', 'person')).toEqual([first]));
  it('かな・空白・全半角を正規化する', () =>
    expect(search(map, 'ﾔﾏﾀﾞ はなこ', 'person')).toEqual([second]));
  it('空欄と0件は空配列を返す', () => {
    expect(search(map, '　', 'all')).toEqual([]);
    expect(search(map, '存在しない', 'all')).toEqual([]);
  });
});
describe('地図の範囲', () => {
  it('回転した墓所の四隅を収容する', () => {
    const result = bounds([map.graves[1]!]);
    expect(result.x).toBeCloseTo(35);
    expect(result.y).toBeCloseTo(5);
    expect(result.width).toBeCloseTo(10);
    expect(result.height).toBeCloseTo(20);
  });
});
describe('Windows形式の直接取り込み', () => {
  it('通常のWindowsエクスポートを直接読む', () => {
    const result = readArchive(archive());
    expect(result.map).toEqual(map);
    expect(result.createdAt).toBe(createdAt);
  });
  it('非対応版とバックアップ形式を拒否する', () => {
    expect(() => readArchive(archive({ override: { formatVersion: 2 } }))).toThrow();
    expect(() => readArchive(archive({ override: { archiveType: 'backup' } }))).toThrow();
    expect(() =>
      readArchive(archive({ project: { ...windowsProject(), schemaVersion: 2 } })),
    ).toThrow();
  });
  it('ハッシュ・サイズ不一致を拒否する', () =>
    expect(() =>
      readArchive(
        archive({
          override: { files: [{ path: 'project.json', sizeBytes: 1, sha256: '0'.repeat(64) }] },
        }),
      ),
    ).toThrow());
  it('未宣言ファイルを拒否する', () =>
    expect(() =>
      readArchive(archive({ extra: { 'assets/extra.png': new Uint8Array([1]) } })),
    ).toThrow());
  it('上位ディレクトリ・絶対パス・Windowsパスを拒否する', () => {
    for (const path of [
      '../escape',
      'assets/../escape',
      '/absolute',
      'C:\\escape',
      'assets//escape',
    ])
      expect(() => readArchive(archive({ extra: { [path]: new Uint8Array([1]) } }))).toThrow();
  });
  it('重複する墓所ID・人物IDを拒否する', () => {
    const project = windowsProject();
    expect(() =>
      readArchive(
        archive({ project: { ...project, graves: [project.graves[0], project.graves[0]] } }),
      ),
    ).toThrow();
    expect(() =>
      readArchive(
        archive({ project: { ...project, people: [project.people[0], project.people[0]] } }),
      ),
    ).toThrow();
  });
  it('人物と墓所の参照不整合を拒否する', () => {
    const project = windowsProject();
    project.people[0]!.graveId = projectId;
    expect(() => readArchive(archive({ project }))).toThrow();
  });
  it('別墓地のマニフェストを拒否する', () =>
    expect(() => readArchive(archive({ override: { projectId: first } }))).toThrow());
  it('不正な寸法を拒否する', () => {
    const project = windowsProject();
    project.graves[0]!.width = -1;
    expect(() => readArchive(archive({ project }))).toThrow();
  });
  it('背景画像と位置・回転・縦横倍率を引き継ぐ', () => {
    const result = readArchive(
      archive({ project: backgroundProject(), assets: { [backgroundPath]: png } }),
    );
    expect(result.map.background).toEqual({
      path: backgroundPath,
      x: 10,
      y: 20,
      rotation: 90,
      width: 2,
      height: 3,
    });
    expect(result.backgroundUri).toBe(`data:image/png;base64,${btoa(String.fromCharCode(...png))}`);
  });
  it('参照先のない背景とアセットを拒否する', () => {
    expect(() => readArchive(archive({ project: backgroundProject() }))).toThrow();
    const project = backgroundProject();
    project.assets = [];
    expect(() => readArchive(archive({ project }))).toThrow();
  });
  it('背景のSHA-256や画像形式が不正なら拒否する', () => {
    const project = backgroundProject();
    project.assets[0]!.sha256 = '0'.repeat(64);
    expect(() => readArchive(archive({ project, assets: { [backgroundPath]: png } }))).toThrow();
    const invalid = new Uint8Array([1, 2, 3]);
    project.assets[0]!.sha256 = hash(invalid);
    project.assets[0]!.sizeBytes = invalid.length;
    expect(() =>
      readArchive(archive({ project, assets: { [backgroundPath]: invalid } })),
    ).toThrow();
  });
  it('人物氏名は残し、添付・備考・戒名・元アーカイブを保存対象に含めない', () => {
    const project = windowsProject();
    const attachment = new Uint8Array([255, 216, 255, 1, 2, 3]);
    project.assets = [
      {
        id: attachmentId,
        assetType: 'attachment',
        graveId: first,
        relativePath: attachmentPath,
        storedMediaType: 'image/jpeg',
        sizeBytes: attachment.length,
        sha256: hash(attachment),
        displayName: '参照しない添付',
      },
    ];
    const result = readArchive(archive({ project, assets: { [attachmentPath]: attachment } }));
    expect(result.map.graves[0]!.people).toEqual(map.graves[0]!.people);
    const serialized = JSON.stringify(result);
    for (const value of [
      '参照に不要な備考',
      '参照に不要な戒名',
      '参照しない添付',
      attachmentPath,
      'manifest.json',
    ])
      expect(serialized).not.toContain(value);
    expect(Object.keys(JSON.parse(serialized)).sort()).toEqual(['createdAt', 'map']);
  });
  it('使わない添付でも破損していたら置換候補を作らない', () => {
    const project = windowsProject();
    const attachment = new Uint8Array([1, 2, 3]);
    project.assets = [
      {
        id: attachmentId,
        assetType: 'attachment',
        graveId: first,
        relativePath: attachmentPath,
        storedMediaType: 'image/jpeg',
        sizeBytes: attachment.length,
        sha256: '0'.repeat(64),
      },
    ];
    expect(() =>
      readArchive(archive({ project, assets: { [attachmentPath]: attachment } })),
    ).toThrow();
  });
});
