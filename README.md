# GitHubFinder (Android)

GitHubのリポジトリをキーワードで検索し、お気に入りに保存できるAndroidアプリです。2026 年時点のデファクトスタンダード（Jetpack Compose + ViewModel + StateFlow + Hilt + Room + Material 3 + Glance Widget）に準拠した学習サンプル実装です。

## 技術スタック

| 項目 | 選定 | 採用理由 |
|------|------|---------|
| UI | Jetpack Compose | Android 公式の宣言的 UI。Now in Android 採用 |
| 状態管理 | ViewModel + StateFlow (UDF 単一状態) | 公式アーキテクチャガイドの推奨構成。Now in Android 採用 |
| DI | Hilt | 公式推奨の DI。Now in Android 採用 |
| 永続化 | Room | Jetpack 公式。Now in Android 採用 |
| ネットワーク | Retrofit + kotlinx.serialization | Android のデファクト OSS。Now in Android 採用 |
| ナビゲーション | Navigation 3（NavDisplay + NavKey） | Jetpack 公式の Compose 向け新ナビゲーション |
| Widget | Glance + Material3 for Glance | Jetpack 公式の Compose 風 Widget API |
| テスト | JUnit4 + Turbine + Compose UI Tests | JUnit4 / Compose UI Tests は公式、Turbine は Flow テストのデファクト（Now in Android 採用） |
| SDK | minSdk 26 (Android 8.0) / targetSdk 36 (Android 16) / compileSdk 37.1 | 公式ガイドの最新 API 追従方針 |
| デザイン | Material 3 | Android 公式デザインシステム |

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
    data class Idle(val history: List<String> = emptyList(), ...) : SearchUiState
    data class Loading(...) : SearchUiState
    data class Loaded(items, isLoadingMore, isRefreshing, ...) : SearchUiState
    data class Empty(...) : SearchUiState
    data class Error(val message: UiText, ...) : SearchUiState
}
```

`Loaded` の中に `isEditMode`・`selectedIDs` などサブ状態を持たせることで、**Loaded 状態内の状態変化**もデータクラス1つで構造化しています。

### 一度きりの通知も State として持つ（userMessages + userMessageShown）

「SnackBar 表示」のような一度きりの通知は、[公式ガイド](https://developer.android.com/topic/architecture/ui-layer/events)の推奨どおり **ViewModel のイベントを必ず UI State の更新として扱う** 形にしています。`Channel` / `SharedFlow` は配信が保証されない（購読者不在時に取りこぼし得る）ため、ユーザーへの通知の配信には使いません（`SearchViewModel` が内部に持つ private な `MutableSharedFlow` は通知ではなく、「debounce を待たず今すぐ検索したい要求」（履歴選択・プルリフレッシュ）を検索パイプラインへ流す入力で、View には公開しません）。通知は `uiState` とは別の `StateFlow` に **FIFO のリスト** で持ち、View が表示し終えたら `userMessageShown(id)` で消します（フェーズごとの sealed case に載せないのは、通知がどのフェーズでも発生し得るため。ただし画面の状態であり、View は `StateFlow` として購読します）:

```kotlin
data class UserMessage(val id: Long, val text: UiText)  // 文言は UiText（文字列リソース ID + 引数）。 id は同文言が連続しても別通知と扱うためだけの値

@HiltViewModel
class SearchViewModel : ViewModel() {
    private val _userMessages = MutableStateFlow<List<UserMessage>>(emptyList())
    val userMessages: StateFlow<List<UserMessage>> = _userMessages.asStateFlow()

    fun toggleFavorite(...) {
        try { ... } catch (_: Exception) {
            enqueueMessage(UiText.Resource(R.string.search_error_favorite_update))
        }
    }

    fun userMessageShown(id: Long) {
        _userMessages.update { messages -> messages.filterNot { it.id == id } }
    }
}

