import { strToU8, zipSync } from 'fflate';
import { hash } from './archive';
import { map, projectId } from './fixtures';

export const backgroundId = '44444444-4444-4444-8444-444444444444';
export const attachmentId = '55555555-5555-4555-8555-555555555555';
export const backgroundPath = `assets/backgrounds/${backgroundId}.png`;
export const attachmentPath = `assets/attachments/${attachmentId}.jpg`;
export const createdAt = '2026-10-23T01:00:00.000Z';
export const png = Uint8Array.from(
  atob(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jR1sAAAAASUVORK5CYII=',
  ),
  (character) => character.charCodeAt(0),
);
export function windowsProject() {
  return {
    schemaVersion: 1,
    project: { id: projectId, name: map.projectName, createdAt, updatedAt: createdAt },
    areas: [],
    graves: map.graves.map((grave) => ({
      id: grave.id,
      x: grave.x,
      y: grave.y,
      width: grave.width,
      height: grave.height,
      rotation: grave.rotation,
      name: grave.name,
      notes: '参照に不要な備考',
      updatedAt: createdAt,
    })),
    people: map.graves.flatMap((grave, graveIndex) =>
      grave.people.map((name, index) => ({
        id: `${graveIndex * 2 + index + 6}6666666-6666-4666-8666-666666666666`,
        graveId: grave.id,
        name,
        posthumousName: '参照に不要な戒名',
        createdAt,
        updatedAt: createdAt,
        displayOrder: index,
      })),
    ),
    assets: [] as Record<string, unknown>[],
    background: undefined as Record<string, unknown> | undefined,
  };
}
export function archive({
  project = windowsProject(),
  override = {},
  assets = {},
  extra = {},
}: {
  project?: unknown;
  override?: Record<string, unknown>;
  assets?: Record<string, Uint8Array>;
  extra?: Record<string, Uint8Array>;
} = {}) {
  const files = { 'project.json': strToU8(JSON.stringify(project)), ...assets };
  return zipSync({
    'manifest.json': strToU8(
      JSON.stringify({
        formatVersion: 1,
        archiveType: 'export',
        applicationVersion: '0.1.10',
        createdAt,
        projectId,
        projectName: map.projectName,
        files: Object.entries(files).map(([path, bytes]) => ({
          path,
          sizeBytes: bytes.length,
          sha256: hash(bytes),
        })),
        ...override,
      }),
    ),
    ...files,
    ...extra,
  });
}
export function backgroundProject() {
  const project = windowsProject();
  project.background = { assetId: backgroundId, x: 10, y: 20, rotation: 90, scaleX: 2, scaleY: 3 };
  project.assets = [
    {
      id: backgroundId,
      assetType: 'background',
      relativePath: backgroundPath,
      sourceMediaType: 'application/pdf',
      storedMediaType: 'image/png',
      originalFileName: '検証背景.pdf',
      sizeBytes: png.length,
      sha256: hash(png),
      createdAt,
    },
  ];
  return project;
}
