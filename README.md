# GitHubFinder (Android)

GitHubのリポジトリをキーワードで検索し、お気に入りに保存できるAndroidアプリです。2026 年時点のデファクトスタンダード（Jetpack Compose + ViewModel + StateFlow + Hilt + Room + Material 3 + Glance Widget）に準拠した学習サンプル実装です。

## 技術スタック

| 項目 | 選定 |
|------|------|
| UI | Jetpack Compose |
| 状態管理 | ViewModel + StateFlow (UDF 単一状態) |
| DI | Hilt |
| 永続化 | Room |
| ネットワーク | Retrofit + kotlinx.serialization |
| ナビゲーション | Navigation Compose |
| Widget | Glance + Material3 for Glance |
| テスト | JUnit4 + Turbine + Compose UI Tests |
| minSdk | 26 (Android 8.0) |
| デザイン | Material 3 |

## アーキテクチャ

[Android公式アーキテクチャガイド](https://developer.android.com/topic/architecture)に準拠しています。

### 設計原則

| 原則 | 説明 |
|------|------|
| **UDF（単方向データフロー）** | データは Data→UI へ流れ、イベントは UI→Data へ流れる |
| **SSOT（単一信頼源）** | お気に入り→Room DB、検索結果→API+メモリキャッシュ |
| **単一状態集約** | 各 Screen の状態は 1つの `sealed interface UiState` に集約 |
| **不変データの公開** | UiState は不変の data class / sealed interface。ViewModel のみが更新 |
| **関心の分離** | Screen は表示のみ、ロジックは ViewModel に集約 |
| **テスタビリティ** | interface ベースの DI (Hilt) でモック差し替え可能 |

### レイヤー構成

```
┌─────────────────────────────────┐
│         UI Layer                │
│  Screen (Compose) ← 表示のみ    │
│  ViewModel + StateFlow ← UDF   │
└──────────────┬──────────────────┘
               ↓
┌─────────────────────────────────┐
│         Data Layer              │
│  Repository ← DataSource統合    │
│  ├── RemoteDataSource (Retrofit)│
│  ├── LocalDataSource (Room)     │
│  └── CacheDataSource (メモリ)   │
└─────────────────────────────────┘
```

Domain Layer（UseCase）は設けていません。このアプリの規模では不要と判断しました（Android公式でもオプションと明記）。

### UDF フロー

```
[Data Layer]                    [UI Layer]
Repository ──── UiState ────→ ViewModel ────→ Screen
    ↑                              │             │
    └──── User Event ──────────────┘←────────────┘
```

### 単一 UiState 集約（sealed interface）

UDF の「状態集約」原則に厳密準拠し、Screen の **画面の見た目を表す状態** は 1 つの sealed interface に統合しています。

```kotlin
sealed interface SearchUiState {
    val searchQuery: String
    data object Idle(val history: List<String> = emptyList(), ...) : SearchUiState
    data class Loading(...) : SearchUiState
    data class Loaded(items, isLoadingMore, isRefreshing, ...) : SearchUiState
    data class Empty(...) : SearchUiState
    data class Error(val message: String, ...) : SearchUiState
}
```

`Loaded` の中に `isEditMode`・`selectedIDs` などサブ状態を持たせることで、**Loaded 状態内の状態変化**もデータクラス1つで構造化しています。

### 一度きりイベント（Channel + UiEvent）

「SnackBar 表示」「ナビゲーション」のような **一度しか発火しないイベント** は State には含めず、`Channel<UiEvent>` 経由で発火する Compose defacto パターンを採用:

```kotlin
sealed interface SearchUiEvent {
    data class ShowSnackBar(val message: String) : SearchUiEvent
}

@HiltViewModel
class SearchViewModel : ViewModel() {
    private val _events = Channel<SearchUiEvent>(Channel.BUFFERED)
    val events: Flow<SearchUiEvent> = _events.receiveAsFlow()

    fun toggleFavorite(...) {
        try { ... } catch (_: Exception) {
            _events.send(SearchUiEvent.ShowSnackBar("お気に入りの更新に失敗しました"))
        }
    }
}

// Composable 側
LaunchedEffect(Unit) {
    viewModel.events.collect { event ->
        // showSnackbar は Material 3 公式 API のため命名そのまま使用
        is SearchUiEvent.ShowSnackBar -> snackBarHostState.showSnackbar(event.message)
    }
}
```

**メリット**: 画面回転・プロセス復帰時に古い SnackBar が再表示されない / 「画面の状態」と「一度きりのアクション」の通信路を分離できる。

### combine + stateIn によるリアクティブ State 合成（SearchViewModel）

複数の非同期入力源（フェーズ・お気に入り ID・検索履歴）から最終 UiState を合成する場合は、`combine + stateIn` で **宣言的に合成** する:

```kotlin
private val _phase = MutableStateFlow<SearchPhase>(SearchPhase.Idle())

val uiState: StateFlow<SearchUiState> = combine(
    _phase,
    favoriteStore.favoriteIDs,
    searchHistoryDataSource.history,
) { phase, favoriteIDs, history ->
    phase.toUiState(favoriteIDs, history)  // 純粋な変換関数
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SearchUiState.Idle(),
)
```

**メリット**:
- お気に入り ID / 検索履歴が更新されたら現在のフェーズに自動反映される（手動キャッシュ不要）
- 全ての非同期入力が「単一の純粋な変換関数」に集約され、テストしやすい
- フェーズ遷移時に latestHistory を都度参照する手動同期コードが消える

## モジュール構成（NiA準拠）

[Now in Android](https://github.com/android/nowinandroid) のモジュール設計思想に準拠し、10 モジュール + `build-logic`（Convention Plugin）の構成です。

```
:build-logic/convention  ← Convention Plugin（共通 Gradle 設定を集約）
:app                    ← エントリポイント + Navigation + Hilt + TutorialScreen
:core:model             ← Entity + AppError（共有型）
:core:data              ← Repository interface/impl + CacheDataSource + FavoriteStore + SearchHistoryDataSource
:core:network           ← Retrofit + DTO + RemoteDataSource + DI
:core:database          ← Room + Dao + DbEntity + LocalDataSource + DI
:core:designsystem      ← Material 3 Theme + 共通UIコンポーネント + BrowserLauncher + Tutorial関連
:feature:search         ← SearchViewModel + SearchScreen + SearchUiState + SearchUiEvent
:feature:favorite       ← FavoriteViewModel + FavoriteScreen + FavoriteUiState + FavoriteUiEvent
:feature:settings       ← SettingsScreen
:feature:widget         ← Glance Widget (FavoriteWidget)
```

### Convention Plugin（`build-logic/`）

NiA と同じく、`build-logic/convention/` に共通 Gradle 設定を集約。各モジュールの `build.gradle.kts` は Plugin alias と固有依存だけを記述する：

```kotlin
// feature/favorite/build.gradle.kts（採用後・5 行）
plugins {
    alias(libs.plugins.githubfinder.android.feature)
}
android { namespace = "com.example.githubfinder.feature.favorite" }
dependencies { implementation(project(":core:data")) }
```

提供される Convention Plugin:

| Plugin ID | 役割 | 適用モジュール例 |
|-----------|------|----------------|
| `githubfinder.android.application` | App ターゲット共通設定（compileSdk/minSdk/Java/Kotlin/buildConfig） | `:app` |
| `githubfinder.android.application.compose` | App + Compose | `:app` |
| `githubfinder.android.library` | Library 共通設定 | `:core:model` 等 |
| `githubfinder.android.library.compose` | Library + Compose（BOM 自動注入） | `:core:designsystem` 等 |
| `githubfinder.android.hilt` | Hilt + KSP + hilt-android 注入 | `:core:data` 等 |
| `githubfinder.android.feature` | Library + Compose + Hilt + lifecycle + coil + テスト共通（Feature 集約） | `:feature:search`, `:feature:favorite`, `:feature:settings` |

### モジュール依存関係

```
:app
├── :core:model
├── :core:data
├── :core:network
├── :core:database
├── :core:designsystem
├── :feature:search
├── :feature:favorite
├── :feature:settings
└── :feature:widget

:feature:search / :feature:favorite
├── :core:model
├── :core:data
└── :core:designsystem

:feature:settings
└── :core:designsystem

:feature:widget
├── :core:model
└── :core:data

:core:data
├── :core:model
├── :core:network
└── :core:database

:core:network / :core:database
└── :core:model

:core:designsystem
└── (Hilt / Compose / Browser / AndroidX)
```

### :core:data の役割

`:core:data` は「データアクセスの統合レイヤー」です。

```
:core:data
├── Repository interface           ← データアクセスの契約
├── Repository impl                ← Remote + Local + Cache を統合
├── SearchCacheDataSource          ← Repository の内部実装詳細（外部に公開しない）
├── SearchHistoryDataSource (i/f)  ← 検索履歴の永続化契約
├── SearchHistoryDataSourceImpl    ← DataStore Preferences 実装（テスト時は Fake 差し替え）
├── FavoriteStore                  ← お気に入りのメモリ同期プール（@Singleton）
├── NetworkMonitor                 ← ConnectivityManager → Flow<Boolean>
└── DI Module                      ← interface ↔ impl のバインド
```

### :core:designsystem の拡張された役割

`:core:designsystem` は UI 共通ライブラリに加え、**横断的関心事のインフラ**も提供します:

```
:core:designsystem
├── Theme / EmptyStateView / ErrorView     ← UI コンポーネント
├── BrowserLauncher                        ← Custom Tabs 起動ユーティリティ
├── GitHubUrl (githubUrlFor)               ← URL 組み立てヘルパー
└── tutorial/                              ← CoachMarkManager, CoachMarkStep,
                                             CoachMarkAnchor, TutorialLocalDataSource
```

チュートリアル関連の `CoachMarkManager` はアプリ全体で共有されるため `:core:designsystem` 配下に配置し、`:feature:*` からも Hilt 経由で参照できるようにしています。

### DataSource の分離

Android公式に従い、各DataSourceは**単一のデータソースのみ**を処理します。

| DataSource | モジュール | 責務 | データソース |
|-----------|----------|------|------------|
| `GitHubRemoteDataSource` | `:core:network` | GitHub API 通信のみ | Retrofit |
| `FavoriteLocalDataSource` | `:core:database` | お気に入り CRUD のみ | Room |
| `SearchCacheDataSource` | `:core:data` | 検索結果のメモリキャッシュ | HashMap |

### SSOT（単一信頼源）

| データ | SSOT | 理由 |
|--------|------|------|
| お気に入り | Room DB (ローカル) | アプリ終了後も保持。唯一の真実。Widget からも同じ DB を読む |
| 検索結果 | API + メモリキャッシュ | API が正。キャッシュはアプリ起動中のみ |
| チュートリアル表示済み | SharedPreferences | ローカルフラグ |

### お気に入り設計（Passive View + SSOT）

Entity にフラグを持たせず、ViewModel 内で表示用データクラス `RepositoryDisplayItem` に変換して公開します。Screen は受け取った値をそのまま表示するだけです。

```
SearchViewModel（内部）
├── allItems: List<RepositoryEntity>       ← API から取得した生データ
├── favoriteStore.favoriteIDs: Set<Int>    ← FavoriteRepository (SSOT = DB)
└── buildDisplayItems()                    ← 突き合わせて RepositoryDisplayItem に変換

SearchViewModel（公開）
└── uiState: StateFlow<SearchUiState>      ← Screen はこれを表示するだけ
```

### オフライン検知（NetworkMonitor）

`:core:data/network/NetworkMonitor.kt` で `ConnectivityManager` を `callbackFlow` でラップし、`Flow<Boolean>` として公開。`MainActivity` で Hilt inject、`MainNavigation` で `collectAsStateWithLifecycle` 観察:

```kotlin
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    val isConnected: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        // registerNetworkCallback / awaitClose で unregister
    }.distinctUntilChanged()
}
```

UI 層は `AnimatedVisibility(visible = !isConnected)` で `OfflineBanner`（`:core:designsystem`）をスライドイン表示。

### 構造化ログ（Timber）

`com.jakewharton.timber:timber:5.0.1` を採用。`GitHubFinderApplication.onCreate()` で `BuildConfig.DEBUG` のときのみ `DebugTree` を `plant()`。**Release ビルドでは Timber 呼び出しが no-op** になり、本番でログが流出しない設計。

```kotlin
class GitHubFinderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
```

各 ViewModel / NetworkMonitor で機能別 tag を付与:

| Tag | 用途 |
|---|---|
| `Search` | 検索 / API 呼び出し |
| `Favorite` | お気に入り CRUD / 一括削除 |
| `Network` | 接続状態変化 |

```kotlin
Timber.tag("Search").i("Search succeeded: count=%d", result.items.size)
Timber.tag("Search").e(exception, "Search failed (unknown)")
```

### Widget と本体の DB 共有

`:feature:widget` の `FavoriteWidget` (Glance) は、Hilt の `EntryPointAccessors` を経由して同じ `FavoriteRepository` を参照します。

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun favoriteRepository(): FavoriteRepository
}

// Widget 内
val repo = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
    .favoriteRepository()
val favorites = repo.getAll().take(5)
```

Widget は Hilt スコープ外で動作するため `@AndroidEntryPoint` は使えませんが、`EntryPointAccessors` で Singleton スコープの Repository を取得できます。

### エラーハンドリング

```
DataSource → 生の例外を throw
    ↓
Repository → catch して AppError に変換
    ↓
ViewModel → AppError を UiState.Error に反映
    ↓
Screen → エラーメッセージ + リトライボタン表示
```

AppError は 5分類（`Network` / `RateLimit` / `Server(code)` / `Decode` / `Unknown`）。

## DI（Hilt）

```
@Module NetworkModule
├── Json (kotlinx.serialization)
├── OkHttpClient
├── Retrofit
└── GitHubApiService

@Module DatabaseModule
├── AppDatabase (Room)
└── FavoriteDao

@Module DataModule (abstract, @Binds)
├── SearchRepository ← SearchRepositoryImpl
└── FavoriteRepository ← FavoriteRepositoryImpl

@Module TutorialModule (abstract, @Binds)
└── TutorialLocalDataSource ← TutorialLocalDataSourceImpl
```

自動注入:
- `GitHubRemoteDataSource` / `FavoriteLocalDataSource` / `SearchCacheDataSource` — `@Inject constructor`
- 各 ViewModel — `@HiltViewModel` + `@Inject constructor`
- `CoachMarkManager` — `@Singleton` + `@Inject constructor`

## 機能一覧

### メイン機能

| 機能 | 説明 |
|------|------|
| キーワード検索 | デバウンス付き（500ms）、GitHub API |
| **検索履歴** | 直近10件を `DataStore Preferences` に永続化、Idle 時に履歴一覧 / 行タップで即時再検索 / 個別削除 / 全消去 |
| 無限スクロール | 末尾3件手前でプリフェッチ |
| お気に入り登録/解除 | ハートボタン、Room 永続化 |
| お気に入り一覧 | スワイプ削除 / 行タップで GitHub ページを Custom Tabs で開く |
| **ソート（メモリ保持）** | 保存が新しい順 / Star 多い順 / 名前 A→Z（TopAppBar の DropdownMenu） |
| **言語フィルタ（メモリ保持）** | 登録済み言語を `FilterChip` で動的提示、トグルで絞り込み・解除（フィルタ中の言語が消失すると自動クリア） |
| **シェア** | `Intent.ACTION_SEND` の Chooser で GitHub URL をシェア（検索/お気に入り両画面の行に Share IconButton） |
| **一括選択削除** | 編集モードで複数選択 → AlertDialog 確認 → 一括削除 |
| メモリキャッシュ | 検索結果をアプリ起動中キャッシュ |
| プルリフレッシュ | 検索・お気に入り両画面（`PullToRefreshBox`） |
| **オフライン検知** | `ConnectivityManager` を `Flow<Boolean>` で公開、切断時に画面上部にバナー表示 |
| **構造化ログ** | Timber による tag 別 Logcat 出力（DEBUG ビルドのみ） |
| チュートリアル | **スポットライト型専用画面**（TutorialScreen）、初回起動時のみ |
| 設定画面 | プライバシーポリシー/利用規約（Custom Tab） |
| ダークモード | システム設定追従（Dynamic Color対応） |
| エラーハンドリング | 5分類（Network/RateLimit/Server/Decode/Unknown）+ リトライ |

### ホームウィジェット（Glance）

| 項目 | 内容 |
|------|------|
| 機能 | お気に入り上位5件をホーム画面に表示 |
| サイズ | resizable（horizontal+vertical）、デフォルト 3×2 セル |
| データ取得 | Hilt `EntryPointAccessors` 経由で `FavoriteRepository.getAll()` |
| 更新頻度 | `updatePeriodMillis=3600000`（1時間ごと） |
| タップ動作 | アプリ起動 |
| 空状態 | 「お気に入りがありません / 検索画面から追加してください」 |

### チュートリアル（スポットライト型）

専用の `TutorialScreen` がモック UI を組み、実際のアプリ UI と同じレイアウトに `Modifier.coachMarkAnchor` を付与してスポットライトを当てます。本機能実装には一切影響しません。

| ステップ | 操作 |
|---------|------|
| 1. 検索バー | モック TextField タップで次へ |
| 2. お気に入りボタン | モックハートタップで次へ |
| 3. お気に入りタブ | モックタブタップで完了 |

## セキュリティ

| 項目 | 対応 |
|------|------|
| HTTPS 強制 | `res/xml/network_security_config.xml` で `cleartextTrafficPermitted="false"` |
| URL 構築 | `Uri.encode(fullName, "/")` で自動 percent-encode（`githubUrlFor`） |
| パーミッション | `INTERNET` のみ（最小） |
| Receiver export | Widget Receiver は `exported="false"` |
| DB バックアップ除外 | `res/xml/backup_rules.xml` + `res/xml/data_extraction_rules.xml` で `github_finder.db` を除外 |
| SQL インジェクション | Room `@Query` のパラメータ化で型安全 |

## テスト

### Unit Tests（JUnit4 + Turbine + StandardTestDispatcher）

| モジュール | テストファイル | 件数 |
|---|---|---|
| `:feature:search` | SearchViewModelTest（FakeSearchRepository / FakeFavoriteRepository） | 13 |
| `:feature:favorite` | FavoriteViewModelTest（一括削除含む） | 15 |
| `:core:data` | SearchCacheDataSourceTest, SearchRepositoryImplTest, FavoriteRepositoryImplTest | 18 |
| `:core:database` | FavoriteLocalDataSourceTest（FakeDao） | 8 |

### UI Tests（Compose UI Tests）

- `:feature:search` — SearchScreenTest（3 CUJ: 初期表示 / 検索→結果 / クリアボタンで Idle 復帰）

### テスト手法のハイライト

- `FakeGitHubApiService`（GitHubApiService interface の Fake）で `HttpException` / `IOException` を発生させ、エラーマッピングを検証
- `FakeFavoriteDao`（in-memory HashMap）で Room 実装を bypass したハーメティックなテスト
- `StandardTestDispatcher` + `advanceUntilIdle()` で Coroutine の完了を決定論的に待機
- `// Arrange:` / `// Act:` / `// Assert:` の AAA コメント必須

## セットアップ

1. Android Studio で `GitHubFinderAndroid/` を開く
2. Gradle Sync を実行
3. エミュレータまたは実機を選択して Run

### Widget の動作確認

1. アプリ起動 → 検索 → お気に入りを数件登録
2. ホーム画面長押し → ウィジェット → 「GitHubFinder」から配置

GitHub API は認証なし（Rate Limit: 10 req/min）で動作します。

## 命名規則

| 種類 | 命名 |
|------|------|
| Entity | `〇〇Entity` |
| DTO | `〇〇DTO` |
| Room Entity | `〇〇DbEntity` |
| Repository interface | `〇〇Repository` |
| Repository 実装 | `〇〇RepositoryImpl` |
| DataSource | `〇〇RemoteDataSource` / `〇〇LocalDataSource` / `〇〇CacheDataSource` |
| ViewModel | `〇〇ViewModel` |
| Screen | `〇〇Screen` |
| UiState | `〇〇UiState` (sealed interface) |

## iOS版

同一仕様のiOS版が `/Users/k-arai/Git/GitHubFinder/` にあります。設計思想・機能は共通で、技術スタックのみが異なります。

## オープンソース利用について

### ライセンス
[MIT License](./LICENSE) で公開しています。

### フォーク時の識別子置換

このリポジトリをフォークして利用する場合、以下の識別子を**自身のものに変更してください**:

| 項目 | サンプル値 | 変更先の例 |
|---|---|---|
| `applicationId` | `com.example.githubfinder` | `com.yourdomain.githubfinder` |
| `namespace`（各モジュール） | `com.example.githubfinder.*` | `com.yourdomain.githubfinder.*` |
| Package 名（Kotlin ソース） | `com.example.githubfinder.*` | 同上に合わせる |
| Signing 設定 | デフォルトなし | `release` ビルド用 keystore を作成し署名設定 |

#### 変更箇所

1. **`app/build.gradle.kts`** の `applicationId`
2. **各モジュールの `build.gradle.kts`** の `namespace`
3. **Kotlin 全ソースの `package`** 宣言（IDE のリファクタ機能で一括置換）
4. **`AndroidManifest.xml`** 内の参照
5. **`feature/widget/src/main/AndroidManifest.xml`** の `receiver android:name`

### リリースビルドの注意点

サンプル目的のため `app/build.gradle.kts` の `release` ブロックは:

```kotlin
release {
    isMinifyEnabled = false  // サンプル用、本番では true 推奨
}
```

になっています。**プロダクション利用する場合は必ず以下を有効化してください**:

- `isMinifyEnabled = true`（R8 によるコード難読化）
- 専用 ProGuard ルール（`proguard-rules.pro`）の整備
- リリース keystore による署名設定

### GitHub 商標について

本アプリは GitHub Inc. と無関係な学習用サンプルです。アプリ名・アイコンに公式 GitHub の商標を使用していません。

### GitHub API 利用について

[GitHub REST API Terms of Use](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) に準拠してください。認証なしのレートリミットは **60 req/h** です。

### セキュリティ

脆弱性を発見された場合は [SECURITY.md](./SECURITY.md) を参照してください。

### 免責事項

本アプリは**学習・教育目的のサンプル実装**です。本番環境での利用は自己責任でお願いします。Now in Android（NiA）相当の品質を目指していますが、特定用途への適合性を保証するものではありません。
