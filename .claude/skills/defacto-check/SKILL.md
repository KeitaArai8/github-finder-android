---
name: defacto-check
description: 公式 defacto reference (Apple docs / Google docs / NiA / 各種 Releases) を巡回し、前回スナップショットからの差分を検出する。検出した変更は GitHub Issue として自動起票し、本 repo の実装メンテナンスを促す。手動実行型 (Phase 1)。`/defacto-check`、`/defacto-check --dry-run`、`/defacto-check --source=apple-swiftui` などで使用する。
---

# /defacto-check — 公式 defacto 自動追従ハーネス (Phase 1)

このスキルは **本 repo 限定** で動作する。`.claude/skills/defacto-check/sources.yaml` に定義された defacto reference を巡回し、変更を検出した上で本 repo に Issue を自動起票する。

## 引数

| 引数 | 意味 | デフォルト |
|---|---|---|
| `--dry-run` | Issue を起票せず Markdown レポートを stdout に出力 | false |
| `--source=<id>` | 特定 source (例: `apple-swiftui`) のみチェック | 全 source |
| `--force` | state を無視して全 source を新規扱いで検査 (state の prime 用) | false |
| `--no-noise-filter` | sources.yaml の `noise_filter.keywords` を無視して全変化を報告 | false |

## sources.yaml の `role` フィールド

各 source は `role:` で監視の位置づけを持つ。

| role | 対象 | 扱い |
|---|---|---|
| `design` | 公式アーキテクチャガイド・公式サンプル (Now in Android) | 設計移行 (Nav2→Nav3、UI イベント処理方針など) の根拠になりうる |
| `version` | Google/Apple 所有ライブラリ・ツールチェーンのリリースノート | バージョン bump、compileSdk/targetSdk 要件、deprecation を駆動する |
| `third-party` | Google 以外の OSS (Coil, Retrofit, OkHttp, Turbine) | 本 repo は Now in Android の採用に追従するため、各ライブラリ自身のリリースではなく NiA の version catalog 経由で監視する |
| `official` | iOS 用。Apple が唯一の公式ソースなので全 source がこれ | 層の区別なし |

## 前提条件

実行前に以下を満たしていること:

1. **`gh` CLI 認証済み**: `gh auth status` で OK 表示
   - 失敗する場合は `gh auth login` で認証
2. **GitHub repo 作成済み** (Issue 起票時のみ必要):
   - `git remote get-url origin` で github.com URL が返る
   - 未作成なら `--dry-run` で利用
3. **本 repo に以下のラベル登録**:
   - `defacto-watch` (この skill が起票した Issue の identifier)
   - `severity:info` / `severity:migration` / `severity:breaking`
   - 未作成でも skill は動作する (gh が自動でラベル無しの Issue を作る)

## ワークフロー

以下の順序で必ず実行する。

### Step 1: 引数の解析と環境確認

1. ユーザの呼び出しから `--dry-run`, `--source=...`, `--force`, `--no-noise-filter` を抽出
2. `gh auth status` を実行し認証状態を確認 (`--dry-run` 時はスキップ可)
3. `git remote get-url origin` から GitHub repo URL を取得
   - `git@github.com:<owner>/<repo>.git` または `https://github.com/<owner>/<repo>` 形式から `<owner>/<repo>` を抽出
   - sources.yaml の `repo_override.github` が non-null ならそちらを優先
   - origin が無く override も無ければ自動的に `--dry-run` モードに切り替え (Issue 起票せず stdout 出力のみ) し、ユーザに通知

### Step 2: sources.yaml の読み込み

`Read` ツールで `.claude/skills/defacto-check/sources.yaml` を読み、`sources` 配列を取得する。`--source=<id>` 指定時は該当 1 件のみに絞る。

### Step 3: 既存 state の読み込み

`.claude/skills/defacto-check/state/state.json` を `Read` で読む。存在しなければ空 `{}` として扱う。state の構造:

```json
{
  "<source-id>": {
    "last_checked": "2026-04-30T10:00:00Z",
    "signature": "sha256:abc..." or "commit:<sha>" or "release:<tag>",
    "summary": "前回観測時のページ概要 (web_doc 用)"
  }
}
```

