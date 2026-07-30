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
              printWindow.document.write(
                `<style>@page{size:${paper} ${orientation};margin:0}body{margin:0}img{width:100%;display:block}</style><img alt="Hakamap地図" src="${image}">`,
              );
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
