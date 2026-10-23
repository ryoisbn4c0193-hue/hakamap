import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { ArchiveError, type SavedMap } from './src/archive';
import { pickArchive } from './src/pickArchive';
import { notify, confirmReplace } from './src/dialogs';
import { OfflineStatus } from './src/OfflineStatus';
import { loadMap, loadNames, saveMap, saveNames } from './src/storage';
import { normalize, search, targets, type SearchTarget } from './src/model';
import { MapView } from './src/MapView';
import SearchInput from './src/SearchInput';
import { GraveDetails } from './src/GraveDetails';
import { ImportGuide } from './src/ImportGuide';

function Button({
  label,
  onPress,
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={[styles.button, disabled && { opacity: 0.5 }]}
    >
      <Text style={styles.buttonText}>{label}</Text>
    </Pressable>
  );
}
function Screen() {
  const [data, setData] = useState<SavedMap | null>(null),
    [ready, setReady] = useState(false),
    [busy, setBusy] = useState(false);
  const [names, setNames] = useState(true),
    [query, setQuery] = useState(''),
    [committed, setCommitted] = useState('');
  const [target, setTarget] = useState<SearchTarget>('all'),
    [selector, setSelector] = useState(false),
    [manage, setManage] = useState(false);
  const [fit, setFit] = useState(0),
    [zoom, setZoom] = useState(0),
    [loadFailed, setLoadFailed] = useState(false);
  const [guideOpen, setGuideOpen] = useState(false);
  const [selectedGraveId, setSelectedGraveId] = useState<string | null>(null);
  const selectedGrave = data?.map.graves.find((grave) => grave.id === selectedGraveId) ?? null;
  const importLock = useRef(false);
  const [inputVersion, setInputVersion] = useState(0);
  useEffect(() => {
    let active = true;
    Promise.all([loadMap(), loadNames()])
      .then(([map, show]) => {
        if (active) {
          setData(map);
          setNames(show);
        }
      })
      .catch(() => {
        if (active) {
          setLoadFailed(true);
          notify(
            '読み込みできません',
            '保存済みデータを読み込めませんでした。アプリを再起動してください。',
          );
        }
      })
      .finally(() => {
        if (active) setReady(true);
      });
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    const timer = setTimeout(() => setCommitted(query), 250);
    return () => clearTimeout(timer);
  }, [query]);
  const matches = useMemo(
    () => (data ? search(data.map, committed, target) : []),
    [data, committed, target],
  );
  async function importMap() {
    if (importLock.current) return;
    importLock.current = true;
    setBusy(true);
    try {
      const candidate = await pickArchive();
      if (!candidate) return;
      if (data && !(await confirmReplace())) return;
      await saveMap(candidate);
      setSelectedGraveId(null);
      setData(candidate);
      setInputVersion((value) => value + 1);
      setQuery('');
      setCommitted('');
      setFit((v) => v + 1);
      notify('取り込み完了', '配置図を更新しました。');
    } catch (error) {
      notify(
        '取り込みできません',
        `${error instanceof ArchiveError ? error.message : 'ファイルの形式・容量・空き容量を確認してください。'}保存済みデータは変更していません。`,
      );
    } finally {
      importLock.current = false;
      setBusy(false);
    }
  }
  async function toggleNames(value: boolean) {
    try {
      await saveNames(value);
      setNames(value);
    } catch {
      notify('保存できません', '表示設定を保存できませんでした。');
    }
  }
  if (!ready)
    return (
      <View style={styles.center}>
        <ActivityIndicator accessibilityLabel="読み込み中" />
      </View>
    );
  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar style="light" />
      <View style={styles.header}>
        <Text style={styles.title}>Hakamap</Text>
        <View style={styles.headerActions}>
          <Button label="使い方" onPress={() => setGuideOpen(true)} />
          <Button label="データ管理" onPress={() => setManage(true)} />
        </View>
      </View>
      <OfflineStatus busy={busy} />
      <KeyboardAvoidingView
        style={styles.body}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        {data ? (
          <>
            <View style={styles.panel}>
              <View style={styles.row}>
                <Button
                  label={`${targets.find((t) => t.value === target)?.label} ▾`}
                  onPress={() => setSelector(true)}
                />
                <View style={{ flex: 1, height: 46 }}>
                  <SearchInput
                    resetKey={`${data.createdAt}:${inputVersion}`}
                    onText={async (value) => {
                      setQuery(value);
                    }}
                    dom={{
                      // Expo GoのDomWebViewで発生するネイティブビュー参照エラーを避ける。
                      useExpoDOMWebView: false,
                      style: { height: 46 },
                      scrollEnabled: false,
                    }}
                  />
                </View>
              </View>
              <Text style={styles.small}>
                {normalize(committed)
                  ? matches.length
                    ? `検索結果：${matches.length}件`
                    : '該当するお墓はありません'
                  : '墓地配置図'}
              </Text>
            </View>
            <View style={styles.toolbar}>
              <Text>墓所名</Text>
              <Switch
                accessibilityLabel="墓所名の表示"
                value={names}
                onValueChange={(value) => {
                  void toggleNames(value);
                }}
                trackColor={{ true: '#249BD3' }}
              />
              <Button label="全体表示" onPress={() => setFit((v) => v + 1)} />
              <Button label="−" onPress={() => setZoom((v) => Math.max(-20, v - 1))} />
              <Button label="＋" onPress={() => setZoom((v) => Math.min(20, v + 1))} />
            </View>
            <MapView
              data={data}
              matches={matches}
              showNames={names}
              fitRequest={fit}
              zoom={zoom}
              onGravePress={setSelectedGraveId}
            />
            <Text style={styles.footer}>
              データ作成：{new Date(data.createdAt).toLocaleString('ja-JP')}
            </Text>
          </>
        ) : (
          <View style={styles.center}>
            <Text style={styles.heading}>
              {loadFailed ? '読み込みに失敗しました' : '配置図を取り込んでください'}
            </Text>
            <Text style={styles.help}>
              Windows版でエクスポートした.hakamapファイルを選択します。
            </Text>
            <Button
              label="データを取り込む"
              disabled={busy || loadFailed}
              onPress={() => {
                void importMap();
              }}
            />
          </View>
        )}
      </KeyboardAvoidingView>
      <ImportGuide visible={guideOpen} onClose={() => setGuideOpen(false)} />
      <GraveDetails
        grave={selectedGrave}
        query={committed}
        target={target}
        onClose={() => setSelectedGraveId(null)}
      />
      <Modal
        visible={selector}
        transparent
        animationType="fade"
        onRequestClose={() => setSelector(false)}
      >
        <View style={styles.overlay}>
          <View style={styles.dialog}>
            <Text style={styles.heading}>検索対象</Text>
            {targets.map((option) => (
              <Pressable
                key={option.value}
                accessibilityRole="radio"
                accessibilityLabel={option.label}
                accessibilityState={{ checked: target === option.value }}
                style={({ pressed }) => [
                  styles.searchOption,
                  target === option.value && styles.searchOptionSelected,
                  pressed && { opacity: 0.7 },
                ]}
                onPress={() => {
                  setTarget(option.value);
                  setSelector(false);
                }}
              >
                <Text style={styles.searchOptionText}>
                  {target === option.value ? '●' : '○'} {option.label}
                </Text>
              </Pressable>
            ))}
            <View style={styles.searchActions}>
              <Pressable
                accessibilityRole="button"
                onPress={() => setSelector(false)}
                style={({ pressed }) => [styles.searchClose, pressed && { opacity: 0.8 }]}
              >
                <Text style={styles.searchCloseText}>閉じる</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
      <Modal
        visible={manage}
        animationType="slide"
        onRequestClose={() => {
          if (!busy) setManage(false);
        }}
      >
        <SafeAreaView style={styles.screen}>
          <View style={styles.header}>
            <Text style={styles.title}>データ管理</Text>
            <Button label="閉じる" disabled={busy} onPress={() => setManage(false)} />
          </View>
          <ScrollView contentContainerStyle={styles.panel}>
            <Text style={styles.heading}>{data?.map.projectName ?? 'データ未登録'}</Text>
            {data && (
              <Text style={styles.help}>
                データ作成：{new Date(data.createdAt).toLocaleString('ja-JP')}
              </Text>
            )}
            <Button
              label="データを取り込む"
              disabled={busy || loadFailed}
              onPress={() => {
                void importMap();
              }}
            />
            {busy && <ActivityIndicator accessibilityLabel="取り込み中" />}
            <Text style={styles.help}>
              LINEで受け取ったファイルは、先に「ファイル」に保存してください。
            </Text>
          </ScrollView>
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}
export default function App() {
  return (
    <SafeAreaProvider>
      <Screen />
    </SafeAreaProvider>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#DFF4FC' },
  body: { flex: 1 },
  header: {
    backgroundColor: '#249BD3',
    padding: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerActions: { flexDirection: 'row', gap: 6 },
  title: { color: 'white', fontWeight: '700', fontSize: 23 },
  panel: { padding: 12, backgroundColor: 'white', gap: 12 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  input: {
    flex: 1,
    minHeight: 46,
    borderWidth: 1,
    borderColor: '#A5BDC9',
    borderRadius: 6,
    paddingHorizontal: 10,
    fontSize: 17,
    color: '#162832',
  },
  small: { fontSize: 13, color: '#162832' },
  button: {
    minHeight: 44,
    justifyContent: 'center',
    paddingHorizontal: 10,
    borderRadius: 5,
    backgroundColor: 'white',
  },
  buttonText: { color: '#147FB5', fontSize: 15, fontWeight: '600' },
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    backgroundColor: 'white',
    marginTop: 1,
    padding: 4,
  },
  center: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    gap: 16,
    backgroundColor: '#DFF4FC',
  },
  heading: { fontSize: 20, fontWeight: '600', color: '#162832' },
  help: { fontSize: 15, lineHeight: 24, color: '#162832' },
  footer: { padding: 10, fontSize: 12, color: '#162832', backgroundColor: 'white' },
  overlay: { flex: 1, backgroundColor: '#00000055', justifyContent: 'center', padding: 30 },
  dialog: { backgroundColor: 'white', padding: 20, borderRadius: 8, gap: 8 },
  searchOption: {
    minHeight: 48,
    justifyContent: 'center',
    paddingHorizontal: 12,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#D4E0E7',
    backgroundColor: 'white',
  },
  searchOptionSelected: { backgroundColor: '#EAF6FC', borderColor: '#147FB5' },
  searchOptionText: { color: '#162832', fontSize: 16 },
  searchActions: { borderTopWidth: 1, borderTopColor: '#D4E0E7', marginTop: 8, paddingTop: 12 },
  searchClose: {
    minHeight: 44,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#147FB5',
    borderRadius: 6,
  },
  searchCloseText: { color: 'white', fontSize: 16, fontWeight: '600' },
});
