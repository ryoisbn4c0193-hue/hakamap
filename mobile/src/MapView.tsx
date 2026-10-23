import { useEffect, useMemo, useRef, useState, type ComponentRef } from 'react';
import { PanResponder, View, StyleSheet } from 'react-native';
import Svg, { G, Rect, Text, Image } from 'react-native-svg';
import { bounds, type Box } from './model';
import type { SavedMap } from './archive';
import { hitGrave, MapTap, type Camera } from './mapInteraction';

export function MapView({
  data,
  matches,
  showNames,
  fitRequest,
  zoom,
  onGravePress,
}: {
  data: SavedMap;
  matches: string[];
  showNames: boolean;
  fitRequest: number;
  zoom: number;
  onGravePress: (id: string) => void;
}) {
  const canvasRef = useRef<ComponentRef<typeof View>>(null);
  const canvasOrigin = useRef({ x: 0, y: 0 });
  const tap = useRef(new MapTap());
  const latest = useRef({ data, onGravePress });
  latest.current = { data, onGravePress };
  const [size, setSize] = useState({ width: 1, height: 1 });
  const [camera, setCamera] = useState<Camera>({ x: 0, y: 0, scale: 1 });
  const current = useRef(camera);
  current.current = camera;
  const start = useRef<Camera>(camera);
  const dragOffset = useRef({ x: 0, y: 0 });
  const touchStart = useRef<{ x: number; y: number; distance: number } | null>(null);
  const selected = useMemo(() => new Set(matches), [matches]);
  const lastFit = useRef<{ data: SavedMap; request: number } | null>(null);
  useEffect(() => {
    if (size.width <= 40 || size.height <= 40) return;
    const explicitFit = lastFit.current?.data !== data || lastFit.current.request !== fitRequest;
    lastFit.current = { data, request: fitRequest };
    // 全体表示の明示操作がなければ、0件・検索解除時はカメラを維持する。
    if (!explicitFit && !matches.length) return;
    const boxes: Box[] = explicitFit
      ? [...data.map.areas, ...data.map.graves]
      : data.map.graves.filter((grave) => selected.has(grave.id));
    if (explicitFit && data.map.background) {
      const bg = data.map.background,
        angle = (bg.rotation * Math.PI) / 180;
      boxes.push({
        ...bg,
        id: data.map.projectId,
        x:
          bg.x +
          (bg.width / 2) * Math.cos(angle) -
          (bg.height / 2) * Math.sin(angle) -
          bg.width / 2,
        y:
          bg.y +
          (bg.width / 2) * Math.sin(angle) +
          (bg.height / 2) * Math.cos(angle) -
          bg.height / 2,
      });
    }
    const result = bounds(boxes);
    const scale = Math.min((size.width - 40) / result.width, (size.height - 40) / result.height);
    setCamera({
      x: size.width / 2 - (result.x + result.width / 2) * scale,
      y: size.height / 2 - (result.y + result.height / 2) * scale,
      scale,
    });
  }, [data, fitRequest, matches, selected, size]);
  const previousZoom = useRef(zoom);
  useEffect(() => {
    const factor = 1.25 ** (zoom - previousZoom.current);
    previousZoom.current = zoom;
    setCamera((old) => ({
      scale: old.scale * factor,
      x: size.width / 2 + (old.x - size.width / 2) * factor,
      y: size.height / 2 + (old.y - size.height / 2) * factor,
    }));
  }, [zoom, size]);
  const responder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: () => true,
        onPanResponderGrant: (event) => {
          tap.current.begin(
            { x: event.nativeEvent.pageX, y: event.nativeEvent.pageY },
            event.nativeEvent.touches.length,
          );
          canvasRef.current?.measureInWindow((x, y) => {
            canvasOrigin.current = { x, y };
          });
          start.current = current.current;
          touchStart.current = null;
          dragOffset.current = { x: 0, y: 0 };
        },
        onPanResponderStart: (event) => {
          tap.current.move(
            { x: event.nativeEvent.pageX, y: event.nativeEvent.pageY },
            event.nativeEvent.touches.length,
          );
        },
        onPanResponderMove: (event, gesture) => {
          tap.current.move(
            { x: event.nativeEvent.pageX, y: event.nativeEvent.pageY },
            event.nativeEvent.touches.length,
          );
          const touches = event.nativeEvent.touches;
          const a = touches[0],
            b = touches[1];
          if (a && b) {
            const x = (a.pageX + b.pageX) / 2 - canvasOrigin.current.x,
              y = (a.pageY + b.pageY) / 2 - canvasOrigin.current.y;
            const distance = Math.hypot(a.pageX - b.pageX, a.pageY - b.pageY);
            if (!touchStart.current) {
              touchStart.current = { x, y, distance };
              start.current = current.current;
              return;
            }
            const initial = touchStart.current;
            const factor = Math.max(0.1, Math.min(10, distance / Math.max(1, initial.distance)));
            setCamera({
              scale: start.current.scale * factor,
              x: x - (initial.x - start.current.x) * factor,
              y: y - (initial.y - start.current.y) * factor,
            });
          } else if (touchStart.current) {
            start.current = current.current;
            touchStart.current = null;
            dragOffset.current = { x: gesture.dx, y: gesture.dy };
          } else
            setCamera({
              ...start.current,
              x: start.current.x + gesture.dx - dragOffset.current.x,
              y: start.current.y + gesture.dy - dragOffset.current.y,
            });
        },
        onPanResponderRelease: (event) => {
          const point = { x: event.nativeEvent.pageX, y: event.nativeEvent.pageY };
          if (!tap.current.end(point)) return;
          const camera = current.current;
          const source = latest.current.data;
          canvasRef.current?.measureInWindow((x, y) => {
            // 非同期の計測中に地図データが切り替わった場合は古い墓所を開かない。
            if (source !== latest.current.data) return;
            const id = hitGrave(source.map.graves, { x: point.x - x, y: point.y - y }, camera);
            if (id) latest.current.onGravePress(id);
          });
        },
        onPanResponderTerminate: () => tap.current.cancel(),
      }),
    [],
  );
  return (
    <View
      ref={canvasRef}
      style={styles.canvas}
      onLayout={(event) => setSize(event.nativeEvent.layout)}
      {...responder.panHandlers}
      accessibilityLabel="墓地配置図。お墓をタップすると墓所情報が開きます。指で移動、2本指で拡大・縮小できます"
    >
      <Svg width="100%" height="100%">
        <G transform={`translate(${camera.x},${camera.y}) scale(${camera.scale})`}>
          {data.map.background && (
            <Image
              href={data.backgroundUri}
              preserveAspectRatio="none"
              x={data.map.background.x}
              y={data.map.background.y}
              width={data.map.background.width}
              height={data.map.background.height}
              transform={`rotate(${data.map.background.rotation},${data.map.background.x},${data.map.background.y})`}
            />
          )}
          {data.map.areas.map((area) => (
            <G
              key={area.id}
              rotation={area.rotation}
              originX={area.x + area.width / 2}
              originY={area.y + area.height / 2}
            >
              <Rect
                x={area.x}
                y={area.y}
                width={area.width}
                height={area.height}
                fill={area.color}
                fillOpacity={0.12}
                stroke={area.color}
                strokeWidth={1 / camera.scale}
              />
              <Text
                x={area.x + 4}
                y={area.y + 14 / camera.scale}
                fontSize={12 / camera.scale}
                fill="#162832"
              >
                {area.name}
              </Text>
            </G>
          ))}
          {data.map.graves.map((grave) => (
            <G
              key={grave.id}
              rotation={grave.rotation}
              originX={grave.x + grave.width / 2}
              originY={grave.y + grave.height / 2}
            >
              <Rect
                x={grave.x}
                y={grave.y}
                width={grave.width}
                height={grave.height}
                fill={selected.has(grave.id) ? '#FADADD' : '#FFFFFF'}
                fillOpacity={0.85}
                stroke={selected.has(grave.id) ? '#D32F2F' : '#162832'}
                strokeWidth={(selected.has(grave.id) ? 3 : 1) / camera.scale}
              />
              {showNames && (
                <Text
                  x={grave.x + grave.width / 2}
                  y={grave.y + grave.height / 2}
                  textAnchor="middle"
                  alignmentBaseline="central"
                  fontSize={Math.min(
                    grave.height * 0.35,
                    grave.width / Math.max(2, Array.from(grave.name).length),
                  )}
                  fill="#162832"
                >
                  {grave.name}
                </Text>
              )}
            </G>
          ))}
        </G>
      </Svg>
    </View>
  );
}
const styles = StyleSheet.create({
  canvas: { flex: 1, backgroundColor: '#DFF4FC', overflow: 'hidden' },
});