// Composable 側: 長寿命の単一 LaunchedEffect で先頭を 1 件ずつ表示 → 表示後に消す
LaunchedEffect(Unit) {
    viewModel.userMessages
        .map { it.firstOrNull() }
        .distinctUntilChanged()
        .filterNotNull()
        .collect { message ->
            snackBarHostState.showSnackbar(message.text)  // Material 3 公式 API のため命名そのまま
            viewModel.userMessageShown(message.id)
        }
}
```

**メリット**: 配信が保証される（表示し終えて `userMessageShown` が呼ばれるまで State に残る）/ 複数の通知が近接しても順番にすべて表示される / テストは `userMessages.value` の検証だけで済む。

**注意点（回転時の挙動）**: 表示が完了していない通知は、画面回転後に再表示されます（`userMessageShown` が呼ばれるまで State に残るため。配信が保証される代わりに、回転で再表示されない性質は持ちません）。

**Undo の確定は ViewModel が持つ**: お気に入り一覧のスワイプ削除は、`removeFavorite` が `pendingDeleteIDs` へ追加し、「元に戻す」付きの通知（`UserMessage.undoID`）を積み、`viewModelScope` の猶予タイマー（約 4 秒）で実削除を確定します（Room への書き込みは `@ApplicationScope` で実行し、書き込み中に ViewModel が破棄されても中断されません）。View が行うのは「元に戻す」が押されたときの `undoDelete(id)`（タイマーのキャンセル + 行の復元）だけです。実削除を View 側の SnackBar の `Dismissed` に結び付けると、`LaunchedEffect` のキャンセル（キー変更・回転）で `Dismissed` が届かず、行が `pendingDeleteIDs` に取り残されるためです。また、猶予中に ViewModel が破棄された場合（タブ切替で back stack から外れるなど）は `onCleared` が残りを即座に実削除します（`viewModelScope` は破棄時にキャンセルされるため、`@ApplicationScope` のアプリ寿命スコープで実行）。

### combine + stateIn によるリアクティブ State 合成（SearchViewModel）

複数の非同期入力源（フェーズ・検索クエリ・お気に入り ID・検索履歴）から最終 UiState を合成する場合は、`combine + stateIn` で **宣言的に合成** する:

```kotlin
private val _phase = MutableStateFlow<SearchPhase>(SearchPhase.Idle)  // Idle は data object

val uiState: StateFlow<SearchUiState> = combine(
    _phase,
    query,  // SavedStateHandle 由来の StateFlow<String>
    favoriteStore.favoriteIDs,
    searchHistoryRepository.history,
) { phase, query, favoriteIDs, history ->
    phase.toUiState(query, favoriteIDs, history)  // 純粋な変換関数
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = SearchUiState.Idle(searchQuery = query.value),
)
```

**メリット**:
- お気に入り ID / 検索履歴が更新されたら現在のフェーズに自動反映される（手動キャッシュ不要）
- 全ての非同期入力が「単一の純粋な変換関数」に集約され、テストしやすい
- フェーズ遷移時に latestHistory を都度参照する手動同期コードが消える

`SearchViewModel` は検索の実行を 1 本のパイプライン（`query` の debounce と、履歴選択・プルリフレッシュの即時要求を `merge` し、`mapLatest` で前の検索をキャンセルする）に集約しています。

## モジュール構成（NiA準拠）

[Now in Android](https://github.com/android/nowinandroid) のモジュール設計思想に準拠し、15 モジュール + `build-logic`（Convention Plugin）の構成です。

```
:build-logic/convention  ← Convention Plugin（共通 Gradle 設定を集約）
:app                    ← エントリポイント + Navigation 3 + Hilt（MainActivity で CoachMarkManager を注入）
:core:model             ← ドメインモデル（GitHubRepo / Favorite / SearchResult）+ RepositoryDisplayItem + AppError（共有型）。Android 依存を持たない純粋な Kotlin/JVM モジュール
:core:common            ← @Dispatcher / GitHubFinderDispatchers と @ApplicationScope（アプリ寿命の CoroutineScope）の Provider。Android ライブラリ（Timber で未捕捉例外をログに残すため）
:core:data              ← Repository interface/impl（実装は internal。検索履歴・チュートリアル表示済みフラグ・ネットワーク接続状態も Repository で公開）+ CacheDataSource + NetworkMonitor（internal）+ FavoriteStore + DTO→ドメインモデルのマッピング
:core:network           ← Retrofit + DTO + RemoteDataSource + DI
:core:database          ← Room + Dao + Room Entity（model/）+ LocalDataSource + DI
:core:designsystem      ← Material 3 Theme + アイコン + 汎用の状態表示コンポーネント（EmptyStateView / ErrorView / OfflineBanner）。他のプロジェクトモジュールにも Hilt にも依存しない
:core:ui                ← モデルを描画するコンポーネント（RepositoryListItem）+ UI 補助（UiText / UserMessage / BrowserLauncher / ShareRepository / GitHubUrl）
:core:datastore         ← DataStore Preferences のデータソース（SearchHistoryLocalDataSource / TutorialLocalDataSource）+ DataStore を提供する DataStoreModule（旧 SharedPreferences からの移行を含む）。利用側は :core:data のみ
:feature:search         ← SearchViewModel + SearchScreen + SearchUiState
:feature:favorite       ← FavoriteViewModel + FavoriteScreen + FavoriteUiState + FavoriteUserMessage
:feature:settings       ← SettingsScreen
:feature:widget         ← Glance Widget (FavoriteWidget)
:feature:tutorial       ← TutorialScreen + CoachMarkOverlay + CoachMarkManager（@Singleton の State holder）+ チュートリアルの文言
:core:testing           ← テスト専用（MainDispatcherRule + 共有 Fake + TestData。本番コードからは依存しない）
```

### Convention Plugin（`build-logic/`）

NiA と同じく、`build-logic/convention/` に共通 Gradle 設定を集約。各モジュールの `build.gradle.kts` は Plugin alias と固有依存だけを記述する：

```kotlin
// feature/favorite/build.gradle.kts（先頭のコメントを除く全体）
plugins {
    alias(libs.plugins.githubfinder.android.feature)
}

