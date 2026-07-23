# ローカルAPI・DTO・エラーコード詳細設計

## 目的

ReactとJavaバックエンドの境界を、Project UUID、編集リビジョン、一時セッション、
型付きDTO、およびProblem Detailsで明確にし、古い画面、外部サイト、別Project、
不正入力、および長時間処理から業務状態を保護する。

## 共通規則

- APIのベースパスを`/api/v1`とする。
- APIバージョンは保存Schemaバージョンおよび製品バージョンから独立させる。
- 互換性を壊すAPI変更では既存`v1`の意味を変更せず、新しいメジャーパスを追加する。
- JSONのプロパティ名と列挙値は保存形式と同じ`camelCase`と英語固定文字列を使用する。
- API DTO、ドメインモデル、およびJSON保存モデルを別のJava型とする。
- APIでリソースを識別する場合はUUIDを使用し、名称や管理番号を識別子として使用しない。
- Project配下APIは`/projects/{projectId}`とし、現在開いているProject UUIDと一致する場合だけ処理する。
- APIレスポンスへ絶対パス、認証情報、内部一時パス、例外スタック、入力された個人情報を含めない。
- 日時はUTCミリ秒付きISO 8601、座標は小数第3位までのJSON numberとする。
- 状態変更APIは`application/json`または用途ごとに明示したContent-Typeだけを受け入れる。
- 成功レスポンスには必要な場合だけ`Cache-Control: no-store`を付け、ブラウザキャッシュへ
  Projectデータを残さない。

## JSONストリーム防御

| 対象 | 上限 |
| --- | --- |
| APIのJSONリクエスト本文 | 10MB |
| JSONのネスト深度 | 50 |
| 文字列1項目 | 1MB |
| 数値トークン | 64文字 |
| Project JSON | 100MB |
| Catalog JSON | 10MB |
| Recovery JSON | 110MB |

- HTTP本文サイズはJSON解析前にContent-Lengthと実読込量の両方で監視する。
- Content-Lengthがない、または宣言値より実体が大きい場合も実読込量で上限を適用する。
- Jacksonのストリーム制約を明示設定し、既定値の変更へ依存しない。
- 上限超過時はツリー構築、DTO変換、および業務処理を継続しない。
- Project、Catalog、およびRecoveryのファイルサイズは読み込み開始前にも確認する。
- 画像・PDF・エクスポートZIPをJSONやBase64へ埋め込まず、Java側のファイル選択と
  ストリーミングファイル処理を使用する。

## 認証とリクエスト保護

- すべての非公開APIで起動時の一時セッションCookieを要求する。
- 状態変更APIではセッションに加え、CSRFトークン、Origin、およびHostを検証する。
- React起動時に呼ぶ認証済み`GET /api/v1/session`で、CSRFトークンを
  `X-Hakamap-CSRF-Token`レスポンスヘッダーとして返す。
- ReactはCSRFトークンをJavaScriptメモリだけに保持し、localStorage、sessionStorage、
  Cookie、URL、TanStack Queryの永続キャッシュ、およびログへ保存しない。
- 状態変更APIでは同じ値を`X-Hakamap-CSRF-Token`リクエストヘッダーで送信する。
- CSRFトークンの有効期間はHttpOnlyセッションCookieと同じとし、バックエンド再起動、
  セッション失効、またはセッション再生成で以前のトークンを無効化する。
- 複数タブは同じブラウザセッションCookieを共有し、各タブが認証済み画面取得時に
  同じセッションへ対応するトークンを取得してメモリ保持する。
- トークンを再取得する専用APIは設けず、認証済みの`GET /api/v1/session`の
  レスポンスヘッダーから再取得する。このAPI自体は有効なHttpOnlyセッションCookie、
  正しいOrigin、およびHostを要求する。
- 状態変更APIがセッションまたはCSRF失効を返した場合は自動的に同じ変更を再送せず、
  起動用認証から新しいセッションを確立して画面状態を再取得する。
- セッションCookieは`HttpOnly`、`SameSite=Strict`とし、HTTPSを使わない
  `127.0.0.1`構成ではSecure属性の実装可否をブラウザ試験で確認する。
- 認証失敗時にProjectの存在、UUID、保存先、および処理状態を推測できる情報を返さない。

## Projectスナップショット

プロジェクトを開いた直後に、編集画面は次を一括取得する。

