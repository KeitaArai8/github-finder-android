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

