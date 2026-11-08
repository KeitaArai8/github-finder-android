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
data class UiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false
)
```

```kotlin
@HiltViewModel
class ItemViewModel @Inject constructor(
    private val repository: ItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun load() {
        viewModelScope.launch {
            val items = repository.fetch()
            _uiState.value = UiState(items = items)
        }
    }
}
```

```kotlin
@Composable
fun ItemScreen(viewModel: ItemViewModel) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn {
        items(state.items) {
            Text(it.name)
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

### 対策（Reducer寄せ）

```kotlin
fun reduce(state: UiState, action: Action): UiState
```

---

## 🔄 追従規則と基準線（2026-10 更新）

### 追従規則（3 層）

何に追従するかを層で分け、層ごとに一次情報を決める。

| 層 | 一次情報 | 動かす条件 |
|---|---|---|
| 設計パターン | 公式アーキテクチャガイド（一次）／Now in Android（実装例の二次） | 両者が一致したら移行する |
| Google 製ライブラリの版 | 最新安定版（release notes が一次情報） | 安定版が出たら上げる |
| 第三者 OSS（Coil・Retrofit・OkHttp・Turbine） | NiA の採用 | NiA が移行したら追従する。各ライブラリのリリースでは動かさない |

- 第三者 OSS を NiA に揃えるのは、個々の新版を追うと判断基準が毎回ばらつくため
- Coil は 3 が現行だが、この規則により NiA が移行するまで Coil 2 を維持する（保留）

### SDK 基準線

- compileSdk 37 / targetSdk 36 / minSdk 26（維持）
- targetSdk 36 は Google Play の 2026-08-31 要件に合わせた値

### ツールチェーン

- AGP 9.4.1 / Gradle 9.8.2 / Kotlin 2.4.21 / KSP 2.3.12
- Kotlin は AGP 9 の built-in Kotlin を使い、`kotlin-android` plugin は適用しない。`android.builtInKotlin=false` / `android.newDsl=false` などの opt-out フラグは AGP 10 で削除されるため撤去した

### 反映した個別判断

- **Navigation 3**：安定版で NiA も移行済みのため基準にした。単一の `rememberNavBackStack` に NiA の top-level 規則（開始タブでリセット・他タブは末尾へ・再タップは no-op）を再現する。Favorite / Settings の ViewModel はタブ切替（Search へ戻る）で release される
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
- **`room-ktx`**：Room 2.7 で空のアーティファクトになったため削除した

### 意図した逸脱（2026-11 監査）

公式・NiA と異なる実装を、理由つきで意図的に残している。

1. **FavoriteStore は app スコープの `stateIn(Eagerly)`、ViewModel は `init` でパイプラインを開始する**（`stateIn(WhileSubscribed(5_000))` ではない）：タブ復帰時の Loading のちらつきを避け、`onCleared` の Undo commit 経路を保つため
2. **Undo 削除の commit は ViewModel から `@ApplicationScope` で起動する**（Repository の suspend 関数内で外部スコープを使う形ではない）：ViewModel 破棄後も commit を完了させる所有者を ViewModel 側に置くため
3. **NetworkMonitor は INTERNET + VALIDATED で判定する**（NiA は INTERNET のみ）：公式ガイドに従い、疎通できない接続をオンラインと誤判定しないため
4. **`@TutorialScope` の DI Provider で `Dispatchers.Default` を直書きしている**（NiA が Dispatcher を直書きする場所と同じ）：core:designsystem は core:data の `@ApplicationScope` に依存できないため

2026-11 の 3 巡目確認（a-10）で、①②⑤（旧番号。FavoriteStore の `stateIn(Eagerly)`、Undo commit の ViewModel 起動、`Dispatchers.Default` の直書き）は上記のまま意図した逸脱として残す。旧④（`MainDispatcherRule` の複製）は `core:testing` の追加で解消したため削除した。

### 未対応

- Baseline Profile / Startup Profile：サンプルを配布するまで保留する
- Coil 3（NiA は 2.7 のまま）
- Material 3 Expressive（beta）
- ウィジェットのプロセス外更新：既存の `updatePeriodMillis`（1 時間）で足りるため、WorkManager は追加しない（2026-11 監査 a-7 / a-15 は対応済み扱い）
- 検索 UI の `SearchBar`（a-13）：NiA が使っていないため採用しない（`TextField` のまま）
- Room 3（評価結果。2026-11 時点）
  - 変わること：パッケージが `androidx.room3`（artifact は `room3-runtime` / `room3-compiler`）に変わる。SupportSQLite を使わず `SQLiteDriver` が必須になり、Migration / Callback の引数は `SQLiteConnection` になる。DAO は coroutine ベース、コード生成は Kotlin のみ（KSP 必須。本アプリは KSP 済み）。KMP 対応が主目的
  - 安定版は 3.0.3（3.0.0 は 2026-07-01、3.1 は alpha）。2.x は 2.8.5（2026-09-09）まで更新が続いており、公式ページに deprecated の記載は見つからない
  - 推奨：今は 2.8.5 を維持する。追従規則（Google 製ライブラリは最新安定版）では対象だが、パッケージ名と `SQLiteDriver` 必須化を伴う major なので、公式アーキテクチャガイド / NiA が Room 3 へ移った時点で 1 PR として移行する
  - 参照：https://developer.android.com/jetpack/androidx/releases/room3

---

# 🧠 OS共通設計原則（最重要）

## ✅ 1. フレームワークはUI層に閉じ込める

```
UI → Domain → Data
```

---

## ✅ 2. 依存は一方向

```
View → Model → Repository
```

---

## ✅ 3. 状態はなるべく集約

---

## ✅ 4. 置き換え可能にする

- Rx → async/await
- Combine → Observation
- Flow → suspend

---

## ✅ 5. 公式に戻れる設計にする

---

# 💬 まとめ

- iOS：柔軟・シンプル・自由
- Android：厳格・UDF・統制
- 最適解：
  - 公式ベース
  - 軽いUDF
  - 依存の局所化

---

（2026時点のデファクトまとめ）

