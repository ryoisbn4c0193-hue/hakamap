# コーディング規約

## Java

JavaコードはGoogle Java Styleを基礎とし、Spotlessの`google-java-format`で整形します。

```bash
cd backend
./mvnw spotless:apply
./mvnw spotless:check
./mvnw checkstyle:check
./mvnw test
```

- クラスとrecordはUpperCamelCase、メソッドと変数はlowerCamelCase、定数はUPPER_SNAKE_CASEにします。
- wildcard importを使用せず、不要なimportを残しません。
- DTOはAPI入出力、Entityは永続化、Repositoryはデータアクセスに責務を限定します。
- Serviceはユースケースとトランザクションを担当し、ControllerはHTTP入出力の変換に専念します。
- EntityをAPIレスポンスとして直接公開しません。

## ReactとTypeScript

ReactとTypeScriptはAirbnb JavaScript Style Guideを基礎とし、ESLintとPrettierに従います。

```bash
cd frontend
pnpm lint
pnpm lint:fix
pnpm format
pnpm format:check
pnpm test
```

- コンポーネントと型はUpperCamelCase、関数と変数はlowerCamelCaseにします。
- Hooksは`use`から始め、React Hooksのルールに従います。
- importは外部、内部、相対参照のグループに分け、各グループ内をアルファベット順にします。
- コンポーネントは表示と操作に集中させ、再利用する状態ロジックはカスタムHookへ分離します。
- API通信は専用モジュールへ集約し、Reactコンポーネントから`fetch`を直接呼び出しません。
- サーバー状態はTanStack Query、画面内の状態はReactまたはZustandで管理します。

## AIエージェント

AIエージェントも同じ規約に従います。変更後は対象に応じてformat、lint、testを実行し、
エラーを残したまま完了としてはいけません。実行できない検査がある場合は理由を報告します。