```http
GET /api/v1/projects/{projectId}/snapshot
```

`ProjectSnapshotResponse`は次を含む。

```text
projectId
revision
dirty
project
background?
areas[]
graves[]
assets[]
graveStates[]
historySummary
capabilities
```

- `revision`は開いた編集セッションの現在値とする。
- `graveStates`は墓所UUID、所属エリアUUID、完成状態、未完成理由、および警告理由を含む。
- Project内の人物全件は含めず、選択墓所の人物専用APIから取得する。
- 添付メタデータを含めるが、画像・PDF由来画像のバイナリとサムネイルは含めない。
- `capabilities`は保存、Undo、Redo、キャンセルなど現在可能な操作を真偽値で示す。
- ズーム、パン、選択、および検索条件は含めない。
- Projectを開いていない、またはパスのUUIDと開いているUUIDが異なる場合はスナップショットを返さない。

## 選択墓所の人物一覧

```http
GET /api/v1/projects/{projectId}/graves/{graveId}/people?cursor={opaque}
```

`GravePeoplePageResponse`は次を含む。

```text
projectId
graveId
revision
items[]
nextCursor?
totalCount
```

- `items`は人物UUID、氏名、戒名、登録日時、更新日時、および表示順を含む
  `PersonListItemResponse`の配列とする。
- 1ページを100件とし、表示順、人物UUIDの順で安定して返す。
- `cursor`はProject UUID、墓所UUID、取得開始位置、`revision`、および5分の期限へ
  サーバー側で紐づける不透明値とする。
- 人物が100件を超える場合は`nextCursor`で追加取得し、右パネルでは仮想スクロールにより
  表示中の行だけを描画する。
- Projectの`revision`が変わったカーソルを拒否し、先頭ページから取得し直す。
- 人物一覧は墓所UUID単位のTanStack Queryとしてキャッシュし、Projectを閉じる、
  墓所を削除する、または選択キャッシュの保持上限を超えた場合に破棄する。
- 墓所の選択解除だけでは直ちに個人情報を画面表示しないが、同一編集セッション中の
  再選択を高速化するため直近5墓所までをメモリキャッシュしてよい。永続化しない。

## 編集コマンドAPI

```http
POST /api/v1/projects/{projectId}/commands
Content-Type: application/json
```

```json
{
  "expectedRevision": 12,
  "commandType": "moveGraves",
  "payload": {}
}
```

- `commandType`を判別子とするsealed DTOへデシリアライズする。
- 各CommandTypeは専用payload recordを持ち、汎用Mapや`JsonNode`をアプリケーション層へ渡さない。
- UUIDと更新日時はクライアントから受け取らず、作成対象のクライアント内対応付けが必要な場合は
  リクエスト内だけで有効な個人情報を含まない`clientRef`を使用する。
- `expectedRevision`不一致時は候補作成、UUID発行、および履歴変更を行わない。

### コマンド共通DTO

```java
record CommandRequest(
    long expectedRevision,
    CommandType commandType,
    CommandPayload payload) {}
```

- JSON上の`commandType`は次表のlower camel case値を使用し、Javaの列挙名との変換を
  APIアダプター内へ閉じ込める。
- `payload`は`commandType`に対応する専用recordだけへ変換し、未知項目を拒否する。
- 更新DTOでは「変更しない」と「値を消去する」を曖昧にしない。フォームで一括適用する
  更新コマンドは対象項目をすべて必須プロパティとし、任意文字列の消去だけJSON `null`で表す。
- 文字列のトリム、文字数、座標の丸め、および業務不変条件はJava側で再検証する。
- `clientRef`は同一リクエスト内の作成候補を応答のUUIDへ対応付ける文字列であり、
  1～64文字のASCII英数字、ハイフン、およびアンダースコアだけを許可する。

### CommandType別payload

