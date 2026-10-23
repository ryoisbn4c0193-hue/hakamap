import { z } from 'zod';
import { mapSchema, type MapData } from './model';

const number = z.number().finite();
const uuid = z.uuid();
const label = z
  .string()
  .max(200)
  .regex(/^[^\u0000-\u001f\u007f\u2028\u2029]*$/u);
const geometry = z.object({
  id: uuid,
  x: number,
  y: number,
  width: number.positive(),
  height: number.positive(),
  rotation: number.min(0).lt(360).default(0),
});
const colors = {
  blue: '#1565C0',
  green: '#2E7D32',
  purple: '#6A1B9A',
  teal: '#00695C',
  indigo: '#283593',
  brown: '#6D4C41',
  pink: '#AD1457',
  navy: '#1A237E',
  violet: '#7B1FA2',
  turquoise: '#00838F',
  olive: '#827717',
  burgundy: '#880E4F',
};
// 参照に必要な項目を型検証する。備考・戒名などの不要項目はここで除外する。
export const projectSchema = z
  .object({
    schemaVersion: z.literal(1),
    project: z.object({ id: uuid, name: label }),
    areas: z
      .array(
        geometry.extend({
          name: label,
          colorPreset: z.enum(
            Object.keys(colors) as [keyof typeof colors, ...(keyof typeof colors)[]],
          ),
          visible: z.boolean(),
          displayOrder: z.number().int().nonnegative(),
        }),
      )
      .max(12),
    graves: z.array(geometry.extend({ name: label.optional() })).max(5000),
    people: z.array(z.object({ id: uuid, graveId: uuid, name: label.optional() })).max(20000),
    background: z
      .object({
        assetId: uuid,
        x: number,
        y: number,
        rotation: number.min(0).lt(360),
        scaleX: number.positive(),
        scaleY: number.positive(),
      })
      .optional(),
    assets: z
      .array(
        z.object({
          id: uuid,
          assetType: z.enum(['background', 'attachment']),
          relativePath: z.string(),
          storedMediaType: z.enum(['image/png', 'image/jpeg', 'image/webp']),
          sizeBytes: z.number().int().positive(),
          sha256: z.string().regex(/^[a-f0-9]{64}$/),
          graveId: uuid.optional(),
        }),
      )
      .max(100000),
  })
  .superRefine((project, ctx) => {
    const allIds = [
      project.project.id,
      ...project.areas.map((x) => x.id),
      ...project.graves.map((x) => x.id),
      ...project.people.map((x) => x.id),
      ...project.assets.map((x) => x.id),
    ];
    if (new Set(allIds).size !== allIds.length)
      ctx.addIssue({ code: 'custom', message: 'IDが重複しています。' });
    const graves = new Set(project.graves.map((x) => x.id));
    if (
      project.people.some((person) => !graves.has(person.graveId)) ||
      project.assets.some(
        (asset) =>
          asset.assetType === 'attachment' && (!asset.graveId || !graves.has(asset.graveId)),
      )
    )
      ctx.addIssue({ code: 'custom', message: '人物・添付の墓所参照が不正です。' });
    const paths = project.assets.map((asset) => asset.relativePath.toLowerCase());
    if (new Set(paths).size !== paths.length)
      ctx.addIssue({ code: 'custom', message: 'アセットのパスが重複しています。' });
    if (
      project.background &&
      !project.assets.some(
        (asset) => asset.id === project.background?.assetId && asset.assetType === 'background',
      )
    )
      ctx.addIssue({ code: 'custom', message: '背景の参照が不正です。' });
  });
export type WindowsProject = z.infer<typeof projectSchema>;
export function projectMap(project: WindowsProject, background?: MapData['background']): MapData {
  const people = new Map<string, string[]>();
  for (const person of project.people) {
    if (!person.name) continue;
    const names = people.get(person.graveId) ?? [];
    names.push(person.name);
    people.set(person.graveId, names);
  }
  return mapSchema.parse({
    schemaVersion: 1,
    projectId: project.project.id,
    projectName: project.project.name,
    areas: project.areas
      .filter((area) => area.visible)
      .sort((a, b) => a.displayOrder - b.displayOrder)
      .map((area) => ({
        id: area.id,
        name: area.name,
        x: area.x,
        y: area.y,
        width: area.width,
        height: area.height,
        rotation: area.rotation,
        color: colors[area.colorPreset],
      })),
    graves: project.graves.map((grave) => ({
      id: grave.id,
      name: grave.name ?? '',
      x: grave.x,
      y: grave.y,
      width: grave.width,
      height: grave.height,
      rotation: grave.rotation,
      people: people.get(grave.id) ?? [],
    })),
    background,
  });
}
