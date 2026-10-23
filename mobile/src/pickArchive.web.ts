import { ArchiveError, MAX_ARCHIVE, readArchive, type SavedMap } from './archive';

export async function readFile(file: File): Promise<SavedMap> {
  if (file.size > MAX_ARCHIVE) throw new ArchiveError('ファイルが大きすぎます（上限32MiB）。');
  const candidate = readArchive(new Uint8Array(await file.arrayBuffer()));
  if (candidate.backgroundUri) {
    await new Promise<void>((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve();
      image.onerror = () => reject(new ArchiveError('背景画像を読み込めません。'));
      image.src = candidate.backgroundUri!;
    });
  }
  return candidate;
}

export function pickArchive(): Promise<SavedMap | null> {
  // タップから直接開き、iOSのユーザー操作制約を満たす。
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.hakamap,application/zip,application/octet-stream';
  input.hidden = true;
  document.body.append(input);
  return new Promise<File | null>((resolve) => {
    input.addEventListener('change', () => resolve(input.files?.[0] ?? null), { once: true });
    input.addEventListener('cancel', () => resolve(null), { once: true });
    input.click();
  })
    .then(async (file) => (file ? readFile(file) : null))
    .finally(() => input.remove());
}
