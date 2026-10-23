import path from 'node:path';
import metroConfig from 'expo/metro-config';

const config = metroConfig.getDefaultConfig(import.meta.dirname);
// 両アプリへ同梱するマニュアルだけを追加の監視対象とする。
config.watchFolders = [...config.watchFolders, path.resolve(import.meta.dirname, '../shared')];
export default config;