| `commandType` | payload項目 | 備考 |
| --- | --- | --- |
| `renameProject` | `name` | Project UUIDと更新日時はサーバー管理 |
| `setBackground` | `fileSelectionId`, `x`, `y`, `rotation`, `scaleX`, `scaleY` | 追加・差し替え共通。プレビューで利用者が確定した配置 |
| `transformBackground` | `x`, `y`, `rotation`, `scaleX`, `scaleY` | 配置の全値を送る |
| `removeBackground` | なし | 背景アセットと配置だけを対象とする |
| `createArea` | `clientRef`, `name`, `x`, `y`, `width`, `height`, `colorPreset?`, `visible` | 色省略時は未使用色をサーバーが割り当てる |
| `updateArea` | `areaId`, `name`, `x`, `y`, `width`, `height`, `colorPreset`, `visible` | フォーム確定後の全値を送る |
| `deleteArea` | `areaId` | 所属墓所は削除せず再判定する |
| `createGrave` | `clientRef`, `x`, `y`, `width`, `height` | 管理番号などの情報は設定しない |
| `createGraveGrid` | `clientRefPrefix`, `x`, `y`, `rows`, `columns`, `graveWidth`, `graveHeight`, `horizontalGap`, `verticalGap` | 左上から行単位で生成する |
| `fillGraveRange` | `clientRefPrefix`, `rangeX`, `rangeY`, `rangeWidth`, `rangeHeight`, `graveWidth`, `graveHeight`, `horizontalGap`, `verticalGap` | 範囲内へ収まる候補だけを生成する |
| `updateGraveInfo` | `graveId`, `managementNumber`, `name`, `notes` | 任意文字列は`null`で消去する |
| `moveGraves` | `graveIds`, `deltaX`, `deltaY` | 全対象へ同じ移動量を適用する |
| `resizeGrave` | `graveId`, `x`, `y`, `width`, `height` | 確定後の長方形全値を送る |
| `copyGraves` | `graveIds`, `deltaX`, `deltaY` | 元の相対位置を維持する |
| `deleteGraves` | `graveIds` | 人物・添付も同じコマンドで削除する |
| `numberGraves` | `numberingPreviewToken` | 採番条件や番号配列を再送しない |
| `createPerson` | `graveId`, `clientRef`, `name`, `posthumousName` | 任意文字列は`null`を許可する |
| `updatePerson` | `personId`, `name`, `posthumousName` | 表示順と所有墓所は変更しない |
| `deletePerson` | `personId` | 所有墓所UUIDの再送は不要 |
| `addAttachments` | `graveId`, `fileSelectionIds` | 選択した全ファイルを原子的に追加する |
| `updateAttachment` | `assetId`, `displayName`, `description` | 任意文字列は`null`で消去する |
| `reorderAttachments` | `graveId`, `orderedAssetIds` | 当該墓所の全添付UUIDを重複なく送る |
| `deleteAttachment` | `assetId` | 所有墓所UUIDの再送は不要 |

- `graveIds`、`fileSelectionIds`、および`orderedAssetIds`は空配列と重複値を拒否する。
- サーバーはProject、エリア、墓所、人物、およびアセットのUUID、作成日時、更新日時、
  表示順を必要に応じて生成し、クライアント指定を受け付けない。
- 背景差し替えの初期プレビュー値はスナップショットの現配置をクライアントが引き継ぐが、
  確定時には`setBackground`へ配置全値を明示する。
- `fileSelectionId`はJavaのファイル選択で発行し、HTTPセッション、選択目的、および
  使用可能なコマンドへ紐づける。単一使用とし、期限切れ、別用途、別セッション、
  または使用済みIDを拒否する。

### 一括採番プレビュー

```http
POST /api/v1/projects/{projectId}/numbering-previews
```

```text
expectedRevision
graveIds[]
prefix
startNumber
digitCount
suffix
```

- `graveIds`の入力順に依存せず、サーバーが地図座標の左から右、上から下で採番順を確定する。
- 応答は順序付きの墓所UUIDと割当予定番号、および不透明な
  `numberingPreviewToken`を返す。
- プレビューではProject、revision、履歴、および更新日時を変更しない。
- トークンはセッションと`expectedRevision`へ紐づけ、5分間有効な単一使用値とする。
- `numberGraves`はトークンに保持した候補を再検証して一括適用し、リクエストから変更された
  番号や対象を受け付けない。

### コマンド成功応答

Project全体ではなく、Javaが確定した差分を返す。

```text
status = applied | noChange
revision
dirty
upsertedAreas[]
deletedAreaIds[]
upsertedGraves[]
deletedGraveIds[]
personChanges[]
upsertedAssets[]
deletedAssetIds[]
graveStates[]
warnings[]
historySummary
result?
```

- `upserted`には正規化後の確定値を含める。
- `graveStates`は変更によって状態が変わった墓所だけを含める。
- `personChanges`の各要素は`graveId`、`upsertedPeople[]`、`deletedPersonIds[]`、
  および変更後の`totalCount`を含む。
