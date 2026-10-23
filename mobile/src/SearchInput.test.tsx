// @vitest-environment jsdom
import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import SearchInput from './SearchInput';
afterEach(cleanup);
it('変換中は検索せず、確定時に確定文字を通知する', () => {
  const onText = vi.fn(async () => {});
  const view = render(<SearchInput resetKey="1" onText={onText} />);
  const input = view.getByLabelText('検索する名前');
  fireEvent.compositionStart(input);
  fireEvent.change(input, { target: { value: 'やまだ' } });
  expect(onText).not.toHaveBeenCalled();
  fireEvent.change(input, { target: { value: '山田' } });
  expect(onText).not.toHaveBeenCalled();
  fireEvent.compositionEnd(input);
  expect(onText).toHaveBeenLastCalledWith('山田');
});
it('通常の入力・解除を通知し、データ交換時に入力を初期化する', () => {
  const onText = vi.fn(async () => {});
  const view = render(<SearchInput resetKey="1" onText={onText} />);
  fireEvent.change(view.getByLabelText('検索する名前'), { target: { value: '佐藤' } });
  expect(onText).toHaveBeenLastCalledWith('佐藤');
  fireEvent.click(view.getByLabelText('検索を解除'));
  expect(onText).toHaveBeenLastCalledWith('');
  fireEvent.change(view.getByLabelText('検索する名前'), { target: { value: '田中' } });
  view.rerender(<SearchInput resetKey="2" onText={onText} />);
  expect((view.getByLabelText('検索する名前') as HTMLInputElement).value).toBe('');
});