### Step 4: 各 source のチェック

各 source について type 別に処理する。`--force` 時は state を空として扱う。

#### type: `web_doc`

1. `WebFetch` でページを取得し、Claude として「ページの要点を 200 字以内で要約」してもらう
2. state に signature がある場合: 前回の summary と今回の要約を比較。`noise_filter.keywords` のいずれかが今回の要約に含まれていれば「変更あり」、含まれていなければ skip
3. state に signature が無い (初回): 「初回観測」として要約のみ記録、Issue は起票しない (priming)
4. 変更検出時: WebFetch 結果を再度精読し、具体的な変更点 (新 API、deprecated、推奨パターン変更など) を抽出してレポートに含める

#### type: `github_releases`

1. `gh api repos/<repo>/releases/latest --jq '.tag_name + "|" + .published_at + "|" + .name'` で最新リリースを取得
2. state の signature (`release:<tag>`) と比較
3. tag が変わっていれば: `gh api repos/<repo>/releases/latest --jq '.body'` でリリースノートを取得し、`noise_filter.keywords` を含むかチェック
4. 含めば「変更あり」、含まなければ「該当変更無し (バージョン bump のみ)」として info レベルで記録

#### type: `github_commits`

1. `gh api repos/<repo>/commits/<branch> --jq '.sha + "|" + .commit.author.date'` で latest commit を取得
2. state の signature (`commit:<sha>`) と比較
3. SHA が変わっていれば: `gh api repos/<repo>/commits?since=<state.last_checked>&sha=<branch> --jq '.[] | .sha + " " + .commit.message'` で当該期間のコミット一覧を取得
4. コミットメッセージに `noise_filter.keywords` を含むものがあれば「変更あり」として該当コミット群を抽出

### Step 5: 変更の重要度分類

検出した各変更について以下のいずれかに分類する:

| Severity | 判定基準 | ラベル |
|---|---|---|
| `breaking` | "BREAKING", "Removed", "Discontinued" 等を含む / 既存実装が壊れる可能性 | `severity:breaking` |
| `migration` | "Deprecated", "Replaced by", "renamed" 等 / 段階的移行が必要 | `severity:migration` |
| `info` | 新 API 追加 / docs 構成変更 / 推奨ガイダンス調整 | `severity:info` |

判定は Claude が変更内容を読んで決定する。

`role` による追加ルール:

- `design` role の変更に移行系キーワード (Deprecated / Replaced by / 新推奨パターン等) が含まれる場合、`migration` に分類するのは **公式ガイドと NiA の両方が新パターンを示しているとき** に限る。片方だけなら `info` とし、もう一方の追従を待つ旨を本文に記す（ガイドだけでは参照実装が無く、NiA だけではサンプル固有の判断かもしれないため、両方そろった時点が移行の根拠になる）。
- `third-party` role の報告根拠は NiA の version catalog の変更のみとする（本 repo は第三者 OSS の採用判断を NiA に委ねているため、アップストリームのリリースは監視対象外）。

### Step 6: 現実装との照合

検出した変更が **本 repo の実装に該当するか** を可能な範囲で確認する:

1. iOS の場合: `grep -r "<関連 API 名>" Sources/ App/ Packages/` 等で該当 API の使用箇所を探す
2. Android の場合: `grep -r "<関連 API 名>" app/ feature/ core/` 等で同様に探す
3. 該当箇所があれば Issue 本文に「現実装での該当箇所」セクションを追加 (ファイルパス + 行番号)
4. 該当箇所が無ければ「現実装には該当無し (情報のみ)」と記載し、severity を `info` に降格

### Step 7: Issue 起票 (or dry-run 出力)

各変更について以下のフォーマットで Issue を作成する。

#### Issue タイトル

```
[defacto-watch] <source-id>: <短い変更要約>
```

例:
- `[defacto-watch] apple-swiftui: View.onChange の引数仕様が更新`
- `[defacto-watch] nia: NiA が ksp から ksp2 に移行`
- `[defacto-watch] kotlin-releases: Kotlin 2.X リリース (breaking change あり)`