- 人物変更の成功時、Reactは該当墓所の人物Queryがキャッシュ済みなら、表示順を維持して
  差分を反映する。未取得ページへ影響する場合または整合しない場合は該当墓所の人物Queryを
  無効化し、選択中なら先頭ページから再取得する。
- 墓所削除時は、削除墓所に対応する人物Queryをキャッシュから削除する。
- `result`は作成UUID、採番プレビュー確定結果などCommandType固有の安全な結果を含む。
- `noChange`では`revision`と履歴を変更せず、空の差分を返す。
- クライアントは送信した候補値ではなく、成功応答の確定値を画面状態へ反映する。

### 配置警告の確認応答

配置警告は通常エラーではないためProblem Detailsにせず、コマンド結果として返す。

```json
{
  "status": "confirmationRequired",
  "revision": 12,
  "confirmationToken": "opaque-random-token",
  "expiresAt": "2026-07-23T00:05:00.000Z",
  "warnings": [
    {
      "code": "unassigned",
      "count": 3
    }
  ]
}
```

```http
POST /api/v1/projects/{projectId}/command-confirmations/{confirmationToken}
```

```json
{
  "expectedRevision": 12
}
```

- トークンはパス上で不透明値として扱い、アクセスログへ記録しないよう当該パスをマスクする。
- キャンセルではAPI呼出しを必須とせず、クライアントが候補を破棄できる。
- 明示キャンセルAPIを呼ぶ場合は`DELETE`を使用し、候補を早期破棄する。

## Undo／Redoと履歴

```http
POST /api/v1/projects/{projectId}/history/undo
POST /api/v1/projects/{projectId}/history/redo
GET  /api/v1/projects/{projectId}/history
```

- Undo／Redo要求は本文に`expectedRevision`を持つ。
- 成功応答は編集コマンドと同じ差分形式を使用する。
- 履歴取得は最大100件の操作日時、CommandType、対象種別、件数、適用状態、
  保存位置、およびUndo／Redo可否を返す。
- 履歴説明へ入力値を含めず、画面側がCommandTypeと件数から日本語文言を生成する。

## 検索

```http
POST /api/v1/projects/{projectId}/search
```

- 検索語をURL、ブラウザ履歴、および既定のアクセスログへ残さないため、検索はPOST本文で受ける。
- 検索は保存状態を変更せず、CSRF検証対象の状態変更としては扱わないが、セッション、
  Origin、Host、およびContent-Typeを検証する。
- 検索語はUnicodeコードポイント単位で最大200文字とし、上限超過時は切り捨てず拒否する。
- 前後空白だけの検索語は検索条件なしとして扱う。
- リクエストは検索語、任意の不透明カーソル、および固定ページサイズ200を持つ。
- レスポンスは順序付きの墓所UUID、表示用ラベル、所属エリアUUID、合計件数、
  および次ページがある場合だけ不透明な`nextCursor`を返す。
- カーソルは検索条件と編集`revision`へ紐づけ、有効期限を5分とする。
- ページ取得前に編集`revision`が変わった場合はカーソルを拒否し、最初のページから
  最新状態を検索し直す。
- 結果件数を5,000件で打ち切らず、`nextCursor`がある限り200件ずつ取得できるようにする。
- 人物名など一致した具体的な入力値や、一致元項目の内容をレスポンスへ複製しない。
- 検索で編集`revision`、dirty、履歴、および更新日時を変更しない。

## アセット表示

```http
GET /api/v1/projects/{projectId}/assets/{assetId}/content
GET /api/v1/projects/{projectId}/assets/{assetId}/thumbnail
```

- Project集約から参照され、現在のセッションで表示可能なアセットだけを返す。
- リクエストパスからファイルシステムパスを組み立てず、UUIDから検証済みメタデータを解決する。
- `Content-Type`、`Content-Length`、`X-Content-Type-Options: nosniff`、
  `Content-Security-Policy`、および`Cache-Control: no-store`を設定する。
- 元ファイル名を`Content-Disposition`へ含めず、インライン表示に必要な安全な固定名を使用する。
- MVPではRangeリクエストに対応せず、画像または1ページPDFから変換した画像の全体を返す。
- 背景の通常地図表示では元画像配信ではなく、既存設計のタイルとサムネイルを使用する。

## Project・Catalog API

