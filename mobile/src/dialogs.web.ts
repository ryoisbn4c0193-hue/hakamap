export function notify(title: string, message: string) {
  window.alert(`${title}\n${message}`);
}
export async function confirmReplace(): Promise<boolean> {
  return window.confirm('データの置き換え\n現在のデータを置き換えます。よろしいですか？');
}
