# JSON Schema・Java保存モデル詳細設計

## 目的

Project、Catalog、およびRecoveryの保存境界を、自己完結したJSON SchemaとJava保存モデルで
厳密に定義する。ドメインモデルをJSONライブラリへ直接結合せず、読み込み、変換、検証、
保存の各段階で不正または非対応のデータを正式状態へ取り込まない。

## 対象形式

| 形式 | 正式ファイル | Schema ID | 初期バージョン |
| --- | --- | --- | --- |
| Project | `<project-root>/project.json` | `urn:hakamap:project:1` | 1 |
| Catalog | `%LOCALAPPDATA%/Hakamap/catalog.json` | `urn:hakamap:catalog:1` | 1 |
| Recovery | `%LOCALAPPDATA%/Hakamap/recovery/<project-id>.recovery.json` | `urn:hakamap:recovery:1` | 1 |

- JSON SchemaはDraft 2020-12を使用する。
- 各Schemaは外部参照を持たない自己完結した1ファイルとする。
- Schemaとサンプルは実行時にネットワークへアクセスせず、アプリケーションへ同梱する。
- Project、Catalog、およびRecoveryのバージョンは互いに独立して更新する。

## Schema配置

```text
backend/src/main/resources/json-schema/
├─ project/
│  └─ project-v1.schema.json
├─ catalog/
│  └─ catalog-v1.schema.json
└─ recovery/
   └─ recovery-v1.schema.json
```

- ファイル名、Schema ID、およびルートのバージョン定数が一致しない場合は起動時検査を失敗させる。
- 過去版Schemaは対応するMigratorとともに残し、既存IDを別の内容へ再利用しない。

## Schema検証ライブラリ

- `com.networknt:json-schema-validator:3.0.6`を採用する。
- Spring Boot 4系が使用するJackson 3と合わせ、Java 17以上に対応する3.x系を使用する。
- Draft 2020-12のDialectを明示してSchema Registryを初期化する。
- Schemaと入力はクラスパスまたはメモリからだけ供給し、HTTPなどの外部取得処理を登録しない。
- `format`はDraft 2020-12では既定で注釈扱いとなるため、必要な形式はSchemaの`pattern`と
  Java型変換で検証する。将来`format`を追加する場合はassertion設定を明示する。
- 製品ではバージョンを固定し、更新時はリリースノート、破壊的変更、Schema正常・異常
  サンプル、およびライセンスを再確認する。
- Apache License 2.0のライセンスとNOTICEを第三者ライセンス一覧へ含める。
- YAML対応は使用せず、任意依存として除外可能か実装時に確認する。

## 共通JSON規則

- 文字コードはUTF-8とし、BOMを出力しない。
- ルートを含むすべてのオブジェクトで`additionalProperties: false`を指定する。
- 必須コレクションは0件でも空配列として出力し、`null`を使用しない。
- 任意値がない場合はプロパティを省略し、空文字や`null`で代用しない。
- プロパティ名は`camelCase`、列挙値は英語の固定小文字文字列とする。
- UUIDはハイフン付き小文字のUUID Version 4、日時はUTCミリ秒付きの
  `YYYY-MM-DDTHH:mm:ss.SSSZ`だけを受け入れる。
- SHA-256は64文字の小文字16進数とする。
- 座標、寸法、倍率、および角度は小数第3位までとし、指数表記を出力しない。
- 文字列長はUnicodeコードポイント単位でJava側でも検証する。JSON Schemaの`maxLength`も
  使用するが、検証ライブラリの数え方だけに依存しない。
- 改行を許可しない項目ではCR、LF、Unicode行区切り、および制御文字を拒否する。
- 複数行項目ではCRLFとCRをLFへ正規化し、許可したLF以外の制御文字を拒否する。

## 決定的なJSON出力

- Java保存モデルからJSONを生成するときは、Schemaで定めたプロパティ順で整形出力する。
- Mapの反復順序をJSON配列の順序へ使用しない。
- `areas`は`displayOrder`、続いてUUIDで出力する。
- `graves`はUUIDで出力する。
- `people`は`graveId`、`displayOrder`、UUIDの順で出力する。
- `assets`は`assetType`、添付の`graveId`、`displayOrder`、UUIDの順で出力する。
- Catalogの`projects`はプロジェクトUUIDで出力する。
- Recoveryの`stagedAssets`はアセットUUIDで出力する。
- 同じ保存モデルは常に同じUTF-8 JSONを生成し、不要な並べ替えによるSHA-256変化を防ぐ。
- 配列順に業務上の意味がない項目でも、読み込み後の次回保存で決定的な順序へ正規化する。

