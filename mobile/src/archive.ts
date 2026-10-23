import { unzipSync, strFromU8 } from 'fflate';
import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex } from '@noble/hashes/utils.js';
import { z } from 'zod';
import { imageSize } from 'image-size';
import { mapSchema } from './model';
import { projectSchema, projectMap } from './project';

export class ArchiveError extends Error {}

export const MAX_ARCHIVE = 32 * 1024 * 1024;
const MAX_EXPANDED = 48 * 1024 * 1024;
const manifestSchema = z
  .object({
    formatVersion: z.literal(1),
    archiveType: z.literal('export'),
    applicationVersion: z.string().min(1).max(100),
    createdAt: z.iso.datetime(),
    projectId: z.uuid(),
    projectName: z.string().max(200),
    files: z
      .array(
        z
          .object({
            path: z.string(),
            sizeBytes: z.number().int().nonnegative().max(MAX_EXPANDED),
            sha256: z.string().regex(/^[a-f0-9]{64}$/),
          })
          .strict(),
      )
      .min(1)
      .max(100000),
  })
  .strict();
export const savedSchema = z
  .object({
    map: mapSchema,
    createdAt: z.iso.datetime(),
    backgroundUri: z
      .string()
      .max(MAX_EXPANDED * 2)
      .optional(),
  })
  .strict()
  .superRefine((value, ctx) => {
    if (Boolean(value.map.background) !== Boolean(value.backgroundUri))
      ctx.addIssue({ code: 'custom', message: '背景画像が不整合です' });
  });
export type SavedMap = z.infer<typeof savedSchema>;
export function hash(bytes: Uint8Array): string {
  return bytesToHex(sha256(bytes));
}
const safePath = (path: string) => {
  if (path === 'manifest.json' || path === 'project.json') return true;
  return (
    /^assets\/[a-zA-Z0-9._/-]+$/.test(path) &&
    path.split('/').every((part) => part !== '' && part !== '.' && part !== '..')
  );
};
export function readArchive(bytes: Uint8Array): SavedMap {
  if (bytes.length > MAX_ARCHIVE) throw new ArchiveError('ファイルが大きすぎます（上限32MiB）。');
  let total = 0;
  const names = new Set<string>();
  const files = unzipSync(bytes, {
    filter: (entry) => {
      if (!safePath(entry.name) || names.has(entry.name.toLowerCase()) || names.size >= 100001)
        throw new ArchiveError('ファイル構成が不正です。');
      names.add(entry.name.toLowerCase());
      total += entry.originalSize;
      const limit = entry.name === 'manifest.json' ? 10 * 1024 * 1024 : MAX_EXPANDED;
      if (
        entry.originalSize > limit ||
        total > MAX_EXPANDED ||
        entry.originalSize > Math.max(1, entry.size) * 1000
      )
        throw new ArchiveError('展開サイズが上限を超えています。');
      return true;
    },
  });
  const manifestBytes = files['manifest.json'];
  if (!manifestBytes) throw new ArchiveError('対応するデータファイルではありません。');
  const manifest = manifestSchema.parse(JSON.parse(strFromU8(manifestBytes)));
  const declared = new Set<string>();
  for (const file of manifest.files) {
    if (!safePath(file.path) || file.path === 'manifest.json' || declared.has(file.path))
      throw new ArchiveError('ファイル一覧が不正です。');
    declared.add(file.path);
    const content = files[file.path];
    if (!content || content.length !== file.sizeBytes || hash(content) !== file.sha256)
      throw new ArchiveError('データが破損しています。');
  }
  if (Object.keys(files).length !== declared.size + 1 || !declared.has('project.json'))
    throw new ArchiveError('ファイル一覧と内容が一致しません。');
  const project = projectSchema.parse(JSON.parse(strFromU8(files['project.json']!)));
  if (project.project.id !== manifest.projectId) throw new ArchiveError('墓地情報が一致しません。');
  for (const asset of project.assets) {
    const content = files[asset.relativePath];
    const prefix = asset.assetType === 'background' ? 'assets/backgrounds/' : 'assets/attachments/';
    if (
      !asset.relativePath.startsWith(prefix) ||
      !content ||
      !declared.has(asset.relativePath) ||
      content.length !== asset.sizeBytes ||
      hash(content) !== asset.sha256
    )
      throw new ArchiveError('アセットの参照または内容が不正です。');
  }
  let background;
  let backgroundUri: string | undefined;
  if (project.background) {
    const placement = project.background;
    const asset = project.assets.find((item) => item.id === placement.assetId)!;
    const content = files[asset.relativePath]!;
    const extension = ({ 'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp' } as const)[
      asset.storedMediaType
    ];
    const valid =
      extension === 'png'
        ? content[0] === 137 && strFromU8(content.slice(1, 4)) === 'PNG'
        : extension === 'jpg'
          ? content[0] === 255 && content[1] === 216 && content[2] === 255
          : strFromU8(content.slice(0, 4)) === 'RIFF' && strFromU8(content.slice(8, 12)) === 'WEBP';
    if (!valid) throw new ArchiveError('背景画像の形式が不正です。');
    const dimensions = imageSize(content);
    if (dimensions.width > 4096 || dimensions.height > 4096)
      throw new ArchiveError('背景画像が大きすぎます（最大4096px）。');
    background = {
      path: asset.relativePath,
      x: placement.x,
      y: placement.y,
      rotation: placement.rotation,
      width: dimensions.width * placement.scaleX,
      height: dimensions.height * placement.scaleY,
    };
    // 元アーカイブ・添付は保存せず、参照用画像だけDBへ保持する。
    backgroundUri = `data:image/${extension === 'jpg' ? 'jpeg' : extension};base64,${toBase64(content)}`;
  }
  const map = projectMap(project, background);
  return savedSchema.parse({ map, createdAt: manifest.createdAt, backgroundUri });
}
function toBase64(bytes: Uint8Array): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  const chunks: string[] = [];
  let chunk = '';
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i]!,
      b = bytes[i + 1],
      c = bytes[i + 2];
    chunk +=
      alphabet[a >> 2]! +
      alphabet[((a & 3) << 4) | ((b ?? 0) >> 4)]! +
      (b === undefined ? '=' : alphabet[((b & 15) << 2) | ((c ?? 0) >> 6)]!) +
      (c === undefined ? '=' : alphabet[c & 63]!);
    if (chunk.length >= 16384) {
      chunks.push(chunk);
      chunk = '';
    }
  }
  return chunks.join('') + chunk;
}