android {
    namespace = "com.example.githubfinder.feature.favorite"
}

dependencies {
    implementation(project(":core:data"))
    testImplementation(project(":core:testing"))
}
```

提供される Convention Plugin:

| Plugin ID | 役割 | 適用モジュール例 |
|-----------|------|----------------|
| `githubfinder.android.application` | App ターゲット共通設定（compileSdk/minSdk/Java/Kotlin/buildConfig） | `:app` |
| `githubfinder.android.application.compose` | App + Compose | `:app` |
| `githubfinder.android.library` | Library 共通設定 | `:core:common`, `:core:data` 等（`:core:model` は Android を使わない Kotlin/JVM モジュールのため適用しない） |
| `githubfinder.android.library.compose` | Library + Compose（BOM 自動注入） | `:core:designsystem`, `:core:ui` 等 |
| `githubfinder.android.hilt` | Hilt + KSP + hilt-android 注入 | `:core:common`, `:core:data` 等 |
| `githubfinder.android.feature` | Library + Compose + Hilt + `:core:model` / `:core:designsystem` / `:core:ui` 依存 + lifecycle + coil + timber + テスト共通（Feature 集約） | `:feature:search`, `:feature:favorite`, `:feature:settings`, `:feature:tutorial` |

### モジュール依存関係

```
:app
├── :core:data
├── :core:designsystem
├── :feature:search
├── :feature:favorite
├── :feature:settings
├── :feature:widget
├── :feature:tutorial
└── (test) :core:testing

:feature:search / :feature:favorite / :feature:settings / :feature:tutorial
├── :core:model          ← feature 用 Convention Plugin が全 feature に付与
├── :core:designsystem   ← 同上
└── :core:ui             ← 同上

:feature:search / :feature:favorite / :feature:tutorial
└── :core:data

:feature:widget          （library + hilt の Convention のみ。:core:designsystem / :core:ui には依存しない）
├── :core:model
└── :core:data

:core:data
├── :core:common         （api。@ApplicationScope / @Dispatcher を依存先にも公開する）
├── :core:model
├── :core:network
├── :core:database
└── :core:datastore

:core:common
└── (Hilt / Coroutines / Timber。他の :core モジュールには依存しない)

:core:network / :core:database
└── :core:model

:core:testing（テスト専用。各モジュールの testImplementation / androidTestImplementation からのみ参照。いずれも api）
├── :core:model
├── :core:data
├── :core:database   ← 共有 FakeFavoriteDao が FavoriteDao を実装するため
└── :core:network    ← 共有 FakeGitHubApiService が GitHubApiService を実装するため

