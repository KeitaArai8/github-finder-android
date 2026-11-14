# Contributing to GitHubFinder (Android)

本プロジェクトへのコントリビューションに興味を持っていただきありがとうございます。このドキュメントは、Issue 報告・PR 送付時のガイドラインをまとめたものです。

## 前提

本リポジトリは **学習・教育目的のサンプルアプリ** です。2026 年時点の Android デファクトスタンダード（Jetpack Compose + ViewModel + StateFlow + Hilt + Room + Material 3 + Glance Widget）に準拠した、[Now in Android](https://github.com/android/nowinandroid) 相当のリファレンス実装を目指しています。

本質的なアーキテクチャ（UDF / SSOT / 単一状態集約 / モジュール依存方向）を逸脱する大規模な変更は、Discussion で事前に方向性を合意してから PR をお願いします。

## セットアップ

1. Android Studio（最新安定版）をインストール
2. リポジトリをフォーク & クローン
3. Android Studio で `GitHubFinderAndroid/` を開く
4. Gradle Sync を実行
5. エミュレータ / 実機を選んで Run

### 識別子の置換

フォーク直後はそのままビルドできますが、リリース前は [README](./README.md#フォーク時の識別子置換) の手順で自身の `applicationId` / `namespace` / `package` に置換してください。

## ブランチ命名

| 用途 | 命名 | 例 |
|---|---|---|
| 新機能 | `feature/<short-name>` | `feature/dark-mode-toggle` |
| バグ修正 | `fix/<issue-id>-<short-name>` | `fix/123-favorite-state` |
| ドキュメント | `docs/<short-name>` | `docs/widget-setup` |
| リファクタ | `refactor/<short-name>` | `refactor/search-viewmodel-split` |

## コミットメッセージ

[Conventional Commits](https://www.conventionalcommits.org/) に従ってください。

```
<type>: <subject>

[optional body]

[optional footer]
```

### type の例

- `feat`: 新機能
- `fix`: バグ修正
- `docs`: ドキュメントのみ
- `style`: コード整形（挙動変更なし）
- `refactor`: リファクタリング
- `test`: テスト追加・修正
- `chore`: その他（依存更新、CI 設定等）

### 例

```
feat: お気に入りの一括選択削除機能を追加

FavoriteViewModel に編集モード state を追加し、選択中のアイテムを
まとめて削除できるようにした。AlertDialog で誤操作を防止。

Closes #42
```

## コードスタイル

- **Kotlin 公式コーディング規約** に準拠
- `./gradlew lint` で新規 warning を発生させない
- 1関数は **25 行以内**を目安に
- 引数 3 つ以上は**1 引数ごとに改行**
- `guard`的な早期 return を優先
- **Composable 内でビジネスロジックを書かない**（Stateless Composable 原則）
- 各 Screen の状態は **単一 `sealed interface UiState`** に集約

詳細は既存コードのパターンを参考にしてください。

## モジュール構成ルール

新規ファイル追加時は以下の依存方向を守ってください:

```
:app → :feature:{search, favorite, settings, widget}
     → :core:{data, network, database, designsystem, model}
:feature:* → :core:data, :core:designsystem, :core:model
:core:data → :core:{network, database, model}
:core:{network, database, designsystem} → :core:model
:core:testing → :core:{data, model}   （テスト専用。各モジュールは testImplementation でのみ参照）
```

- `:feature:*` 間は**相互参照しない**
- `:core:model` は **純粋な Entity** のみ（Room / Retrofit 依存を持ち込まない）
- チュートリアル関連の状態（`CoachMarkManager` 等）は `:core:designsystem/tutorial/` に配置

## テスト

- ViewModel を変更する場合は **対応するテストを必ず追加・更新**
- Data 層（Repository / DataSource）変更時も同様
- **Fake 注入** パターンで Hilt を bypass。複数モジュールで使う Fake（`FakeFavoriteRepository` 等）は `:core:testing` に置き、そのモジュールだけで使う Fake は各モジュールの `src/test/` 配下に置く
- AAA パターン（`// Arrange:` / `// Act:` / `// Assert:`）のコメントを各テストに明記

### テスト実行

```bash
./gradlew test                             # 全 unit test
./gradlew :feature:search:test             # 特定モジュールのみ
./gradlew :feature:search:connectedAndroidTest  # Compose UI Tests（要エミュ）
```

## Preview の追加

新規 Composable を追加する場合、可能な限り `@Preview` を追加してください。stateful な Screen は内部の stateless Composable（`RepositoryRow` 等）に対して Preview を作成するのが推奨パターンです。

## PR の送付

1. フォークしたリポジトリに push
2. 元リポジトリへ Pull Request を作成
3. PR テンプレートに従って内容を記述
4. CI (Build & Test & Lint) が緑になることを確認
5. レビューコメントに対応

### マージ基準

- CI 緑
- 最低 1 名のレビュー承認
- コンフリクトなし
- PR テンプレートのチェックリストを全てクリア

## 多言語対応

ハードコードされた文字列を追加しないでください。必ず各モジュールの `res/values/strings.xml` に追加し、英語訳を `res/values-en/strings.xml` にも追加してください。

## セキュリティ脆弱性

公開 Issue は作成せず、[SECURITY.md](./SECURITY.md) の手順で報告してください。

## 行動規範

コントリビューター全員が [Code of Conduct](./CODE_OF_CONDUCT.md) を遵守してください。

## ライセンス

コントリビュートされたコードは [MIT License](./LICENSE) で公開されることに同意したものとみなします。