## Java保存モデルの境界

保存モデルは`jp.hakamap.persistence.json.model`配下の変更不能なrecordとして定義する。

```text
jp.hakamap.persistence.json
├─ model
│  ├─ project
│  ├─ catalog
│  └─ recovery
├─ mapper
├─ schema
└─ migration
```

- recordのプロパティはJSONと同じ`camelCase`名とする。
- UUID、日時、数値は、保存モデルでも原則として`UUID`、`Instant`、`BigDecimal`、
  `long`、`int`など意味に対応するJava型を使用する。
- 任意の単一値は`Optional`ではなくnullableなrecord構成要素としてJackson境界で受け、
  Mapperへ渡す前にSchemaと必須条件を検証する。ドメインモデルへ`null`を持ち込まない。
- 必須配列は`List`とし、デシリアライズ直後に不変コピーを作成する。
- 保存モデルには業務メソッドを持たせず、ドメインモデルとの変換を形式別Mapperが担当する。
- ドメインモデル、API DTO、および保存モデルを同じJava型として兼用しない。

## Project v1

### ルート

`ProjectFileV1`は次の構成要素を持つ。

| プロパティ | Java型 | 必須 |
| --- | --- | --- |
| `schemaVersion` | `int` | 必須、固定値1 |
| `project` | `ProjectMetadataV1` | 必須 |
| `background` | `BackgroundPlacementV1` | 任意 |
| `areas` | `List<AreaV1>` | 必須 |
| `graves` | `List<GraveV1>` | 必須 |
| `people` | `List<PersonV1>` | 必須 |
| `assets` | `List<AssetV1>` | 必須 |

- `AssetV1`はsealed interfaceとし、`BackgroundAssetV1`と`AttachmentAssetV1`を
  `assetType`で判別する。
- JSON Schemaでも`assetType`を判別子とする`oneOf`を使用し、種別に不要な項目を拒否する。
- 所属エリア、完成状態、未完成理由、警告、検索インデックス、および編集リビジョンを保存しない。

### Project保存record

```java
record ProjectFileV1(
    int schemaVersion,
    ProjectMetadataV1 project,
    BackgroundPlacementV1 background,
    List<AreaV1> areas,
    List<GraveV1> graves,
    List<PersonV1> people,
    List<AssetV1> assets) {}

record ProjectMetadataV1(UUID id, String name, Instant createdAt, Instant updatedAt) {}

record BackgroundPlacementV1(
    UUID assetId,
    BigDecimal x,
    BigDecimal y,
    BigDecimal rotation,
    BigDecimal scaleX,
    BigDecimal scaleY) {}

record AreaV1(
    UUID id,
    String name,
    BigDecimal x,
    BigDecimal y,
    BigDecimal width,
    BigDecimal height,
    String colorPreset,
    boolean visible,
    int displayOrder) {}

record GraveV1(
    UUID id,
    String managementNumber,
    String name,
    String notes,
    BigDecimal x,
    BigDecimal y,
    BigDecimal width,
    BigDecimal height,
    BigDecimal rotation,
    Instant updatedAt) {}

record PersonV1(
    UUID id,
    UUID graveId,
    String name,
    String posthumousName,
    Instant createdAt,
    Instant updatedAt,
    int displayOrder) {}
```

- `background`、`managementNumber`、`name`、`notes`、および`posthumousName`のうち
  Schemaで任意とした値は、デシリアライズ境界に限って`null`を許容する。
- Mapperは任意値をドメインの`Optional`と値オブジェクトへ変換し、空文字や不正値を拒否する。

```java
sealed interface AssetV1 permits BackgroundAssetV1, AttachmentAssetV1 {}

record BackgroundAssetV1(
    UUID id,
    String assetType,
    String originalFileName,
    String relativePath,
    String sourceMediaType,
    String storedMediaType,
    long sizeBytes,
    String sha256,
    Instant createdAt) implements AssetV1 {}

record AttachmentAssetV1(
    UUID id,
    String assetType,
    String originalFileName,
    String relativePath,
    String sourceMediaType,
    String storedMediaType,
    long sizeBytes,
    String sha256,
    Instant createdAt,
    UUID graveId,
    String displayName,
    String description,
    Instant updatedAt,
    int displayOrder) implements AssetV1 {}
```

