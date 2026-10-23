# Windows配布・実機検証

## 前提

- Windows 11
- Java 21 JDK（`java`、`jlink`、`jpackage`を含む）
- Maven Wrapperとフロントエンド依存を取得済みの作業ツリー
- EXE作成に必要なWiX Toolset

Windows用EXEはWindows上だけで作成する。Linuxからのクロスビルド結果をリリースへ使用しない。

## テスト配布版の更新

- GitHub Actionsは既存配布版`0.1.10`より大きい`0.1.<10 + ワークフロー実行番号>`を
  自動採番する。手動実行時もバージョン欄が空欄なら自動採番する。
- 同じ実行の再試行では同じバージョンになる。異なる改修を配布するときは新しい実行で生成する。
- バージョンを手動指定する場合は、インストール済みの版より大きい値を指定する。
- 新版への更新はHakamapを画面内の終了操作で終了してからインストーラーを実行する。
  `--win-upgrade-uuid`を維持し、既存インストールの更新として扱う。
- 同じバージョンのインストーラーを実行しても新版への更新にはならない。
  アンインストールせずに旧版から新版へ更新でき、利用者データが保持されることをWindows実機で確認する。

## ビルド

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\packaging\windows\build.ps1 -Version 0.1.0
```

`packaging/out/Hakamap-<version>.exe`と、SHA-256を含む`manifest.json`が生成される。
`packaging/out`と`packaging/work`はGitへ追加しない。
アイコン未指定時は`assets/hakamap-icon.png`から16、24、32、48、64、128、256pxを含む
正式ICOを生成する。検証などで別アイコンを使う場合は
`-IconPath C:\path\hakamap.ico`で1か所だけ差し替える。

## 実機検証

最初に検証記録の雛形を生成する。

```powershell
.\packaging\windows\verify-installation.ps1 `
  -Installer .\packaging\out\Hakamap-0.1.0.exe `
  -EnvironmentClass minimum
```

次の項目をWindows最低環境と推奨環境で実施し、生成されたJSONの`null`と`not-run`を
実測値へ更新する。未実施項目を合格として記録しない。

1. 管理者権限のないユーザーでインストールする。
2. デスクトップとスタートメニューの両方から起動する。
3. 初回起動が5秒以内でブラウザ表示まで進むことを3回測定する。
4. 二重起動でバックエンドが増えず、既存画面が再表示されることを確認する。
5. ブラウザを閉じ、ショートカットから再表示する。
6. 画面内終了でプロセスと実行時ファイルが終了・清掃されることを確認する。
7. 1366×768、1920×1080、表示倍率100%と150%で主要画面を確認する。
8. インターネット切断中に主要E2E、印刷、エクスポート、インポートを実施する。
9. 新版を上書きインストールし、Project、Catalog、Recovery、ログが維持されることを確認する。
10. アンインストール後も利用者データが残り、再インストール後に再利用できることを確認する。

## 性能・障害試験

`docs/design/test-strategy.md`の条件と測定回数に従い、最低・推奨環境で以下を記録する。

- 起動、Project読込、選択・移動・パネル表示、検索、手動保存の中央値と最大値
- Java、ブラウザ、GPUを分けたメモリ使用量
- 5,000墓所、最大背景、現実的添付分布、1墓所20添付の境界ケース
- ローカル、外付け媒体、SMB 3.x共有
- 容量不足、読取専用、ファイル占有、切断、ロック喪失、外部更新

NASは単一端末・単一Windowsユーザーだけを対象とする。ファイルロックまたは原子的な
名前変更が機能しない共有はサポート外として結果へ記録する。

ローカル、外付け媒体、SMB 3.x共有ごとに保存先機能検査を実行する。

```powershell
.\packaging\windows\test-storage-capabilities.ps1 `
  -Directory Z:\HakamapTest `
  -StorageType smb3
```

排他ロック、置換相当操作、同期書込み・読戻し、清掃のどれかが失敗した保存先は
Hakamapの動作保証対象にしない。スクリプトは検査用のランダムな一時ディレクトリだけを使い、
既存ファイルを読み書きしない。
