import { Alert } from 'react-native';
export const notify = (title: string, message: string) => Alert.alert(title, message);
export function confirmReplace(): Promise<boolean> {
  return new Promise((resolve) =>
    Alert.alert(
      'データの置き換え',
      '現在のデータを置き換えます。よろしいですか？',
      [
        { text: 'キャンセル', style: 'cancel', onPress: () => resolve(false) },
        { text: '置き換える', onPress: () => resolve(true) },
      ],
      { cancelable: false },
    ),
  );
}