### Schema検証と業務検証の分担

Schemaで検証するもの：

- 必須・任意、JSON型、固定値、列挙値、文字数、数値範囲、配列上限
- UUID・日時・SHA-256・相対パスなどの単項目形式
- 背景と添付アセットで許可するプロパティの違い

JSON Schema成功後、保存モデルからドメインモデルを構築する前に、Javaで次を検証する。

| Java検証 | 対象と合格条件 |
| --- | --- |
| UUIDの全体一意性 | Project、エリア、墓所、人物、アセットの種別をまたいで同じUUIDが存在しない |
| 所有・参照整合性 | 人物と添付の`graveId`、背景配置のアセット参照、RecoveryとProjectの参照先が存在し種別も一致する |
| Project・Recovery一致 | Recovery外側のProject UUID、埋込Project UUID、アセット所有Projectが一致する |
| 表示順の連続性 | エリア、同一墓所内の人物・添付が0から始まる重複のない連番である |
| エリア名称・色 | 正規化したエリア名と色プリセットがProject内で一意で、色がシステム状態色と重複しない |
| エリア形状 | 正規化後のエリア内部が相互に重ならず、辺・角の接触だけを許可する |
| 墓所形状 | 正規化後の墓所内部が相互に重ならず、辺・角の接触だけを許可する |
| 墓所所属 | 墓所中心点、エリア境界、表示順から所属エリアを一意に導出できる |
| 墓所業務キー | 管理番号がある墓所は、導出したエリアUUIDと正規化管理番号の組み合わせが一意である |
| 人物複数項目 | 氏名または戒名の少なくとも一方が設定されている |
| 添付件数 | 同一墓所の添付が20件以下である |
| アセット所有・種別 | 背景は最大1件で背景配置から参照され、添付は存在する墓所だけに所有される |
| アセット実体 | 管理領域内に通常ファイルとして存在し、リンク、ディレクトリ、管理領域外参照ではない |
| アセット相対パス | 正規化した相対パスが用途別ディレクトリ、アセットUUID、および許可拡張子と一致する |
| アセット内容 | 実ファイルのシグネチャ、MIMEタイプ、拡張子、容量、画像寸法、PDFページ数がメタデータと許可条件に一致する |
| アセット完全性 | 実ファイルのサイズとSHA-256が保存メタデータと一致する |
| Catalog参照 | Project UUIDと保存先が重複せず、デフォルトProjectが存在する`active`項目である |

- いずれか1件でも失敗した場合は部分的に読み込まず、Project、Catalog、Recovery、
  インポート、復元、および保存候補の全体を拒否する。
- 読み込み、保存直前、Schema移行後、Recovery適用前、インポート確定前、
  バックアップ復元確定前に、対象に応じた同じ検証サービスを使用する。

## Catalog v1

`CatalogFileV1`は`schemaVersion`、任意の`defaultProjectId`、および必須の`projects`を持つ。

- `CatalogProjectV1`は`state`を判別子とするsealed interfaceとする。
- `active`は`originalPath`を許可せず、`trashed`は`originalPath`を必須とする。
- 絶対パスはCatalogだけに保存し、Project、Recovery内のProjectスナップショット、
  APIレスポンス、およびログへ複製しない。
- `defaultProjectId`の参照先が存在し、`active`であることはJavaの業務検証で確認する。
- `lastKnownName`と日時は一覧表示用キャッシュであり、Projectの正式値を上書きする入力には使用しない。

```java
record CatalogFileV1(
    int schemaVersion,
    UUID defaultProjectId,
    List<CatalogProjectV1> projects) {}

sealed interface CatalogProjectV1 permits ActiveCatalogProjectV1, TrashedCatalogProjectV1 {}

record ActiveCatalogProjectV1(
    UUID projectId,
    String path,
    String lastKnownName,
    Instant lastKnownCreatedAt,
    Instant lastKnownUpdatedAt,
    String state) implements CatalogProjectV1 {}

record TrashedCatalogProjectV1(
    UUID projectId,
    String path,
    String originalPath,
    String lastKnownName,
    Instant lastKnownCreatedAt,
    Instant lastKnownUpdatedAt,
    String state) implements CatalogProjectV1 {}
```

