import { FlatList, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { normalize, type MapData, type SearchTarget } from './model';

type Grave = MapData['graves'][number];
export function GraveDetails({
  grave,
  query,
  target,
  onClose,
}: {
  grave: Grave | null;
  query: string;
  target: SearchTarget;
  onClose: () => void;
}) {
  if (!grave) return null;
  const key = target !== 'grave' ? normalize(query) : '';
  return (
    <Modal visible animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.screen}>
        <View style={styles.header}>
          <Text style={styles.title}>墓所情報</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="墓所情報を閉じる"
            onPress={onClose}
            style={styles.close}
          >
            <Text style={styles.closeText}>閉じる</Text>
          </Pressable>
        </View>
        <FlatList
          data={grave.people}
          keyExtractor={(_name, index) => String(index)}
          contentContainerStyle={styles.content}
          ListHeaderComponent={
            <View style={styles.section}>
              <Text style={styles.label}>墓所名・家名</Text>
              <Text selectable style={styles.name}>
                {grave.name || '墓所名は未登録です'}
              </Text>
              <Text style={styles.heading}>登録された人物の氏名（{grave.people.length}人）</Text>
            </View>
          }
          ListEmptyComponent={<Text style={styles.empty}>人物の氏名は登録されていません。</Text>}
          renderItem={({ item: name }) => {
            const matched = key.length > 0 && normalize(name).includes(key);
            return (
              <View style={[styles.person, matched && styles.matched]}>
                <Text selectable style={styles.personName}>
                  {name}
                </Text>
                {matched && <Text style={styles.matchLabel}>検索に一致</Text>}
              </View>
            );
          }}
        />
      </SafeAreaView>
    </Modal>
  );
}
const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#DFF4FC' },
  header: {
    padding: 12,
    backgroundColor: '#249BD3',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: { fontSize: 23, fontWeight: '700', color: 'white' },
  close: {
    minHeight: 44,
    paddingHorizontal: 16,
    justifyContent: 'center',
    backgroundColor: 'white',
    borderRadius: 5,
  },
  closeText: { fontSize: 15, fontWeight: '600', color: '#147FB5' },
  content: { padding: 16 },
  section: { gap: 12, paddingBottom: 16 },
  label: { fontSize: 14, color: '#162832' },
  name: { fontSize: 22, fontWeight: '600', color: '#162832' },
  heading: { fontSize: 17, fontWeight: '600', color: '#162832', marginTop: 12 },
  person: {
    padding: 14,
    marginBottom: 8,
    borderRadius: 6,
    backgroundColor: 'white',
    borderWidth: 1,
    borderColor: '#A5BDC9',
    gap: 6,
  },
  personName: { fontSize: 18, color: '#162832' },
  matched: { borderColor: '#D32F2F', backgroundColor: '#FFF1F1' },
  matchLabel: { fontSize: 13, fontWeight: '600', color: '#B3261E' },
  empty: { padding: 14, backgroundColor: 'white', color: '#162832', fontSize: 16 },
});
