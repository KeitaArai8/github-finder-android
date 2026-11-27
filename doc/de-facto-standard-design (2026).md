# モバイルアプリ開発 デファクトスタンダードまとめ（2026）

---
# 🤖 Android編（詳細版）

## 🔗 学習リンク（デファクト）

### 🥇 公式

- [https://developer.android.com/topic/architecture](https://developer.android.com/topic/architecture)
- [https://developer.android.com/jetpack](https://developer.android.com/jetpack)
- [https://developer.android.com/jetpack/compose](https://developer.android.com/jetpack/compose)

### 🥇 リファレンス実装

- [https://github.com/android/nowinandroid](https://github.com/android/nowinandroid)

### 🥇 サンプル

- [https://github.com/android/architecture-samples](https://github.com/android/architecture-samples)
- [https://github.com/android/compose-samples](https://github.com/android/compose-samples)

### 🥈 Kotlin

- [https://kotlinlang.org/docs/home.html](https://kotlinlang.org/docs/home.html)

### 🥈 学習（補助）

- Android Developers YouTube [https://www.youtube.com/@AndroidDevelopers](https://www.youtube.com/@AndroidDevelopers)

---

## 🧭 デファクト設計

### 構成（UDF）

```
Event → ViewModel → State → View
```

---

## 🧠 設計思想

- 単一状態（UiState）
- 一方向データフロー（UDF）
- Viewはstateless
- 副作用はViewModel
- DIはHilt

---

## 💡 実装例

```kotlin
sealed interface UiState {
    data object Loading : UiState
    data class Loaded(val items: List<Item>) : UiState
}
```

```kotlin
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val items = repository.fetch()
            _uiState.value = UiState.Loaded(items = items)
        }
    }
}
```

```kotlin
@Composable
fun ItemScreen(viewModel: ItemViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        UiState.Loading -> CircularProgressIndicator()
        is UiState.Loaded -> LazyColumn {
            items(current.items) {
                Text(it.name)
            }
        }
    }
}
```

---

## 🧪 テスト構成

- ViewModelテスト（State検証）
- Repositoryテスト

---

## ⚠️ 大規模での課題と対策

### 課題

- State肥大化
- ロジック分散
- Flow乱用

### 対策（フェーズと派生入力を分けて合成）

本プロジェクトは Reducer（`reduce(state, action)`）を採らない。ViewModel の内部状態を「フェーズ（読み込みの段階とそのデータ）」と「派生入力（お気に入り ID・検索履歴など別の Flow）」に分け、`combine` + `stateIn` で 1 つの `UiState` に合成する（`SearchViewModel`）。

---

## 🔄 追従規則と基準線（2026-11 更新）

### 追従規則（3 層）

何に追従するかを層で分け、層ごとに一次情報を決める。

| 層 | 一次情報 | 動かす条件 |
|---|---|---|
| 設計パターン | 公式アーキテクチャガイド（一次）／Now in Android（実装例の二次） | 両者が一致したら移行する |
| Google 製ライブラリの版 | 最新安定版（release notes が一次情報） | 安定版が出たら上げる |
| 第三者 OSS（Coil・Retrofit・OkHttp・Turbine） | NiA の採用 | NiA が移行したら追従する。各ライブラリのリリースでは動かさない |

- 第三者 OSS を NiA に揃えるのは、個々の新版を追うと判断基準が毎回ばらつくため。この規則は `.github/dependabot.yml` の `ignore`（Coil / Retrofit / OkHttp / Turbine）で担保している
- Coil は 3 が現行だが、この規則により NiA が移行するまで Coil 2 を維持する（保留）
- Retrofit 3.0.0 / OkHttp 5.5.1 / Turbine 1.2.1 は、NiA の main（2.11.0 / 4.12.0 / 1.2.0）より先の版を使っている（2026-11-26 確認）。この規則と食い違っているが、版を下げてはいない

### SDK 基準線

- compileSdk 37.1 / targetSdk 36 / minSdk 26
- compileSdk 37.1 は Compose 1.13（BOM 2026.11.01）の要求（37.1 以上）に合わせた値。AGP 9.5 が対応する上限は API level 37
- targetSdk 36 は Google Play の 2026-08-31 要件に合わせた値（2026-11-26 確認。要件は API level 36 以上のまま）

### ツールチェーン

- AGP 9.5.3 / Gradle 9.9.2 / Kotlin 2.4.21 / KSP 2.3.14（2026-11-26 時点の最新安定版。AGP 9.5 は Gradle 9.6.0 以上・JDK 17 以上を要求する）
- Kotlin は AGP 9 の built-in Kotlin を使い、`kotlin-android` plugin は適用しない。`android.builtInKotlin=false` / `android.newDsl=false` などの opt-out フラグは AGP 10 で削除されるため撤去した

### 反映した個別判断

- **Navigation 3**：安定版で NiA も移行済みのため基準にした。単一の `rememberNavBackStack` に NiA の top-level 規則（開始タブでリセット・他タブは末尾へ・再タップは no-op）を再現する。Favorite の ViewModel はタブ切替（Search へ戻る）で release される
- **一度きりの通知は state で持つ**：公式 UI events ガイドに従い `userMessages` + `userMessageShown(id)` にした。Undo の実削除は ViewModel が担い、破棄時は `onCleared` が残りを commit する（README「一度きりの通知も State として持つ」参照）
- **`material-icons-extended`**：更新が止まっているため、Material Symbols の vector drawable と `AppIcons`（NiA の NiaIcons 相当）に置き換える
- **リリースビルドのスモーク（R8 後の実機確認）**：本番用の署名設定は未整備のため、debug keystore を一時的に注入して署名する。build.gradle.kts には署名用の分岐を持たせない

  ```
  ./gradlew assembleRelease \
    -Pandroid.injected.signing.store.file=$HOME/.android/debug.keystore \
    -Pandroid.injected.signing.store.password=android \
    -Pandroid.injected.signing.key.alias=androiddebugkey \
    -Pandroid.injected.signing.key.password=android
  ```
- **採用済みの項目**：`NavigationSuiteScaffold`（画面幅に応じて NavigationBar / NavigationRail を自動切替。IME はスイート全体で避ける）、R8 とリソース縮小（release で有効）、予測型「戻る」（`enableOnBackInvokedCallback`、targetSdk 36 の既定に合わせて明示）、`core:testing`（`MainDispatcherRule` と共有 Fake をテスト専用モジュールに集約）、`core:tutorial`（Compose 非依存のチュートリアル永続化を `core:designsystem` から分離。`CoachMarkManager` は Compose の `Rect` を持つため designsystem に残す。designsystem に永続化を同居させる逸脱は解消した）
- **`room-ktx`**：Room 2.7 で空のアーティファクトになったため削除した

### 意図した逸脱

公式・NiA と異なる実装を、理由つきで意図的に残している。

1. **FavoriteStore は app スコープの `stateIn(Eagerly)`、ViewModel は `init` でパイプラインを開始する**（`stateIn(WhileSubscribed(5_000))` ではない）：タブ復帰時の Loading のちらつきを避け、`onCleared` の Undo commit 経路を保つため
2. **Undo 削除の commit は ViewModel から `@ApplicationScope` で起動する**（Repository の suspend 関数内で外部スコープを使う形ではない）：ViewModel 破棄後も commit を完了させる所有者を ViewModel 側に置くため
3. **NetworkMonitor は INTERNET + VALIDATED で判定する**（NiA は INTERNET のみ）：公式ガイドに従い、疎通できない接続をオンラインと誤判定しないため
4. **`@TutorialScope` の DI Provider で `Dispatchers.Default` を直書きしている**（NiA が Dispatcher を直書きする場所と同じ）：core:tutorial は core:data の `@ApplicationScope` に依存しない（フラグを書くためだけに data 層へ依存させない）ため
5. **NetworkMonitor は `registerDefaultNetworkCallback` で単一の状態を持つ**（NiA は `registerNetworkCallback` でネットワークの集合を自前管理する）：このアプリが知りたいのは「いま使われている接続で通信できるか」だけで、複数ネットワークの追跡は不要なため。Wi-Fi とモバイルの切り替えは OS がデフォルトネットワークとして選ぶので、新しいデフォルトの通知で状態が更新される。切り替え時に旧ネットワークの `onLost` が遅れて届いても、現在のネットワークと一致しなければ無視する

`MainDispatcherRule` の複製は `core:testing` の追加で解消したため、逸脱から外した。

### 未対応

- Baseline Profile / Startup Profile：サンプルを配布するまで保留する（2026-11-26 確認。公式の recommendations は「Strongly recommended」としているが、効果が出るのは配布したビルドの起動時で、生成用のモジュール追加が要るため、配布を決めた時点で対応する）
  - 参照：https://developer.android.com/topic/architecture/recommendations ／ https://developer.android.com/topic/performance/baselineprofiles/overview
- Coil 3（2026-11-26 確認。NiA の main は 2.7.0 のまま）
  - 参照：https://github.com/android/nowinandroid/blob/main/gradle/libs.versions.toml
- Material 3 Expressive：material3 1.5.0（2026-11-18 安定版。BOM 2026.11.01 に含まれる）で Expressive のテーマと新しいコンポーネントの多くが安定版になった（`LoadingIndicator` などは experimental のまま）。ライブラリは 1.5.0 に上げたが、Expressive のテーマや新コンポーネントへの置き換えは見た目を変える変更なので行っていない（2026-11-26 確認）
  - 参照：https://developer.android.com/jetpack/androidx/releases/compose-material3
- ウィジェットのプロセス外更新：既存の `updatePeriodMillis`（1 時間）で足りるため、WorkManager は追加しない
- 検索 UI の `SearchBar`：NiA が使っていないため採用しない（`TextField` のまま）
- Room 3（評価結果。2026-11-26 に再確認し、結論は変わらず）
  - 変わること：パッケージが `androidx.room3`（artifact は `room3-runtime` / `room3-compiler`）に変わる。SupportSQLite を使わず `SQLiteDriver` が必須になり、Migration / Callback の引数は `SQLiteConnection` になる。DAO は coroutine ベース、コード生成は Kotlin のみ（KSP 必須。本アプリは KSP 済み）。KMP 対応が主目的
  - 安定版は 3.0.3（3.0.0 は 2026-07-01、3.1 は alpha）。2.x は 2.8.5（2026-09-09）まで更新が続いており、公式ページに deprecated の記載は見つからない
  - 推奨：今は 2.8.5 を維持する。追従規則（Google 製ライブラリは最新安定版）では対象だが、パッケージ名と `SQLiteDriver` 必須化を伴う major なので、公式アーキテクチャガイド / NiA が Room 3 へ移った時点で 1 PR として移行する
  - 参照：https://developer.android.com/jetpack/androidx/releases/room3

---

# 🧠 OS共通設計原則（最重要）

## ✅ 1. フレームワークはUI層に閉じ込める

```
UI → Data
```

Domain 層は置かない（公式アーキテクチャガイドでは任意。複数画面で共有する複雑なロジックが出た時点で足す）。

---

## ✅ 2. 依存は一方向

```
View → ViewModel → Repository
```

---

## ✅ 3. 状態はなるべく集約

---

## ✅ 4. 置き換え可能にする

- Flow → suspend

---

## ✅ 5. 公式に戻れる設計にする

---

# 💬 まとめ

- Android：厳格・UDF・統制
- 最適解：
  - 公式ベース
  - 軽いUDF
  - 依存の局所化

---

（2026時点のデファクトまとめ）

