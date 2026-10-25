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
- **`room-ktx`**：Room 2.7 で空のアーティファクトになったため削除した

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

