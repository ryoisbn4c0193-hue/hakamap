import { z } from 'zod';

const coordinate = z.number().finite().min(-1_000_000).max(1_000_000);
const box = z.object({
  id: z.uuid(),
  x: coordinate,
  y: coordinate,
  width: z.number().positive().max(1_000_000),
  height: z.number().positive().max(1_000_000),
  rotation: z.number().finite().min(-360).max(360),
});
const name = z.string().max(200);
export const mapSchema = z
  .object({
    schemaVersion: z.literal(1),
    projectId: z.uuid(),
    projectName: name,
    areas: z
      .array(box.extend({ name, color: z.string().regex(/^#[0-9a-fA-F]{6}$/) }).strict())
      .max(12),
    graves: z.array(box.extend({ name, people: z.array(name).max(20_000) }).strict()).max(5_000),
    background: z
      .object({
        path: z.string().regex(/^assets\/backgrounds\/[a-zA-Z0-9-]+\.(png|jpg|jpeg|webp)$/),
        x: coordinate,
        y: coordinate,
        rotation: z.number().finite().min(-360).max(360),
        width: z.number().positive().max(1_000_000),
        height: z.number().positive().max(1_000_000),
      })
      .strict()
      .optional(),
  })
  .strict()
  .superRefine((map, ctx) => {
    const ids = [...map.areas, ...map.graves].map((item) => item.id);
    if (new Set(ids).size !== ids.length)
      ctx.addIssue({ code: 'custom', message: 'IDが重複しています' });
    if (map.graves.reduce((total, grave) => total + grave.people.length, 0) > 20_000)
      ctx.addIssue({ code: 'custom', message: '人物の件数が上限を超えています' });
  });
export type MapData = z.infer<typeof mapSchema>;
export type Box = z.infer<typeof box>;
export type SearchTarget = 'all' | 'grave' | 'person';
export const targets: { value: SearchTarget; label: string }[] = [
  { value: 'all', label: 'すべて' },
  { value: 'grave', label: '墓所名・家名' },
  { value: 'person', label: '人物の氏名' },
];
export function normalize(value: string): string {
  return value
    .normalize('NFKC')
    .toLowerCase()
    .replace(/\s/gu, '')
    .replace(/[ァ-ヶ]/gu, (character) => String.fromCharCode(character.charCodeAt(0) - 0x60));
}
export function search(map: MapData, query: string, target: SearchTarget): string[] {
  const key = normalize(query);
  if (!key) return [];
  return map.graves
    .filter(
      (grave) =>
        (target !== 'person' && normalize(grave.name).includes(key)) ||
        (target !== 'grave' && grave.people.some((person) => normalize(person).includes(key))),
    )
    .map((grave) => grave.id);
}
export function bounds(boxes: Box[]): { x: number; y: number; width: number; height: number } {
  if (!boxes.length) return { x: 0, y: 0, width: 100, height: 100 };
  const points = boxes.flatMap((item) => {
    const angle = (item.rotation * Math.PI) / 180;
    // Windows版と同じく図形の中心を回転中心とする。
    const cx = item.x + item.width / 2,
      cy = item.y + item.height / 2;
    return [
      [-1, -1],
      [-1, 1],
      [1, -1],
      [1, 1],
    ].map(([sx = 1, sy = 1]) => ({
      x:
        cx + ((sx * item.width) / 2) * Math.cos(angle) - ((sy * item.height) / 2) * Math.sin(angle),
      y:
        cy + ((sx * item.width) / 2) * Math.sin(angle) + ((sy * item.height) / 2) * Math.cos(angle),
    }));
  });
  const x = Math.min(...points.map((p) => p.x)),
    y = Math.min(...points.map((p) => p.y));
  return {
    x,
    y,
    width: Math.max(1, Math.max(...points.map((p) => p.x)) - x),
    height: Math.max(1, Math.max(...points.map((p) => p.y)) - y),
  };
}
