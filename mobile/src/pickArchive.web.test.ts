import { expect, it } from 'vitest';
import { readFile } from './pickArchive.web';
import { archive } from './archive-fixtures';
import { MAX_ARCHIVE } from './archive';

it('ブラウザーのFileから既存Windows形式を直接読み込む', async () => {
  const file = new File([Uint8Array.from(archive()).buffer], 'sample.hakamap');
  expect((await readFile(file)).map.graves[0]?.name).toBe('山田家');
});
it('容量超過はファイルを読み込む前に拒否する', async () => {
  let read = false;
  const file = {
    size: MAX_ARCHIVE + 1,
    arrayBuffer: async () => {
      read = true;
      return new ArrayBuffer(0);
    },
  } as File;
  await expect(readFile(file)).rejects.toThrow('上限32MiB');
  expect(read).toBe(false);
});
it('壊れたアーカイブを拒否する', async () => {
  await expect(readFile(new File(['broken'], 'broken.hakamap'))).rejects.toThrow();
});