- 保存recordではパスをJSON文字列として受けるが、Mapperで`Path`へ変換して絶対パス、
  正規化、および管理領域との関係を検証する。

## Recovery v1

`RecoveryFileV1`は次の構成要素を持つ。

| プロパティ | Java型 | 必須 |
| --- | --- | --- |
| `recoverySchemaVersion` | `int` | 必須、固定値1 |
| `applicationVersion` | `String` | 必須 |
| `projectId` | `UUID` | 必須 |
| `createdAt` | `Instant` | 必須 |
| `baseProjectSha256` | `String` | 必須 |
| `projectSnapshot` | `ProjectFileV1` | 必須 |
| `stagedAssets` | `List<StagedAssetV1>` | 必須 |

- Recovery Schemaは`projectSnapshot`を必須のJSONオブジェクトとして検証し、
  その内部は`projectSnapshot.schemaVersion`に対応する同梱済みProject Schemaで
  続けて検証する。
- Recovery SchemaからProject Schemaへの外部`$ref`は使用せず、JavaのSchema検証サービスが
  2つのローカルSchemaを順に適用する。定義の複製と将来の更新漏れを防ぎつつ、
  実行時のネットワーク参照を発生させない。
- `projectId`と`projectSnapshot.project.id`の一致はJavaで検証する。
- `tempRelativePath`はアプリケーション一時領域を基準とする相対パスだけを許可する。
- ステージングアセットのUUID、サイズ、およびSHA-256がスナップショットと実体に
  一致することをJavaで検証する。

```java
record RecoveryFileV1(
    int recoverySchemaVersion,
    String applicationVersion,
    UUID projectId,
    Instant createdAt,
    String baseProjectSha256,
    ProjectFileV1 projectSnapshot,
    List<StagedAssetV1> stagedAssets) {}

record StagedAssetV1(
    UUID assetId,
    String tempRelativePath,
    long sizeBytes,
    String sha256) {}
```

## 読み込み順序

1. ファイルサイズとJSONパーサーのストリーム制約を検査する。
2. JSONをツリーとして読み、ルートの形式別バージョンだけを取得する。
3. 対応Schemaをローカルリソースから選択する。
4. 選択したSchemaでJSONツリーを検証する。
5. 対応する保存recordへデシリアライズする。
6. 保存モデル固有のJava検証を行う。
7. Mapperでドメイン候補へ変換する。
8. ProjectまたはCatalog集約全体の業務不変条件を検証する。
9. すべて成功した候補だけを正式なメモリ状態として公開する。

- バージョンプロパティがない、整数でない、1未満、または対応版より新しい場合は変換を試みない。
- Schema違反を推測で補正せず、プロジェクト全体または対象の端末管理ファイルを拒否する。
- CatalogとRecoveryの既定の復旧手順は基本設計に従い、破損データを部分的に採用しない。

## 保存順序

1. ドメイン集約から保存モデルを生成する。
2. 決定的な配列順とプロパティ順で一時JSONへ整形出力する。
3. 出力したJSONを再度読み、対応Schemaで検証する。
4. 保存モデル固有のJava検証と業務不変条件検証を行う。
5. UTF-8バイト列とSHA-256を確定する。
6. 形式ごとの安全な置換手順で正式ファイルへ切り替える。

- メモリ上の保存モデルを検証しただけで確定せず、実際に出力したJSONを検証する。
- SchemaまたはJava検証に失敗したJSONは正式ファイルへ置換しない。

## テスト方針

- 形式・バージョンごとに最小正常、最大正常、および各制約の異常サンプルを用意する。
- Schema自体をDraft 2020-12のメタスキーマ相当で検査する。
- 正常サンプルを読み込み、保存し直し、再読み込みして同じ保存モデルになることを確認する。
- プロパティ順、配列順、数値表現、および改行コードが決定的であることを確認する。
- 未知プロパティ、`null`、型違い、上限超過、重複参照、および不正パスを確認する。
- Schemaで表現しない相互参照と業務不変条件がJava検証で拒否されることを確認する。
- Recovery内の`projectSnapshot`へ対応するProject Schemaが必ず適用され、
  単独のProject検証と同じ判定になることを確認する。

## 未決事項

- JSONパーサーの防御値は`local-api.md`の上限をProject、Catalog、およびRecoveryの
  読み込みにも適用する。
