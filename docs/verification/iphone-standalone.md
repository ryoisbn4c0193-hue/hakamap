# iPhone単独アプリの作成とオフライン確認

状態: ビルド設定・確認手順を準備済み。署名済みIPAの生成・インストール・機内モード試験は未実施。

## 配布方式

EAS Buildの`preview`で、登録済みiPhone向けのAd Hoc配布アプリを作る。
Release構成でJS・検索欄のDOM・共通マニュアルを同梱し、Expo GoやPCのMetroを使わず起動する。
`developmentClient`は無効。TestFlight提出やApp Store公開は行わない。

Windows PCからクラウドビルドできるが、Apple Developer Program（有料）の加入と、
Expo・Appleアカウントでの設定が必要。加入・課金は自動で行わない。
[Expo公式の内部配布手順](https://docs.expo.dev/build/internal-distribution/)を参照。

## Windows PCでの作成

実データの`.hakamap`や署名ファイルはリポジトリ内へ置かない。
Expo・Appleへのログインと認証コード入力はPC上で行い、チャットへパスワードを送らない。
以下は一行ずつ実行する。

```powershell
cd D:\ChatGPT\hakamap\hakamap-iphone
git pull --ff-only
cd mobile
npm ci
npx eas-cli@latest whoami
```

未ログインなら`npx eas-cli@latest login`でExpo Goで使ったアカウントにログインする。

```powershell
npx eas-cli@latest init
npx eas-cli@latest device:create
```

- `init`では自分のExpoアカウントにプロジェクトを作成・関連付ける。生成されるプロジェクトIDは実際の値を使用する。
- `device:create`では案内のURLをテストするiPhoneのSafariで開き、端末登録を完了する。
- Bundle Identifierは`jp.hakamap.mobile`。Appleアカウントで使用できない場合は、利用可能な固有値へ`app.json`を変更する。
- 共有マニュアル`shared/`もビルドに必要なので、リポジトリ全体を保持して`mobile`から実行する。

```powershell
npx eas-cli@latest build --platform ios --profile preview
```

Appleのログイン・2段階認証を案内に従って行う。証明書・Provisioning ProfileをEASで生成し、
先ほど登録したiPhoneを配布対象に含める。シミュレーター用ではなく`preview`を選ぶ。
EASに料金が表示される場合は内容を確認してから進める。

ビルド完了後のインストールURLをiPhoneのSafariで開き、アプリをインストールする。
端末からデベロッパモード等の設定を求められた場合は、案内に従って設定する。
署名の有効期限があるため、この配布方式で無期限に起動できるとは扱わない。

## オフライン確認

Expo Goとはアプリの保存領域が別なので、最初は`.hakamap`を取り込み直す。
通信できる間にインストールとファイルのダウンロードを済ませ、
ファイルは「このiPhone内」など端末へ保存する。クラウド上だけのファイルを使わない。

| 手順 | 期待結果 | 結果 |
| --- | --- | --- |
| ホーム画面のHakamapを直接起動 | Expo Go・QR読み取り・Metroなしで開く | 未実施 |
| `.hakamap`を取り込む | Windowsと同じ配置図・背景・墓所名が出る | 未実施 |
| PCのExpoサーバーをCtrl+Cで停止 | アプリを使い続けられる | 未実施 |
| iPhoneを機内モードにし、Wi-Fiもオフにする | 通信を使わない状態になる | 未実施 |
| Hakamapを完全終了しホーム画面から起動 | 保存済みの背景・配置図が出る | 未実施 |
| 日本語で入力・変換確定し、検索対象を切り替える | 検索欄が使え、該当墓所が赤くなって画面に収まる | 未実施 |
| 墓所をタップする | 墓所名・人物氏名が表示される | 未実施 |
| 拡大縮小・移動・名前表示切替 | 配置図を操作できる | 未実施 |
| 「使い方」を開く | マニュアル全文を読める | 未実施 |
| 名前を非表示にして完全終了・再起動 | 設定と取り込み済みデータが残る | 未実施 |
| iPhoneを再起動し通信オフのままHakamapを開く | 配置図・検索・詳細が使える | 未実施 |

試験終了後は機内モード・Wi-Fiを元へ戻す。
記録にはアプリバージョン、EASのビルドID、iPhone機種、iOS、項目ごとの成否を残す。
実データや人名を含む画面はGitHubへ添付しない。
