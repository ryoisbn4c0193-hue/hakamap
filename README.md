# Hakamap

Hakamapは、墓地全体の図面を背景として取り込み、エリア、墓所、人物、写真・添付情報を
地図上で管理する、オフラインかつ単一ユーザー向けのWindowsアプリです。

MVPの要件定義と基本設計は完了し、次は詳細設計と実装へ進む段階です。既存の実装や依存関係には、
設計確定前の構成が残っている場合があります。詳細設計と実装では`docs/requirements`、
`docs/design`、および`docs/adr`にある最新文書を正とします。

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
採用しません。現在のバックエンド雛形に残るH2関連依存は、JSON永続化の実装時に置き換える予定です。

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

主な検査コマンドは次のとおりです。

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
- [データ保存とプライバシー](docs/design/data-storage-and-privacy.md)
- [バックアップと復元](docs/design/backup-and-restore.md)
- [ADR](docs/adr/)
- [開発引継ぎメモ](docs/development-handoff.md)
- [AI・開発作業規約](AGENTS.md)

機能要件の正本は`docs/requirements`、技術方式と基本設計の正本は`docs/design`、重要な
アーキテクチャ判断は`docs/adr`です。同じ内容が他の文書と異なる場合は、正本を確認して
未確定事項を推測で確定しないでください。

## Windows配布

Windows 11上で、Java 21ランタイムを同梱した`Hakamap-<バージョン>.exe`を作成する予定です。
MVPではコード署名と自動アップデートを行いません。Windows用成果物はWindows上で作成し、
Linuxからのクロスビルドには依存しません。