```text
GET    /api/v1/catalog/projects
POST   /api/v1/catalog/projects
POST   /api/v1/catalog/projects/{projectId}/open
POST   /api/v1/catalog/projects/{projectId}/relink
DELETE /api/v1/catalog/projects/{projectId}/registration
POST   /api/v1/catalog/projects/{projectId}/trash
POST   /api/v1/catalog/projects/{projectId}/restore
DELETE /api/v1/catalog/projects/{projectId}
PUT    /api/v1/catalog/default-project
DELETE /api/v1/catalog/default-project
POST   /api/v1/projects
POST   /api/v1/projects/{projectId}/close
```

- 絶対パスは、Java側のファイル・フォルダ選択で発行した一時選択IDによって受け渡す。
- Catalog一覧では保存場所の末尾だけを表示用文字列として返し、絶対パスを返さない。
- ごみ箱、復元、完全削除、および登録解除を異なるエンドポイントとして明示する。
- 完全削除はCatalog上で`trashed`のProjectだけに許可する。
- Project切り替え時の未保存確認は、保存、破棄、キャンセルの結果に応じて画面が
  明示的にAPIを呼び分ける。

## ファイル・フォルダ選択

```http
POST /api/v1/file-selections
```

`FileSelectionRequest`は次を含む。

```text
selectionMode = singleFile | multipleFiles | directory
purpose
```

`purpose`のMVP許可値は次とする。

```text
backgroundImport
attachmentImport
projectCreateDirectory
projectRelinkDirectory
projectSaveAsDirectory
exportDestination
importArchive
importDestinationDirectory
trashRestoreDirectory
```

- `selectionMode`と`purpose`の許可された組み合わせだけを受け付ける。
- `backgroundImport`と`importArchive`は`singleFile`、`attachmentImport`は
  `multipleFiles`、`exportDestination`は保存対象名を指定する`singleFile`、
  Project作成・再関連付け・別保存・インポート先・ごみ箱復元先は`directory`を使用する。
- JavaはSwing EDTで選択ダイアログを開き、同時に1つだけ表示する。
- 選択成功時は`200 OK`で`status = selected`、1件以上の不透明な
  `fileSelectionIds[]`、および同順の`displayNames[]`を返す。
- 単一選択とフォルダ選択でも配列形式を使用し、要素数を1件とする。
- `displayNames`は末尾の安全な表示名だけとし、絶対パスと親フォルダを含めない。
- 利用者がキャンセルした場合は`200 OK`で`status = cancelled`、空配列を返し、
  Problem Detailsにしない。
- 選択IDはHTTPセッションと`purpose`へ紐づく5分間有効な単一使用値とする。
- 正常使用、明示無効化、期限切れ、セッション終了、Project終了、およびアプリ終了で
  Javaメモリ上の選択コンテキストとIDを破棄する。

```http
DELETE /api/v1/file-selections/{fileSelectionId}
```

- Reactが後続操作をキャンセルした場合はDELETEで早期無効化できる。
- 別セッション、使用済み、および存在しないIDへは、存在を推測させない`404`を返す。
- 選択ダイアログが表示中の場合、新しい開始要求は
  `409 file-selection-dialog-already-open`として拒否する。
- 未知の`selectionMode`、未知の`purpose`、または許可されない組み合わせは
  `400 file-selection-request-invalid`として拒否する。

## 長時間処理

保存、バックアップ、復元、インポート、エクスポート、およびその他の長時間ファイル処理は
Operationリソースとして扱う。

```http
POST /api/v1/projects/{projectId}/operations/save
POST /api/v1/projects/{projectId}/operations/export
POST /api/v1/projects/{projectId}/operations/backup-restore
POST /api/v1/catalog/operations/import
POST /api/v1/catalog/projects/{projectId}/operations/trash-restore
GET  /api/v1/operations/{operationId}
DELETE /api/v1/operations/{operationId}
```

- Project配下の公開開始APIは`save`、`export`、`backup-restore`だけとする。
- Catalog配下の許可値は`import`、ごみ箱Project配下は`trash-restore`だけとする。
- 汎用の`{operationType}`を任意文字列として受けるルーティングは実装せず、未知の処理名は
  `404`として拒否する。