#### Issue 本文 (Markdown)

```markdown
## 概要

<変更の 2-3 行要約>

## 出典

- **Source**: <source-name>
- **URL**: <source-url>
- **検出日**: <YYYY-MM-DD>
- **Severity**: <breaking|migration|info>

## 変更内容

<具体的な変更点。API 名、新シグネチャ、deprecated になったもの等>

## 現実装での該当箇所

<grep で見つかった箇所、または「該当無し」>

- `path/to/file.swift:42` - <該当箇所の説明>

## 推奨アクション

<対応方針: 「migration: 〜に置換」「breaking: 〜を見直し」「info: 動向監視のみ」>

## defacto reference

- <参照 URL 1>
- <参照 URL 2>

---
🤖 Generated by `/defacto-check` skill
```

#### gh issue create コマンド

```bash
gh issue create \
  --repo "<owner>/<repo>" \
  --title "[defacto-watch] <source-id>: <short summary>" \
  --label "defacto-watch,severity:<level>" \
  --body "$(cat <<'EOF'
<上記本文>
EOF
)"
```

`--dry-run` 時は `gh issue create` の代わりに stdout に Markdown 出力 (リポート形式)。

### Step 8: state.json の更新

検出処理が完了したら、各 source の signature を更新して `state/state.json` に書き戻す。

- web_doc: 今回の要約を `summary` に保存、`signature` には要約の SHA256 (上位 16 桁) を保存
- github_releases: 最新 tag を `release:<tag>` 形式で保存
- github_commits: 最新 SHA を `commit:<sha>` 形式で保存
- `last_checked` は現在の ISO 8601 (UTC) を記録

### Step 9: 最終レポート

ユーザに以下を表示:

```
✅ defacto-check 完了

巡回した source: <count>
変更を検出した source: <count>
起票した Issue: <count> (dry-run 時はゼロ)
スキップ (変更なし): <count>
エラー: <count>

起票した Issue:
  - #<number> [defacto-watch] <source-id>: <title>
  - ...

state ファイル: .claude/skills/defacto-check/state/state.json (更新済み)
```

## エラーハンドリング

| 状況 | 動作 |
|---|---|
| `gh` が未認証 | エラー表示しスキル中断。`gh auth login` を案内 |
| WebFetch 失敗 | 該当 source は skip し、最終レポートに「エラー」として計上。state は更新しない |
| `gh issue create` 失敗 | エラー出力 + state は更新しない (次回再試行可能) |
| state.json が壊れている (JSON parse 失敗) | state を空として扱い、ユーザに警告表示 + バックアップを取る (`state.json.broken-<timestamp>`) |
| labels が未作成 (`severity:*` 等) | gh が「ラベルなし」で起票する。ユーザに「ラベル作成推奨」を最終レポートで案内 |

## 初回実行 (priming)

state.json が存在しない初回実行では、すべての source を「初回観測」として現状を記録するのみで Issue は 1 件も起票しない。これにより 2 回目以降の実行から純粋な差分のみが検出される。

ユーザに「初回 priming 完了。次回実行から差分検出が開始されます」と通知する。

## ラベル準備 (推奨セットアップ)

初回実行前に以下を実施しておくと、Issue が綺麗に分類される:

```bash
gh label create "defacto-watch" --description "Auto-filed by /defacto-check skill" --color "0075ca"
gh label create "severity:info" --description "Informational, no action required" --color "0e8a16"
gh label create "severity:migration" --description "Deprecated APIs, gradual migration" --color "fbca04"
gh label create "severity:breaking" --description "Breaking change, immediate attention" --color "d73a4a"
```

## 設計メモ

- **Phase 1 (本スキル)**: 手動実行のみ。スケジューラ無し
- **Phase 2 (将来)**: cron + scheduled-tasks で週次自動化
- **Phase 3 (将来)**: 軽微変更 (deprecated 置換等) を auto-PR 化
- 本 skill は repo-local。clone した人がそのまま `/defacto-check` を使える
- state は `.gitignore` 対象 (ローカルキャッシュ扱い)
