// @vitest-environment jsdom
import type { ReactNode } from 'react';
import type {
  GestureResponderEvent,
  PanResponderCallbacks,
  PanResponderGestureState,
} from 'react-native';
import { act, cleanup, render } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
const handlers = vi.hoisted(() => ({ current: null as PanResponderCallbacks | null }));
vi.mock('react-native', async () => {
  const { forwardRef, useEffect, useImperativeHandle } = await import('react');
  const View = forwardRef(function TestView(
    { children, onLayout }: { children: ReactNode; onLayout: (event: unknown) => void },
    ref,
  ) {
    useImperativeHandle(ref, () => ({
      measureInWindow: (callback: (x: number, y: number) => void) => callback(30, 40),
    }));
    useEffect(() => {
      onLayout({ nativeEvent: { layout: { width: 240, height: 140 } } });
    }, []);
    return <div>{children}</div>;
  });
  return {
    View,
    StyleSheet: { create: <T,>(styles: T) => styles },
    PanResponder: {
      create: (callbacks: PanResponderCallbacks) => {
        handlers.current = callbacks;
        return { panHandlers: {} };
      },
    },
  };
});
vi.mock('react-native-svg', () => ({
  default: () => null,
  G: () => null,
  Rect: () => null,
  Text: () => null,
  Image: () => null,
}));
import { MapView } from './MapView';
import { map } from './fixtures';
const data = { map, createdAt: '2026-10-23T01:00:00.000Z' };
function event(x: number, y: number, touches = 1): GestureResponderEvent {
  return {
    nativeEvent: {
      pageX: x,
      pageY: y,
      touches: Array.from({ length: touches }, () => ({ pageX: x, pageY: y })),
    },
  } as unknown as GestureResponderEvent;
}
const gesture: PanResponderGestureState = {
  stateID: 1,
  moveX: 0,
  moveY: 0,
  x0: 0,
  y0: 0,
  dx: 0,
  dy: 0,
  vx: 0,
  vy: 0,
  numberActiveTouches: 1,
};
afterEach(cleanup);
it('実際のrelease処理が表示倍率とキャンバス位置を考慮して回転墓所を開く', () => {
  const onGravePress = vi.fn();
  render(
    <MapView
      data={data}
      matches={[]}
      showNames
      fitRequest={0}
      zoom={0}
      onGravePress={onGravePress}
    />,
  );
  // 全体表示の倍率4、カメラ(30,20)、キャンバス原点(30,40)。回転墓所の中心は地図(40,15)。
  act(() => {
    handlers.current!.onPanResponderGrant!(event(220, 120), gesture);
    handlers.current!.onPanResponderRelease!(event(220, 120, 0), gesture);
  });
  expect(onGravePress).toHaveBeenCalledWith(map.graves[1]!.id);
});
it('描画更新後も最新の選択コールバックを使う', () => {
  const oldCallback = vi.fn(),
    newCallback = vi.fn();
  const view = render(
    <MapView
      data={data}
      matches={[]}
      showNames
      fitRequest={0}
      zoom={0}
      onGravePress={oldCallback}
    />,
  );
  view.rerender(
    <MapView
      data={data}
      matches={[]}
      showNames={false}
      fitRequest={0}
      zoom={0}
      onGravePress={newCallback}
    />,
  );
  act(() => {
    handlers.current!.onPanResponderGrant!(event(100, 80), gesture);
    handlers.current!.onPanResponderRelease!(event(100, 80, 0), gesture);
  });
  expect(newCallback).toHaveBeenCalledWith(map.graves[0]!.id);
  expect(oldCallback).not.toHaveBeenCalled();
});
it('2本指が触れただけの操作でも詳細を開かない', () => {
  const onGravePress = vi.fn();
  render(
    <MapView
      data={data}
      matches={[]}
      showNames
      fitRequest={0}
      zoom={0}
      onGravePress={onGravePress}
    />,
  );
  act(() => {
    handlers.current!.onPanResponderGrant!(event(100, 80), gesture);
    handlers.current!.onPanResponderStart!(event(100, 80, 2), gesture);
    handlers.current!.onPanResponderRelease!(event(100, 80, 0), gesture);
  });
  expect(onGravePress).not.toHaveBeenCalled();
});
