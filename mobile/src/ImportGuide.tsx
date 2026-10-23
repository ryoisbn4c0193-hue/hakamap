import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { iphoneImportGuide as guide } from '../../shared/manuals/iphoneImport';

export function ImportGuide({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={styles.screen}>
        <View style={styles.header}>
          <Text style={styles.title}>使い方</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="使い方を閉じる"
            onPress={onClose}
            style={styles.close}
          >
            <Text style={styles.closeText}>閉じる</Text>
          </Pressable>
        </View>
        <ScrollView contentContainerStyle={styles.content}>
          <Text accessibilityRole="header" style={styles.heading}>
            {guide.title}
          </Text>
          <Text style={styles.body}>{guide.introduction}</Text>
          {guide.steps.map((step, index) => (
            <View style={styles.section} key={step.title}>
              <Text accessibilityRole="header" style={styles.sectionTitle}>
                {index + 1}. {step.title}
              </Text>
              {step.instructions.map((instruction) => (
                <Text style={styles.body} key={instruction}>
                  ・{instruction}
                </Text>
              ))}
            </View>
          ))}
          <View style={styles.section}>
            <Text accessibilityRole="header" style={styles.sectionTitle}>
              ご利用上の注意
            </Text>
            {guide.notes.map((note) => (
              <Text style={styles.body} key={note}>
                ・{note}
              </Text>
            ))}
          </View>
          <View style={styles.section}>
            <Text accessibilityRole="header" style={styles.sectionTitle}>
              うまく取り込めないとき
            </Text>
            {guide.troubleshooting.map((note) => (
              <Text style={styles.body} key={note}>
                ・{note}
              </Text>
            ))}
          </View>
        </ScrollView>
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
    justifyContent: 'space-between',
    alignItems: 'center',
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
  content: { padding: 16, gap: 16 },
  heading: { fontSize: 22, fontWeight: '700', color: '#162832' },
  section: { padding: 14, gap: 10, backgroundColor: 'white', borderRadius: 6 },
  sectionTitle: { fontSize: 18, fontWeight: '600', color: '#162832' },
  body: { fontSize: 16, lineHeight: 26, color: '#162832' },
});