- `SaveOperationRequest`は`expectedRevision`を持ち、利用者の手動保存だけに使用する。
- `ExportOperationRequest`は`expectedRevision`と`fileSelectionId`を持つ。
- 自動バックアップは手動保存成功後にJava内部から別Operationとして開始し、
  Reactから直接開始するAPIとRequest DTOを設けない。
- `BackupRestoreOperationRequest`は`backupId`を持ち、現在開いている同じProjectを対象とする。
- `ImportOperationRequest`は`.hakamap`の`fileSelectionId`と取込先フォルダの
  `destinationSelectionId`を持つ。開始前に現在のProjectが閉じられていることを要求し、
  既存Project UUIDをパスに要求しない。
- `TrashRestoreOperationRequest`はCatalog上で`trashed`のProject UUIDと、元の場所を
  使用できない場合だけ任意の`destinationSelectionId`を持つ。
- `succeeded`の安全な結果は、保存では`revision`と`dirty`、エクスポートでは完了状態、
  バックアップ復元では再読込対象Project UUID、インポートでは作成・オープンしたProject UUID、
  ごみ箱復元では`active`になったProject UUIDを返す。絶対パスを返さない。
- 開始成功時は`202 Accepted`、`Location: /api/v1/operations/{operationId}`を返す。
- Operation IDは推測困難なランダム値とし、発行したHTTPセッションへ紐づける。
- クライアントは約500ミリ秒間隔で状態を取得する。
- 状態は`queued`、`running`、`committing`、`succeeded`、`failed`、`cancelled`とする。
- 応答は工程コード、進捗率またはスピナー種別、キャンセル可否、および安全な結果を含む。
- `committing`以降はキャンセル不可とし、DELETEへ競合エラーを返す。
- 手動保存は開始後キャンセル不可とする。エクスポート、自動バックアップ、
  バックアップ復元、インポート、およびごみ箱復元は`committing`へ入る前だけキャンセル可能とする。
- 完了したOperationは結果取得後に削除し、取得されない場合も10分後にメモリから破棄する。
- Operation ID、一時選択ID、絶対パス、および処理対象の入力値を通常ログへ出力しない。
- 同じProjectで競合する長時間処理が進行中の場合は新しい処理を開始しない。

## Problem Details

- 通常エラーはRFC 9457のProblem Detailsとして`application/problem+json`で返す。
- `type`は外部URLではなく`urn:hakamap:problem:<code>`とする。
- `title`はコードに対応する固定日本語、`status`はHTTPステータス、`detail`は
  個人情報を含まない固定または安全な説明とする。
- `instance`は省略し、絶対パス、Project UUIDを含むAPIパス、および一時IDを複製しない。
- 拡張プロパティは`code`、安全な`operationId`、`violations`、`totalViolationCount`とする。
- 内部例外クラス名、スタックトレース、SQL、ファイルパス、入力値、および認証情報を含めない。

```json
{
  "type": "urn:hakamap:problem:grave-overlap",
  "title": "墓所が重なっています",
  "status": 422,
  "detail": "配置を見直してください。",
  "code": "grave-overlap",
  "violations": [
    {
      "targetId": "66f74f34-076f-4f8e-a0cb-f437a2e437b8",
      "field": "geometry",
      "code": "overlap"
    }
  ],
  "totalViolationCount": 1
}
```

- 一括エラーの`violations`は最大100件とし、全件数を`totalViolationCount`へ格納する。
- `targetId`は既存データを対象とする場合だけUUIDを返し、新規候補では個人情報を含まない
  `clientRef`または配列位置を使用する。
- フィールド名はDTOの安定したプロパティ名、違反コードは英語固定値とする。

## HTTPステータス

| Status | 用途 |
| --- | --- |
| `200 OK` | 同期処理成功、no-op、スナップショット・履歴・検索取得 |
| `201 Created` | Projectなど同期的なリソース作成 |
| `202 Accepted` | 長時間Operation開始、キャンセル受付 |
| `204 No Content` | 登録解除など返却本文が不要な成功 |
| `400 Bad Request` | JSON構文、型、未知CommandType、単項目形式の不正 |
| `401 Unauthorized` | 一時セッションがない、または無効 |
| `403 Forbidden` | CSRF、Origin、Host、用途の異なる一時選択ID |
| `404 Not Found` | 現在のセッションから参照できないリソース |
| `409 Conflict` | revision、Project切り替え、Operation状態、確認トークンの競合 |
| `413 Content Too Large` | API本文またはアップロード防御上限超過 |
| `422 Unprocessable Content` | 業務不変条件違反 |
| `423 Locked` | Projectロック取得失敗または編集停止中 |
| `507 Insufficient Storage` | 必要空き容量不足 |
| `500 Internal Server Error` | 安全に分類できない内部障害 |
| `503 Service Unavailable` | 保存先切断、結果不明、終了処理中 |

