// サブディレクトリへの配布でも、JSとアセットの参照先を一致させる。
module.exports = ({ config }) => {
  const baseUrl = process.env.HAKAMAP_BASE_PATH ?? '';
  if (baseUrl && !/^\/(?:[A-Za-z0-9_-]+\/?)+$/.test(baseUrl)) {
    throw new Error('HAKAMAP_BASE_PATHには先頭が/の配布パスを指定してください。');
  }
  return { ...config, experiments: { ...config.experiments, baseUrl: baseUrl.replace(/\/$/, '') } };
};