:core:ui
├── :core:designsystem   （api。テーマ・アイコンを使う）
└── :core:model          （api。RepositoryDisplayItem / AppError を描画・変換するため）

:core:designsystem
└── (Compose / Material 3 のみ。他のプロジェクトモジュールには依存しない。Hilt も使わない)

:core:datastore
└── (Hilt / DataStore / Coroutines / Timber。他の :core モジュールには依存しない)
```

### :core:data の役割

`:core:data` は「データアクセスの統合レイヤー」です。

```
:core:data
├── Repository interface           ← データアクセスの契約
├── Repository impl（internal）      ← Remote + Local + Cache を統合
├── model/                         ← DTO → ドメインモデルのマッピング（RepositoryDTO.asExternalModel）
├── repository/FavoriteStore       ← Room の Flow を StateFlow にして共有する薄い橋渡し（@Singleton。SSOT は Room）
├── datasource/SearchCacheDataSource ← Repository の内部実装詳細（internal）
├── repository/SearchHistoryRepository ← 検索履歴の契約（実装は :core:datastore の SearchHistoryLocalDataSource に委譲）
├── repository/TutorialRepository  ← チュートリアル表示済みフラグの契約（実装は :core:datastore の TutorialLocalDataSource に委譲）
├── repository/NetworkStatusRepository ← isOnline: Flow<Boolean> の契約（実装は NetworkMonitor に委譲）
├── util/NetworkMonitor (i/f, internal) ← ネットワーク接続状態のデータソースの契約
├── util/ConnectivityManagerNetworkMonitor ← ConnectivityManager 実装（internal。デフォルトネットワークの状態を 1 つ持つ）
└── di/                            ← interface ↔ impl のバインド（@Binds のみ。Dispatcher と @ApplicationScope は :core:common）
```

### :core:designsystem と :core:datastore

`:core:designsystem` はテーマ・アイコン・汎用の状態表示コンポーネントだけを持ちます。他のプロジェクトモジュールにも Hilt にも依存せず、Compose / Material 3 を `api` で公開します。モデルを描画するコンポーネントと UI 補助は `:core:ui` に置きます（`:core:designsystem` と `:core:model` を `api` で公開）。チュートリアルの画面・コーチマーク・ステップ管理は `:feature:tutorial` に置きます。表示済みフラグの永続化は Compose に依存しないため `:core:datastore` のデータソースが持ち、`CoachMarkManager` は `:core:data` の `TutorialRepository` 経由で読み書きします:

```
:core:designsystem
└── Theme / AppIcons / EmptyStateView / ErrorView / OfflineBanner ← テーマ・アイコン・汎用の状態表示コンポーネント

:feature:tutorial
├── TutorialScreen / CoachMarkOverlay      ← チュートリアル画面（public は TutorialScreen のみ）と、スポットライト・吹き出しの描画
├── CoachMarkManager (public) / CoachMarkStep / coachMarkAnchor ← ステップ管理（@Singleton）と座標の報告（CoachMarkManager 以外は internal）
└── res/values*/strings.xml                ← tutorial_* の文言とモック画面のタブ名

:core:ui
├── RepositoryListItem                     ← RepositoryDisplayItem を描画する 1 行
├── UiText / UserMessage                   ← 文言（文字列リソース + 引数）と通知。例外 → UiText 変換（Throwable.toUiText）
├── BrowserLauncher                        ← Custom Tabs 起動ユーティリティ
├── ShareRepository                        ← シェアシートで GitHub URL を共有
└── GitHubUrl (githubUrlFor)               ← URL 組み立てヘルパー

