import { describe, expect, it, vi } from 'vitest';

import { subscribeFileSelectionActivity, withFileSelectionActivity } from './fileSelectionActivity';

describe('fileSelectionActivity', () => {
  it('ファイル選択要求の完了まで画面へ操作中を通知する', async () => {
    const listener = vi.fn();
    const unsubscribe = subscribeFileSelectionActivity(listener);
    let complete: (() => void) | undefined;
    const request = withFileSelectionActivity(
      () =>
        new Promise<void>((resolve) => {
          complete = resolve;
        }),
    );

    expect(listener).toHaveBeenLastCalledWith(true);
    complete?.();
    await request;
    expect(listener).toHaveBeenLastCalledWith(false);
    unsubscribe();
  });

  it('失敗した場合も操作中を解除する', async () => {
    const listener = vi.fn();
    const unsubscribe = subscribeFileSelectionActivity(listener);

    await expect(
      withFileSelectionActivity(() => Promise.reject(new Error('selection-failed'))),
    ).rejects.toThrow('selection-failed');
    expect(listener).toHaveBeenLastCalledWith(false);
    unsubscribe();
  });
});
