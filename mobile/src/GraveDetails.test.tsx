// @vitest-environment jsdom
import type { ReactNode } from 'react';
import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
vi.mock('react-native', () => ({
  StyleSheet: { create: <T,>(styles: T) => styles },
  Modal: ({ children }: { children: ReactNode }) => <div role="dialog">{children}</div>,
  View: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  Text: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  Pressable: ({ children, onPress }: { children: ReactNode; onPress: () => void }) => (
    <button onClick={onPress}>{children}</button>
  ),
  FlatList: ({
    data,
    renderItem,
    ListHeaderComponent,
    ListEmptyComponent,
  }: {
    data: string[];
    renderItem: (props: { item: string }) => ReactNode;
    ListHeaderComponent: ReactNode;
    ListEmptyComponent: ReactNode;
  }) => (
    <div>
      {ListHeaderComponent}
      {data.length
        ? data.map((item, index) => <div key={index}>{renderItem({ item })}</div>)
        : ListEmptyComponent}
    </div>
  ),
}));
vi.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));
import { GraveDetails } from './GraveDetails';
import { map } from './fixtures';
afterEach(cleanup);
it('墓所と人物氏名を表示して、人名検索の一致を確認できる', () => {
  const onClose = vi.fn();
  const view = render(
    <GraveDetails grave={map.graves[0]!} query="佐藤 太郎" target="person" onClose={onClose} />,
  );
  expect(view.getByText('山田家')).toBeTruthy();
  expect(view.getByText('佐藤 太郎')).toBeTruthy();
  expect(view.getByText('佐藤 次郎')).toBeTruthy();
  expect(view.getAllByText('検索に一致')).toHaveLength(1);
  fireEvent.click(view.getByText('閉じる'));
  expect(onClose).toHaveBeenCalledOnce();
});
it('墓所名だけを検索しているとき人物氏名を一致扱いにしない', () => {
  const view = render(
    <GraveDetails grave={map.graves[0]!} query="佐藤" target="grave" onClose={() => {}} />,
  );
  expect(view.queryByText('検索に一致')).toBeNull();
});
it('名前や人物が未登録の墓所を表示できる', () => {
  const view = render(
    <GraveDetails
      grave={{ ...map.graves[0]!, name: '', people: [] }}
      query=""
      target="all"
      onClose={() => {}}
    />,
  );
  expect(view.getByText('墓所名は未登録です')).toBeTruthy();
  expect(view.getByText('人物の氏名は登録されていません。')).toBeTruthy();
});
it('選択されていないときは画面を出さない', () => {
  const view = render(<GraveDetails grave={null} query="" target="all" onClose={() => {}} />);
  expect(view.queryByRole('dialog')).toBeNull();
});