:core:datastore
├── SearchHistoryLocalDataSource / TutorialLocalDataSource ← DataStore Preferences のデータソース（public。:core:data の Repository だけが使う）
└── di/DataStoreModule + Qualifier                          ← 用途別の DataStore（"search_history" / "tutorial"。tutorial は SharedPreferences からの移行つき）
```

`CoachMarkManager` は Compose の座標型（`Rect`）を持つため `:feature:tutorial` に置き、Compose 非依存の永続化だけを `:core:datastore` が持ちます。書き込みは `@ApplicationScope` の CoroutineScope で行います。

### DataSource の分離

Android公式に従い、各DataSourceは**単一のデータソースのみ**を処理します。

| DataSource | モジュール | 責務 | データソース |
|-----------|----------|------|------------|
| `GitHubRemoteDataSource` | `:core:network` | GitHub API 通信のみ | Retrofit |
| `FavoriteLocalDataSource` | `:core:database` | お気に入り CRUD のみ | Room（`FavoriteDao`） |
| `SearchCacheDataSource` | `:core:data` | 検索結果のメモリキャッシュ（最大 50 エントリ・Mutex で排他） | メモリ（LinkedHashMap） |
| `SearchHistoryLocalDataSource` | `:core:datastore` | 検索履歴の保持（最大 10 件・重複排除・新しい順） | DataStore Preferences（`search_history`） |
| `TutorialLocalDataSource` | `:core:datastore` | チュートリアル表示済みフラグの保持 | DataStore Preferences（`tutorial`） |
| `NetworkMonitor` | `:core:data`（internal） | ネットワーク接続状態の供給 | `ConnectivityManager` |

ViewModel や `CoachMarkManager` はこれらを直接使わず、`:core:data` の Repository（`SearchHistoryRepository` / `TutorialRepository` / `NetworkStatusRepository`）を使います。
### SSOT（単一信頼源）

| データ | SSOT | 理由 |
|--------|------|------|
| お気に入り | Room DB (ローカル) | アプリ終了後も保持。唯一の真実。Widget からも同じ DB を読む |
| 検索結果 | API + メモリキャッシュ | API が正。キャッシュはアプリ起動中のみ・上限あり。プルリフレッシュはキャッシュを読まず再取得して置き換える |
| 検索履歴 | DataStore Preferences | 直近 10 件のローカル履歴。`SearchHistoryRepository` が公開 |
| チュートリアル表示済み | DataStore Preferences | ローカルフラグ（旧 SharedPreferences 値は初回に移行）。`TutorialRepository` が公開 |

### お気に入り設計（Passive View + SSOT）

ドメインモデルにフラグを持たせず、ViewModel が表示用データクラス `RepositoryDisplayItem`（`:core:model`）に変換して公開します。Screen は受け取った値をそのまま表示するだけです。

```
SearchViewModel（内部）
├── _phase: MutableStateFlow<SearchPhase>  ← 検索の段階。Loaded は API から取得した GitHubRepo の一覧を持つ
├── query / history                        ← 検索クエリ（SavedStateHandle）と検索履歴
├── favoriteStore.favoriteIDs: Set<Int>    ← お気に入り ID（SSOT = DB）
└── SearchPhase.toUiState(...)             ← combine の中で突き合わせ、RepositoryDisplayItem.from(repository, isFavorite) に変換

SearchViewModel（公開）
└── uiState: StateFlow<SearchUiState>      ← Screen はこれを表示するだけ
```

### オフライン検知（NetworkStatusRepository）

`:core:data` の `repository/NetworkStatusRepository.kt` が `isOnline: Flow<Boolean>` を公開し、実装の `NetworkStatusRepositoryImpl` はデータソースの `NetworkMonitor`（`:core:data` 内の internal interface）に委譲します。`NetworkMonitor` の実装 `ConnectivityManagerNetworkMonitor` が `ConnectivityManager` を `callbackFlow` でラップします。`registerDefaultNetworkCallback` で「現在のデフォルトネットワークが INTERNET かつ VALIDATED か」という単一の状態だけを持ちます（ネットワークの集合は自前で管理しません）。Wi-Fi が切れてモバイルへ切り替わるときのデフォルトネットワークの選択は OS が行い、新しいデフォルトネットワークの通知で状態が更新されます。切り替え時に届く旧ネットワークの `onLost` は、現在のネットワークと一致するときだけオフラインとして扱います。`MainViewModel` が `NetworkStatusRepository` の値を `stateIn` で `StateFlow<Boolean>` にし、`MainNavigation` が `collectAsStateWithLifecycle` で観察します:

```kotlin
interface NetworkStatusRepository {
    val isOnline: Flow<Boolean>
}

