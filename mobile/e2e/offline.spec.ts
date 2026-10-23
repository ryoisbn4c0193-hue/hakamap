import { expect, test, type Page } from '@playwright/test';
import {
  archive,
  backgroundPath,
  backgroundProject,
  png,
  windowsProject,
} from '../src/archive-fixtures';
async function upload(page: Page, bytes: Uint8Array) {
  const chooser = page.waitForEvent('filechooser');
  await page.getByRole('button', { name: 'データを取り込む', exact: true }).click();
  await (
    await chooser
  ).setFiles({
    name: 'test.hakamap',
    mimeType: 'application/octet-stream',
    buffer: Buffer.from(bytes),
  });
}
test('取り込み・検索・詳細・再起動・保存設定・ガイドを通信オフで使える', async ({
  page,
  context,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('dialog', (dialog) => dialog.accept());
  await page.goto('./');
  await expect(page.getByText('オフライン利用の準備完了', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '更新を適用して再起動' })).not.toBeVisible();
  await upload(page, archive({ project: backgroundProject(), assets: { [backgroundPath]: png } }));
  await expect(page.getByText('山田家', { exact: true })).toBeVisible();
  await page.getByRole('switch', { name: '墓所名の表示' }).click();
  await expect(page.getByText('山田家', { exact: true })).not.toBeVisible();
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByText('オフライン利用の準備完了', { exact: true })).toBeVisible();
  await expect(page.getByRole('switch', { name: '墓所名の表示' })).not.toBeChecked();
  await page.getByRole('switch', { name: '墓所名の表示' }).click();
  await expect(page.getByText('山田家', { exact: true })).toBeVisible();
  await page.getByRole('textbox', { name: '検索する名前' }).fill('佐藤 太郎');
  await expect(page.getByText('検索結果：1件', { exact: true })).toBeVisible();
  // 赤い墓所の中心を押し、Webのポインター操作から詳細を開く。
  const grave = page.locator('rect[stroke="#D32F2F"]');
  await grave.tap({ force: true });
  await expect(page.getByText('佐藤 太郎', { exact: true })).toBeVisible();
  await expect(page.getByText('検索に一致', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '墓所情報を閉じる', exact: true }).click();
  await page.getByRole('button', { name: '使い方', exact: true }).click();
  await expect(page.getByText('WindowsからiPhoneへ', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: '使い方を閉じる', exact: true }).click();
  const cold = await context.newPage();
  await page.close();
  await cold.goto('./');
  await expect(cold.getByText('山田家', { exact: true })).toBeVisible();
  await expect(cold.getByRole('textbox', { name: '検索する名前' })).toHaveValue('');
  await expect(cold.locator('image')).toHaveAttribute('href', /^data:image\/png;base64,/);
  await expect(cold.getByText('オフライン利用の準備完了', { exact: true })).toBeVisible();
  await cold.screenshot({ path: test.info().outputPath('offline.png') });
  expect(errors).toEqual([]);
});
test('置き換えのキャンセルと破損入力で旧データを保持する', async ({ page }) => {
  let replace = false;
  page.on('dialog', (dialog) =>
    dialog.type() === 'confirm' && !replace ? dialog.dismiss() : dialog.accept(),
  );
  await page.goto('./');
  await upload(page, archive());
  await expect(page.getByText('山田家', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'データ管理', exact: true }).click();
  const project = windowsProject();
  project.graves[0]!.name = '更新後';
  await upload(page, archive({ project }));
  await page.getByRole('button', { name: '閉じる', exact: true }).click();
  await expect(page.getByText('山田家', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'データ管理', exact: true }).click();
  await upload(page, new Uint8Array([1, 2, 3]));
  await page.getByRole('button', { name: '閉じる', exact: true }).click();
  await expect(page.getByText('山田家', { exact: true })).toBeVisible();
  replace = true;
  await page.getByRole('button', { name: 'データ管理', exact: true }).click();
  await upload(page, archive({ project }));
  await page.getByRole('button', { name: '閉じる', exact: true }).click();
  await expect(page.getByText('更新後', { exact: true })).toBeVisible();
});
