import type { MapData } from './model';
export const projectId = '11111111-1111-4111-8111-111111111111';
const first = '22222222-2222-4222-8222-222222222222';
const second = '33333333-3333-4333-8333-333333333333';
export const map: MapData = {
  schemaVersion: 1,
  projectId,
  projectName: '検証用',
  areas: [],
  graves: [
    {
      id: first,
      x: 0,
      y: 0,
      width: 20,
      height: 10,
      rotation: 0,
      name: '山田家',
      people: ['佐藤 太郎', '佐藤 次郎'],
    },
    {
      id: second,
      x: 30,
      y: 10,
      width: 20,
      height: 10,
      rotation: 90,
      name: '佐藤家',
      people: ['ヤマダ　ハナコ'],
    },
  ],
};
