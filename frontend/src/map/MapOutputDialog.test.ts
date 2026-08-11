import { describe, expect, it } from 'vitest';

import { createPrintPreviewHtml } from './MapOutputDialog';

describe('createPrintPreviewHtml', () => {
  it('タイトルと拡大縮小操作を持つ印刷HTMLを生成する', () => {
    const html = createPrintPreviewHtml('data:image/png;base64,example', 'A4', 'landscape');

    expect(html).toContain('<title>Hakamap 印刷プレビュー</title>');
    expect(html).toContain('aria-label="縮小"');
    expect(html).toContain('aria-label="拡大"');
    expect(html).toContain('全体表示');
    expect(html).toContain('window.print()');
    expect(html).toContain('@page{size:A4 landscape;margin:0}');
    expect(html).toContain('src="data:image/png;base64,example"');
  });
});