class MainViewModel @Inject constructor(networkStatusRepository: NetworkStatusRepository) : ViewModel() {
    val isOnline: StateFlow<Boolean> = networkStatusRepository.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
```

UI 層は `AnimatedVisibility(visible = !isOnline)` で `OfflineBanner`（`:core:designsystem`）をスライドイン表示。

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

各クラスで機能別 tag を付与（主なもの）:

| Tag | 用途 |
|---|---|
| `Search` | 検索 / API 呼び出し |
| `Favorite` | お気に入り CRUD / 一括削除 |
| `Network` | 接続状態変化 |
| `FavoriteStore` | お気に入りの購読・更新 |
| `FavoriteWidget` | ウィジェットの描画 |

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
val favorites = repo.favorites.first().take(5)
```

Widget は Hilt スコープ外で動作するため `@AndroidEntryPoint` は使えませんが、`EntryPointAccessors` で Singleton スコープの Repository を取得できます。

### エラーハンドリング

```
GitHubRemoteDataSource → HttpException を AppError に変換（403 は RateLimit、他は Server(code)）、
                         IOException は Network、その他は Unknown（CancellationException は変換せず伝播）
    ↓
SearchRepositoryImpl → AppError はそのまま通し、想定外の例外だけ AppError.Unknown に包む（CancellationException は伝播）
    ↓
SearchViewModel → 例外を toUiText()（UiText.kt。AppError は種類ごとの文字列リソース、Unknown は例外メッセージ、
                  AppError 以外は IOException ならネットワーク文言・それ以外は汎用文言）で UiText にして SearchUiState.Error に反映
    ↓
Screen → エラーメッセージ + リトライボタン表示
```

AppError は 4 分類（`Network` / `RateLimit` / `Server(code)` / `Unknown`）。

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
├── FavoriteRepository ← FavoriteRepositoryImpl
├── SearchHistoryRepository ← SearchHistoryRepositoryImpl
├── TutorialRepository ← TutorialRepositoryImpl
├── NetworkStatusRepository ← NetworkStatusRepositoryImpl
├── NetworkMonitor ← ConnectivityManagerNetworkMonitor

@Module DispatchersModule（:core:common）
└── @Dispatcher(Default) CoroutineDispatcher

@Module CoroutineScopesModule（:core:common）
└── @ApplicationScope CoroutineScope（SupervisorJob + Default + 未捕捉例外を Timber に記録）

@Module DataStoreModule（:core:datastore）
├── @SearchHistoryDataStore DataStore<Preferences>（search_history）
└── @TutorialDataStore DataStore<Preferences>（tutorial。SharedPreferences からの移行つき）
```

自動注入:
- `GitHubRemoteDataSource` / `FavoriteLocalDataSource` / `SearchHistoryLocalDataSource` / `TutorialLocalDataSource` / `SearchCacheDataSource` / `FavoriteStore` — `@Inject constructor`
- 各 ViewModel — `@HiltViewModel` + `@Inject constructor`
- `CoachMarkManager` — `@Singleton` + `@Inject constructor`

## 機能一覧

### メイン機能

| 機能 | 説明 |
|------|------|
| キーワード検索 | デバウンス付き（500ms）、GitHub API |
| **検索履歴** | 直近10件を `DataStore Preferences` に永続化（`SearchHistoryRepository` が公開）、Idle 時に履歴一覧 / 行タップで即時再検索 / 個別削除 / 全消去 |
| 無限スクロール | 末尾3件手前でプリフェッチ |
| お気に入り登録/解除 | ハートボタン、Room 永続化 |
| お気に入り一覧 | スワイプ削除 / 行タップで GitHub ページを Custom Tabs で開く |
| **ソート（メモリ保持）** | 保存が新しい順 / Star 多い順 / 名前 A→Z（TopAppBar の DropdownMenu） |
| **言語フィルタ（メモリ保持）** | 登録済み言語を `FilterChip` で動的提示、トグルで絞り込み・解除（フィルタ中の言語が消失すると自動クリア） |
| **シェア** | `Intent.ACTION_SEND` の Chooser で GitHub URL をシェア（検索/お気に入り両画面の行に Share IconButton） |
| **一括選択削除** | 編集モードで複数選択 → AlertDialog 確認 → 一括削除 |
| メモリキャッシュ | 検索結果をアプリ起動中キャッシュ（上限 50 エントリ） |
| プルリフレッシュ | 検索・お気に入り両画面（`PullToRefreshBox`）。検索はキャッシュを読まずに再取得する |
| **オフライン検知** | `ConnectivityManager` を `Flow<Boolean>` で公開、切断時に画面上部にバナー表示 |
| **アダプティブナビゲーション** | `NavigationSuiteScaffold` で画面幅に応じて切り替え（compact は `NavigationBar`、medium 以上は `NavigationRail`）。キーボード表示時はナビゲーション要素ごとキーボードの上に載る |
| **予測型「戻る」** | `enableOnBackInvokedCallback`（targetSdk 36 では既定で有効）。Navigation 3 の `NavDisplay` が戻るジェスチャーのプレビューアニメーションを提供 |
| **構造化ログ** | Timber による tag 別 Logcat 出力（DEBUG ビルドのみ） |
| チュートリアル | **スポットライト型専用画面**（TutorialScreen）、初回起動時のみ |
| 設定画面 | プライバシーポリシー/利用規約（Custom Tab） |
| ダークモード | システム設定追従（Dynamic Color対応） |
| エラーハンドリング | 4分類（Network/RateLimit/Server/Unknown）+ リトライ |

### ホームウィジェット（Glance）

| 項目 | 内容 |
|------|------|
| 機能 | お気に入り上位5件をホーム画面に表示 |
| サイズ | resizable（horizontal+vertical）、デフォルト 3×2 セル |
| データ取得 | Hilt `EntryPointAccessors` 経由で `FavoriteRepository.favorites.first()`（上位5件） |
| 即時反映 | `FavoriteWidgetUpdater` が `FavoriteStore.favorites` を購読し、変化のたびに `updateAll` で再描画 |
| 更新頻度 | `updatePeriodMillis=3600000`（1時間ごと） |
| タップ動作 | アプリ起動 |
| 空状態 | 「お気に入りがありません / 検索画面から追加してください」 |

### チュートリアル（スポットライト型）

`:feature:tutorial` の `TutorialScreen` がモック UI を組み（タブ名などの文言もこのモジュール自身の文字列リソース）、実際のアプリ UI と同じレイアウトに `Modifier.coachMarkAnchor` を付与してスポットライトを当てます。本機能実装には一切影響しません。

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
| パーミッション | `INTERNET`（API 通信。`:core:network` の manifest）と `ACCESS_NETWORK_STATE`（オフライン検知。`:core:data` の manifest。`:app` の manifest にも宣言）のみ（最小） |
| Receiver export | Widget Receiver は `exported="false"` |
| DB バックアップ除外 | `res/xml/backup_rules.xml` + `res/xml/data_extraction_rules.xml` で `github_finder.db` を除外 |
| SQL インジェクション | Room `@Query` のパラメータ化で型安全 |

## テスト

### Unit Tests（JUnit4 + Turbine + StandardTestDispatcher）

| モジュール | テストファイル | 件数 |
|---|---|---|
| `:feature:search` | SearchViewModelTest（debounce の順序は Turbine で検証） | 31 |
| `:feature:favorite` | FavoriteViewModelTest（一括削除・Undo 含む） | 42 |
| `:feature:widget` | FavoriteWidgetUpdaterTest（お気に入り変更ごとの更新・更新失敗後の継続） | 2 |
| `:feature:tutorial` | CoachMarkManagerTest | 5 |
| `:core:data` | SearchCacheDataSourceTest, SearchRepositoryImplTest, FavoriteRepositoryImplTest, SearchHistoryRepositoryImplTest, TutorialRepositoryImplTest, NetworkStatusRepositoryImplTest, FavoriteStoreTest（Turbine で emission 順序も検証）, GitHubRepoTest | 35 |
| `:core:network` | GitHubRemoteDataSourceTest（HTTP / IO / キャンセルのエラー変換） | 7 |
| `:core:database` | FavoriteLocalDataSourceTest（FakeFavoriteDao） | 6 |
| `:core:ui` | UiTextTest | 3 |
| `:core:datastore` | SearchHistoryLocalDataSourceTest（保存形式の固定を含む）, TutorialLocalDataSourceTest | 12 |
| `:app` | MainViewModelTest（オフライン状態の公開） | 2 |

合計 145 件。複数モジュールで使う Fake（`FakeFavoriteRepository` / `FakeSearchRepository` / `FakeSearchHistoryRepository` / `FakeFavoriteDao` / `FakeGitHubApiService`）・`MainDispatcherRule`・`TestData.kt` は、テスト専用の `:core:testing` に置いています（各モジュールは `testImplementation(project(":core:testing"))` で参照）。

### UI Tests（Compose UI Tests）

- `:feature:search` — SearchScreenTest（3 CUJ: 初期表示 / 検索→結果 / クリアボタンで Idle 復帰）

### テスト手法のハイライト

- `FakeGitHubApiService`（GitHubApiService interface の Fake）で `HttpException` / `IOException` を発生させ、エラーマッピングを検証
- `FakeFavoriteDao`（in-memory のリスト）で Room 実装を bypass したハーメティックなテスト
- `StandardTestDispatcher` + `advanceUntilIdle()` で Coroutine の完了を決定論的に待機
- `// Arrange:` / `// Act:` / `// Assert:` の AAA コメント必須

## セットアップ

1. Android Studio でクローンしたフォルダ（`github-finder-android/`）を開く
2. Gradle Sync を実行
3. エミュレータまたは実機を選択して Run

### Widget の動作確認

1. アプリ起動 → 検索 → お気に入りを数件登録
2. ホーム画面長押し → ウィジェット → 「GitHubFinder」から配置

GitHub API は認証なし（Rate Limit: 10 req/min）で動作します。

## 命名規則

| 種類 | 命名 |
|------|------|
| ドメインモデル（`:core:model`） | 接尾辞なし（`GitHubRepo` / `Favorite` / `SearchResult`） |
| DTO | `〇〇DTO` |
| Room Entity（`:core:database` の `model/`） | `〇〇Entity`（Entity → ドメインモデルは `asExternalModel()`） |
| Repository interface | `〇〇Repository` |
| Repository 実装 | `〇〇RepositoryImpl` |
| DataSource | `〇〇RemoteDataSource` / `〇〇LocalDataSource` / `〇〇CacheDataSource` |
| ViewModel | `〇〇ViewModel` |
| Screen | `〇〇Screen` |
| UiState | `〇〇UiState` (sealed interface) |

## iOS版

同一仕様の iOS 版が [KeitaArai8/github-finder-ios](https://github.com/KeitaArai8/github-finder-ios) にあります。設計思想・機能は共通で、技術スタックのみが異なります。

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

`app/build.gradle.kts` の `release` ブロックは R8 によるコード縮小・難読化とリソース縮小を有効にしています（`isMinifyEnabled = true` / `isShrinkResources = true`、ルールは `proguard-android-optimize.txt` の既定と `app/proguard-rules.pro`。`proguard-rules.pro` には実機で失敗を観測した keep ルールだけを、観測内容とともに置きます（現在は WorkManager の `InputMerger` の引数なしコンストラクタ 1 件））。

**署名設定は未整備**のため、プロダクション利用する場合は**リリース keystore による署名設定を必ず追加してください**。

R8 後の動作を実機やエミュレータで確認したいだけなら、build.gradle.kts を変えずに、debug keystore を注入してビルドします:

```
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=$HOME/.android/debug.keystore \
  -Pandroid.injected.signing.store.password=android \
  -Pandroid.injected.signing.key.alias=androiddebugkey \
  -Pandroid.injected.signing.key.password=android
adb install app/build/outputs/apk/release/app-release.apk
```

### GitHub 商標について

本アプリは GitHub Inc. と無関係な学習用サンプルです。アプリ名・アイコンに公式 GitHub の商標を使用していません。

### GitHub API 利用について

[GitHub REST API Terms of Use](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service) に準拠してください。このアプリが使う検索 API の認証なしのレートリミットは **10 req/min** です。

### セキュリティ

脆弱性を発見された場合は [SECURITY.md](./SECURITY.md) を参照してください。

### 免責事項

本アプリは**学習・教育目的のサンプル実装**です。本番環境での利用は自己責任でお願いします。Now in Android（NiA）相当の品質を目指していますが、特定用途への適合性を保証するものではありません。