## エラーコード分類

| 接頭辞 | 分類 |
| --- | --- |
| `request-*` | JSON、DTO、Content-Type、上限 |
| `session-*` | 認証、CSRF、Origin、Host |
| `project-*` | Project未オープン、UUID不一致、revision競合 |
| `area-*` | 名称、色、上限、重なり、参照 |
| `grave-*` | 寸法、重なり、業務キー、所属、採番 |
| `person-*` | 所有墓所、氏名・戒名、順序 |
| `asset-*` | 形式、容量、参照、内容不整合 |
| `history-*` | Undo／Redoなし、差分適用不能 |
| `storage-*` | ロック、切断、容量、確定結果不明 |
| `operation-*` | 実行中、キャンセル不可、期限切れ |
| `catalog-*` | 登録、再関連付け、削除、デフォルト |
| `file-selection-*` | 選択ダイアログ、用途、期限、選択結果 |

- 同じ業務原因へ複数のHTTPステータスまたはコードを割り当てない。

## 安定エラーコード一覧

| HTTP | code | 条件 |
| --- | --- | --- |
| 400 | `request-invalid-json` | JSON構文、型、必須項目、未知項目が不正 |
| 400 | `request-unknown-command` | 未対応の`commandType` |
| 400 | `request-unsupported-media-type` | 許可していないContent-Type |
| 400 | `request-field-invalid` | 単項目の形式、長さ、範囲が不正 |
| 413 | `request-too-large` | HTTP本文またはJSONストリーム防御上限超過 |
| 401 | `session-required` | 一時セッションCookieがない |
| 401 | `session-invalid` | セッションが無効または失効 |
| 403 | `session-csrf-invalid` | CSRFトークン不一致 |
| 403 | `session-origin-invalid` | OriginまたはHost不一致 |
| 404 | `project-not-open` | 編集対象Projectが開かれていない |
| 404 | `project-mismatch` | パスのProjectと現在開いているProjectが異なる |
| 409 | `project-revision-conflict` | `expectedRevision`が現在値と異なる |
| 409 | `project-busy` | 競合する操作または切り替えを実行中 |
| 404 | `area-not-found` | 対象エリアが存在しない |
| 422 | `area-limit-exceeded` | MVPのエリア数または利用可能色の上限超過 |
| 422 | `area-name-duplicate` | 正規化したエリア名が重複 |
| 422 | `area-color-in-use` | 他エリアが使用中の色を指定 |
| 422 | `area-overlap` | エリア内部が重なる |
| 404 | `grave-not-found` | 対象墓所が存在しない |
| 422 | `grave-invalid-geometry` | 寸法、座標、生成条件が不正 |
| 422 | `grave-overlap` | 墓所内部が他墓所と重なる |
| 422 | `grave-business-key-duplicate` | エリアUUIDと管理番号の業務キーが重複 |
| 422 | `grave-number-already-set` | 一括採番対象に採番済み墓所を含む |
| 422 | `grave-unassigned-for-numbering` | 一括採番対象に未割当墓所を含む |
| 404 | `person-not-found` | 対象人物が存在しない |
| 422 | `person-grave-not-found` | 人物の所有先墓所が存在しない |
| 422 | `person-name-required` | 氏名と戒名の両方が未入力 |
| 404 | `asset-not-found` | 対象アセットが存在しない、または参照不可 |
| 403 | `asset-selection-invalid` | 一時選択IDが別用途、別セッション、使用済み |
| 400 | `file-selection-request-invalid` | 選択モード、用途、または組み合わせが不正 |
| 409 | `file-selection-dialog-already-open` | 別のファイル選択ダイアログを表示中 |
| 422 | `asset-format-unsupported` | 拡張子、MIME型、シグネチャ、PDFページ数が非対応 |
| 422 | `asset-size-exceeded` | 1ファイルの容量上限超過 |
| 422 | `asset-dimensions-exceeded` | 画像の辺または総画素数上限超過 |
| 422 | `asset-count-exceeded` | 1墓所の添付件数上限超過 |
| 422 | `asset-integrity-invalid` | デコード、ハッシュ、管理メタデータの整合性不良 |
| 409 | `command-confirmation-invalid` | 確認トークンが別セッション、使用済み、または候補不一致 |
| 409 | `command-confirmation-expired` | 確認トークンの有効期限切れ |
| 409 | `numbering-preview-invalid` | 採番トークンが候補またはセッションと不一致 |
| 409 | `numbering-preview-expired` | 採番トークンの有効期限切れ |
| 409 | `history-undo-empty` | Undoできる履歴がない |
| 409 | `history-redo-empty` | Redoできる履歴がない |
| 409 | `history-asset-unavailable` | 復元に必要なアセット実体を利用できない |
| 423 | `storage-project-locked` | Projectロックを取得できない、または編集停止中 |
| 503 | `storage-unavailable` | 保存先切断またはI/O不能 |
| 507 | `storage-insufficient-space` | 安全な確定に必要な空き容量不足 |
| 409 | `storage-external-change` | 開いた後に正式データが外部変更された |
| 503 | `storage-commit-outcome-unknown` | 原子的置換結果を判定できない |
| 422 | `storage-atomic-move-unsupported` | 選択保存先で必要な原子的置換を利用できない |
| 409 | `operation-already-running` | 同じProjectで競合Operationを実行中 |
| 404 | `operation-not-found` | Operationが存在しない、期限切れ、または別セッション |
| 409 | `operation-cancel-not-allowed` | 確定段階以降のためキャンセル不可 |
| 404 | `catalog-project-not-found` | Catalogの登録または実体を確認できない |
| 409 | `catalog-project-duplicate` | 同じProject UUIDまたは保存先を重複登録 |
| 422 | `catalog-default-invalid` | デフォルトにできないProject状態 |
| 500 | `internal-unexpected` | 安全に分類できない予期しない障害 |

