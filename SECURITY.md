# Security Policy

## Supported Versions

このリポジトリは学習用サンプルアプリのため、バージョン管理されたリリースは行っていません。セキュリティ修正は `main` ブランチに直接適用します。

## Reporting a Vulnerability

脆弱性を発見された場合は、**公開 Issue を作成しないでください**。以下の手順でご報告をお願いします。

1. **GitHub Security Advisories** から非公開でレポートを送信
   - リポジトリの **Security** タブ → **Report a vulnerability**
   - 詳細: [GitHub Docs - Privately reporting a security vulnerability](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)

### Response Time

- **初回応答**: 7 営業日以内
- **修正公開**: 内容の重大度に応じて協議

## Scope

本アプリの想定動作:

- GitHub の **公開 Search API** を認証なしで利用
- 個人情報の収集・送信・保存は **行いません**
- お気に入り情報（公開リポジトリのメタデータのみ）を Room でローカル保存
- Hilt EntryPointAccessors 経由で Glance Widget が同じ Room を参照

## Out of Scope

以下は本リポジトリのスコープ外です:

- 依存ライブラリ（Retrofit / OkHttp / Room / Hilt / Glance 等）の脆弱性 → 各 upstream に報告してください
- 物理アクセス・root 化端末を前提とした攻撃
- ソーシャルエンジニアリング
- フォーク後の独自改変による脆弱性
- ProGuard/R8 無効化（本リポジトリはサンプル目的で `isMinifyEnabled = false`。本番利用時は必ず有効化してください）

## Security Best Practices

本アプリで実装済みのセキュリティ対策:

- ✅ HTTPS 強制（`res/xml/network_security_config.xml` で `cleartextTrafficPermitted="false"`）
- ✅ URL のサニタイズ（`Uri.encode(fullName, "/")` + `https://` スキーム検証）
- ✅ SQL インジェクション耐性（Room `@Query` のパラメータ化）
- ✅ 認証情報・API キーのハードコードなし
- ✅ 機密データのログ出力なし
- ✅ 最小パーミッション（`INTERNET` のみ）
- ✅ Widget Receiver の `exported="false"`
- ✅ DB バックアップ除外（`backup_rules.xml` + `data_extraction_rules.xml`）
