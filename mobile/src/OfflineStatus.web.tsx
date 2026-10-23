import { useEffect, useRef, useState } from 'react';

export function OfflineStatus({ busy }: { busy: boolean }) {
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const [waiting, setWaiting] = useState<ServiceWorker | null>(null);
  const applying = useRef(false);
  useEffect(() => {
    let active = true;
    if (!('serviceWorker' in navigator) || !window.isSecureContext) {
      setFailed(true);
      return;
    }
    const reload = () => {
      if (applying.current) window.location.reload();
    };
    navigator.serviceWorker.addEventListener('controllerchange', reload);
    const cleanups: (() => void)[] = [];
    void navigator.serviceWorker
      .register('./sw.js', { updateViaCache: 'none' })
      .then(async (registration) => {
        const inspect = () => {
          if (active) setWaiting(navigator.serviceWorker.controller ? registration.waiting : null);
        };
        const watch = () => {
          const worker = registration.installing;
          if (!worker) return;
          const changed = () => {
            inspect();
            if (active && worker.state === 'redundant' && !registration.active) setFailed(true);
          };
          worker.addEventListener('statechange', changed);
          cleanups.push(() => worker.removeEventListener('statechange', changed));
        };
        inspect();
        watch();
        registration.addEventListener('updatefound', watch);
        cleanups.push(() => registration.removeEventListener('updatefound', watch));
        await navigator.serviceWorker.ready;
        if (active) setReady(true);
      })
      .catch(() => {
        if (active) {
          if (navigator.serviceWorker.controller) setReady(true);
          else setFailed(true);
        }
      });
    return () => {
      active = false;
      cleanups.forEach((cleanup) => cleanup());
      navigator.serviceWorker.removeEventListener('controllerchange', reload);
    };
  }, []);
  const installed =
    window.matchMedia('(display-mode: standalone)').matches ||
    Boolean((navigator as Navigator & { standalone?: boolean }).standalone);
  return (
    <div
      role="status"
      style={{ padding: '7px 12px', fontSize: 12, color: '#162832', background: '#EAF6FC' }}
    >
      <div>
        {failed
          ? 'オフライン利用の準備に失敗しました。通信できる状態で開き直してください。'
          : ready
            ? 'オフライン利用の準備完了'
            : 'オフライン利用の準備中…'}
      </div>
      {!installed && <div>Safariの共有メニューから「ホーム画面に追加」して使えます。</div>}
      {waiting && (
        <button
          disabled={busy}
          onClick={() => {
            applying.current = true;
            waiting.postMessage({ type: 'ACTIVATE_UPDATE' });
          }}
        >
          更新を適用して再起動
        </button>
      )}
    </div>
  );
}
