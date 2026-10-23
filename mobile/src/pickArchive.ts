import * as DocumentPicker from 'expo-document-picker';
import { File } from 'expo-file-system';
import { Image } from 'react-native';
import { ArchiveError, MAX_ARCHIVE, readArchive, type SavedMap } from './archive';

export async function pickArchive(): Promise<SavedMap | null> {
  const result = await DocumentPicker.getDocumentAsync({
    type: '*/*',
    copyToCacheDirectory: true,
    multiple: false,
  });
  const asset = result.canceled ? null : result.assets[0];
  if (!asset) return null;
  const file = new File(asset.uri);
  try {
    if (file.size > MAX_ARCHIVE) throw new ArchiveError('ファイルが大きすぎます（上限32MiB）。');
    const candidate = readArchive(await file.bytes());
    if (candidate.backgroundUri) await Image.getSize(candidate.backgroundUri);
    return candidate;
  } finally {
    try {
      if (file.exists) file.delete();
    } catch {
      /* キャッシュ清掃は保存結果を変更しない。 */
    }
  }
}
