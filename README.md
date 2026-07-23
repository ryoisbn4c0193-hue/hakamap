# Hakamap

Hakamapは、墓地全体の図面を背景として取り込み、エリア、墓所、人物、写真・添付情報を
地図上で管理する、オフラインかつ単一ユーザー向けのWindowsアプリです。

MVPの要件定義、基本設計、および詳細設計8工程は完了し、Phase 0のビルド・依存・品質基盤まで
実装しています。設計と実装では`docs/requirements`、`docs/design`、および`docs/adr`にある
最新文書を正とします。

## MVPの概要

- PNG、JPEG、WebP、または1ページPDFを墓地図の背景として取り込む
- 長方形のエリアと墓所を作成・編集する
- 1つの墓所へ複数の人物と添付ファイルを登録する
- 墓所の検索、Undo／Redo、バックアップ、復元を行う
- 現在の表示範囲または選択エリアを印刷、PDF、PNGとして出力する
- プロジェクト全体を`.hakamap`形式でエクスポート・インポートする
- インターネットや外部サービスに接続せず、利用者が指定した場所へデータを保存する

通路、入口、建物、自由地図要素、多角形、拡張項目、マスターデータ、および
クラウド同期はMVP対象外です。

ネットワーク上の保存先は、必要な排他ロックと原子的置換の機能検査に合格した
Windows 11から接続するSMB 3.x共有だけを動作保証対象とします。すべてのNASを保証するものではなく、
単一端末・単一Windowsユーザーでの利用に限ります。複数端末からの同時編集はサポートしません。

## アーキテクチャ

```text
既定ブラウザ
    │ 127.0.0.1 / 同一オリジン
    ▼
React + TypeScript + PixiJS
    │ /api
    ▼
Java 21 + Spring Boot
    │
    ▼
project.json + ローカルアセット
```

- フロントエンド: React、TypeScript、Vite、PixiJS
- バックエンド: Java 21、Spring Boot
- UI状態管理: Zustand
- サーバー状態管理: TanStack Query
- UIコンポーネント: MUI
- 永続化: プロジェクトフォルダ内のJSONとアセットファイル
- Windows配布: `jlink`と`jpackage`によるユーザー単位の`.exe`インストーラー

Electron、Tauri、JavaFX、H2、Spring Data JPA、およびFlywayは、確定した基本設計では
採用しません。バックエンド雛形からこれらの永続化依存を除去し、JSON Schema検証と
プロジェクトフォルダ内JSONによる永続化を段階的に実装しています。

ブラウザのタブを閉じただけではHakamap本体は終了しません。画面を閉じた後は、
デスクトップまたはスタートメニューのHakamapショートカットを再実行すると開き直せます。
終了する場合はアプリケーション画面内の「Hakamapを終了」を使用します。
MVPではタスクトレイへ常駐しません。

## ディレクトリ

```text
frontend/    ReactおよびTypeScriptクライアント
backend/     Spring Bootアプリケーション
packaging/   Windows向けパッケージ設定とスクリプト
docs/        要件、基本設計、ADR、開発引継ぎ
```

## 開発環境

Dev ContainerにはJava 21、Node.js、pnpm、Git、および一般的な開発ツールを用意しています。

### フロントエンド

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

主な検査コマンドは次のとおりです。

```bash
cd frontend
pnpm lint
pnpm format:check
pnpm test
pnpm build
```

### バックエンド

```bash
cd backend
./mvnw spring-boot:run
```

主な検査コマンドは次のとおりです。`verify`は固定バージョンのNode.jsとpnpmを
`backend/target`へ用意し、フロントエンドの全標準検査とビルドも実行してJARへ同梱します。

```bash
cd backend
./mvnw test
./mvnw verify
```

開発時はViteからSpring Bootの`/api`へプロキシします。本番ではSpring BootがReactの
ビルド成果物を配信し、HTTPサーバーを`127.0.0.1`だけに公開します。

## データの取り扱い

- プロジェクトは利用者が指定したフォルダへ保存します。
- 構造化データはUTF-8の`project.json`、背景と添付は`assets`へ保存します。
- `%LOCALAPPDATA%/Hakamap`にはカタログ、復旧データ、一時ファイル、キャッシュ、ログを保存します。
- 利用者による`project.json`の閲覧は可能ですが、手動編集はサポートしません。
- 生成されたプロジェクト、背景、添付、バックアップ、復旧データをGitへコミットしないでください。
- 個人情報をログへ出力したり、外部サービスへ送信したりしません。

## ドキュメント

- [要件文書一覧](docs/requirements/README.md)
- [基本アーキテクチャ](docs/design/basic-architecture.md)
- [ドメインモデルと不変条件](docs/design/domain-model.md)
- [JSON SchemaとJava保存モデル](docs/design/json-schema-and-persistence-model.md)
- [コマンドとUndo／Redo差分](docs/design/commands-and-history.md)
- [保存・バックアップ・復旧トランザクション](docs/design/storage-transactions.md)
- [ローカルAPI・DTO・エラーコード](docs/design/local-api.md)
- [フロントエンド状態管理](docs/design/frontend-state-management.md)
- [PixiJS描画・座標変換・当たり判定](docs/design/pixi-map-interaction.md)
- [テスト・テストデータ・Windows検証](docs/design/test-strategy.md)
- [MVP実装計画](docs/implementation-plan.md)
- [データ保存とプライバシー](docs/design/data-storage-and-privacy.md)
- [バックアップと復元](docs/design/backup-and-restore.md)
- [ADR](docs/adr/)
- [開発引継ぎメモ](docs/development-handoff.md)
- [AI・開発作業規約](AGENTS.md)

機能要件の正本は`docs/requirements`、技術方式と基本設計の正本は`docs/design`、重要な
アーキテクチャ判断は`docs/adr`です。同じ内容が他の文書と異なる場合は、正本を確認して
未確定事項を推測で確定しないでください。

詳細設計は、次の責務順で作成しています。

1. ドメインモデルと不変条件
2. JSON SchemaとJava保存モデル
3. コマンドとUndo／Redo差分
4. 保存トランザクション
5. API、DTO、およびProblem Detailsのエラーコード
6. フロントエンド状態管理
7. PixiJSの描画、座標変換、および当たり判定
8. テスト設計

## Windows配布

Windows 11上で、Java 21ランタイムを同梱した`Hakamap-<バージョン>.exe`を作成する予定です。
MVPではコード署名と自動アップデートを行いません。Windows用成果物はWindows上で作成し、
Linuxからのクロスビルドには依存しません。
