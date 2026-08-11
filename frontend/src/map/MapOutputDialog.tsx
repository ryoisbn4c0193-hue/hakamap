import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useState } from 'react';

type Props = {
  capture: (range: 'current' | 'selectedArea') => string | undefined;
  onClose: () => void;
  open: boolean;
};

function MapOutputDialog({ capture, onClose, open }: Props) {
  const [paper, setPaper] = useState<'A4' | 'A3'>('A4');
  const [orientation, setOrientation] = useState<'landscape' | 'portrait'>('landscape');
  const [range, setRange] = useState<'current' | 'selectedArea'>('current');
  const [preview, setPreview] = useState<string>();

  const close = () => {
    setPreview(undefined);
    onClose();
  };

  const render = async () => {
    const source = capture(range);
    if (source === undefined) {
      setPreview(undefined);
      return undefined;
    }
    const result = await createPageImage(source, paper, orientation);
    setPreview(result);
    return result;
  };

  return (
    <Dialog fullWidth maxWidth="md" onClose={close} open={open}>
      <DialogTitle>印刷・PDF・PNG出力</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="用紙"
            onChange={(event) => setPaper(event.target.value as 'A4' | 'A3')}
            select
            slotProps={{ select: { native: true } }}
            value={paper}
          >
            <option value="A4">A4</option>
            <option value="A3">A3</option>
          </TextField>
          <TextField
            label="向き"
            onChange={(event) => setOrientation(event.target.value as 'landscape' | 'portrait')}
            select
            slotProps={{ select: { native: true } }}
            value={orientation}
          >
            <option value="landscape">横</option>
            <option value="portrait">縦</option>
          </TextField>
          <TextField
            label="出力範囲"
            onChange={(event) => setRange(event.target.value as typeof range)}
            select
            slotProps={{ select: { native: true } }}
            value={range}
          >
            <option value="current">現在表示範囲</option>
            <option value="selectedArea">選択エリア</option>
          </TextField>
          <Button onClick={() => void render()}>プレビュー更新</Button>
          {preview === undefined ? (
            <Typography color="text.secondary">
              選択エリアを出力する場合は、先にエリア編集モードで対象を選択してください。
            </Typography>
          ) : (
            <img
              alt="出力プレビュー"
              src={preview}
              style={{ maxHeight: '45vh', maxWidth: '100%', objectFit: 'contain' }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={close}>キャンセル</Button>
        <Button
          onClick={() => {
            void render().then((image) => {
              if (image === undefined) return;
              const anchor = document.createElement('a');
              anchor.download = 'hakamap-map.png';
              anchor.href = image;
              anchor.click();
            });
          }}
        >
          PNG保存
        </Button>
        <Button
          onClick={() => {
            void render().then((image) => {
              if (image === undefined) return;
              const printWindow = window.open('', '_blank');
              if (printWindow === null) return;
              printWindow.document.write(createPrintPreviewHtml(image, paper, orientation));
              printWindow.document.close();
              printWindow.addEventListener('load', () => printWindow.print(), { once: true });
            });
          }}
          variant="contained"
        >
          印刷・PDF
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export function createPrintPreviewHtml(
  image: string,
  paper: 'A4' | 'A3',
  orientation: 'landscape' | 'portrait',
) {
  return `<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Hakamap 印刷プレビュー</title>
  <style>
    @page{size:${paper} ${orientation};margin:0}
    *{box-sizing:border-box}
    body{margin:0;background:#dff4fc;color:#162832;font-family:"Yu Gothic UI","Yu Gothic","Meiryo UI",Meiryo,sans-serif}
    .toolbar{align-items:center;background:#249bd3;color:#fff;display:flex;gap:8px;padding:10px 16px;position:sticky;top:0;z-index:1}
    .toolbar strong{margin-right:auto}
    button{background:#fff;border:0;border-radius:6px;color:#147fb5;cursor:pointer;font:inherit;font-weight:700;min-width:42px;padding:7px 12px}
    button:hover{background:#e7f6fc}
    #zoom-label{font-variant-numeric:tabular-nums;min-width:4.5em;text-align:center}
    .preview{min-height:calc(100vh - 56px);overflow:auto;padding:16px}
    img{display:block;height:auto;margin:0 auto;max-width:none;width:100%;box-shadow:0 8px 28px rgb(20 127 181 / 24%)}
    @media print{body{background:#fff}.toolbar{display:none}.preview{min-height:0;overflow:visible;padding:0}img{box-shadow:none;width:100%!important}}
  </style>
</head>
<body>
  <nav class="toolbar" aria-label="印刷プレビュー操作">
    <strong>Hakamap 印刷プレビュー</strong>
    <button id="zoom-out" type="button" aria-label="縮小">−</button>
    <span id="zoom-label">100%</span>
    <button id="zoom-in" type="button" aria-label="拡大">＋</button>
    <button id="zoom-fit" type="button">全体表示</button>
    <button type="button" onclick="window.print()">印刷</button>
  </nav>
  <main class="preview"><img id="preview-image" alt="Hakamap地図" src="${image}"></main>
  <script>
    (() => {
      const image = document.getElementById('preview-image');
      const label = document.getElementById('zoom-label');
      let zoom = 100;
      const applyZoom = () => {
        image.style.width = zoom + '%';
        label.textContent = zoom + '%';
      };
      document.getElementById('zoom-out').addEventListener('click', () => {
        zoom = Math.max(25, zoom - 25);
        applyZoom();
      });
      document.getElementById('zoom-in').addEventListener('click', () => {
        zoom = Math.min(400, zoom + 25);
        applyZoom();
      });
      document.getElementById('zoom-fit').addEventListener('click', () => {
        zoom = 100;
        applyZoom();
      });
    })();
  </script>
</body>
</html>`;
}

async function createPageImage(
  source: string,
  paper: 'A4' | 'A3',
  orientation: 'landscape' | 'portrait',
): Promise<string | undefined> {
  const landscape = paper === 'A4' ? [3508, 2480] : [4961, 3508];
  const [width, height] = orientation === 'landscape' ? landscape : [landscape[1], landscape[0]];
  const image = new Image();
  image.src = source;
  await image.decode();
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (context === null) return undefined;
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, width, height);
  const margin = 118;
  const scale = Math.min((width - margin * 2) / image.width, (height - margin * 2) / image.height);
  const drawWidth = image.width * scale;
  const drawHeight = image.height * scale;
  context.drawImage(
    image,
    (width - drawWidth) / 2,
    (height - drawHeight) / 2,
    drawWidth,
    drawHeight,
  );
  return canvas.toDataURL('image/png');
}

export default MapOutputDialog;