- 同じ`code`の意味とHTTPステータスはAPI v1の間は変更しない。
- 項目単位の`violations[].code`は`required`、`invalidFormat`、`outOfRange`、
  `duplicate`、`overlap`、`notFound`などの固定値とし、上表のProblem `code`と区別する。
- Projectやエンティティの存在を認証前に判定せず、認証エラーを優先する。

## 検証要件

- Project UUID不一致と古い`expectedRevision`で状態が変更されないことを確認する。
- コマンド成功差分を適用したクライアント状態が、再取得したスナップショットと一致することを確認する。
- 正規化前の入力値ではなくJava確定値が成功応答へ含まれることを確認する。
- 配置警告確認が通常エラーと区別され、期限切れ・使用済み・競合トークンで適用されないことを確認する。
- 長時間処理の状態遷移、500ミリ秒ポーリング、確定後のキャンセル拒否、および10分清掃を確認する。
- 一括エラーが100件までに制限され、総件数と理由別表示に必要なコードを取得できることを確認する。
- Problem Detailsと通常ログに入力値、絶対パス、認証情報、一時ID、および内部例外が含まれないことを確認する。
- 検索語がURL、ブラウザ履歴、およびアクセスログに残らないことを確認する。
- アセットUUIDから管理対象実体だけが配信され、パストラバーサルが成立しないことを確認する。
- 未認証、CSRF不正、外部Origin、およびLAN上の別端末から非公開APIを利用できないことを確認する。
- 各`commandType`が対応する専用payloadだけを受け付け、未知項目、クライアント指定UUID、
  重複した対象UUID、および用途の異なる一時選択IDを拒否することを確認する。
- スナップショットに人物全件が含まれず、選択墓所の人物だけをページ取得し、
  人物変更差分が該当墓所のキャッシュだけへ反映されることを確認する。
- 各Operation開始APIが許可した処理とDTOだけを受け付け、Projectのないインポート、
  ごみ箱Projectの復元、およびキャンセル可能段階が設計どおりであることを確認する。
- ファイル・フォルダ選択の成功、キャンセル、多重起動、用途不一致、期限切れ、
  明示無効化、および絶対パス非公開を確認する。
- CSRFトークンが認証済みレスポンスヘッダーからだけ取得でき、メモリ外へ保存されず、
  セッション再生成時に古いトークンで状態変更できないことを確認する。
- 採番プレビューが状態を変更せず、期限切れ、使用済み、およびrevision競合時に
  `numberGraves`を適用しないことを確認する。
