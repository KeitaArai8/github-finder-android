/*
 * Abstract:
 * SearchScreen の主要ユーザージャーニーをCompose UI Testで検証する。
 * 初期表示 / クエリ入力→結果表示 / クリアボタンでIdle復帰 の3シナリオをカバー。
 */

package com.example.githubfinder.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.githubfinder.core.data.repository.FavoriteStore
import com.example.githubfinder.core.model.RepositoryEntity
import com.example.githubfinder.core.model.SearchResultEntity
import com.example.githubfinder.core.testing.FakeFavoriteRepository
import com.example.githubfinder.core.testing.FakeSearchHistoryDataSource
import com.example.githubfinder.core.testing.FakeSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** 画面の文言はリソースから解決する（端末の言語に依存して文字列リテラルが食い違わないようにするため）。 */
    private val idleDescription: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.search_empty_idle_description)

    private val historyTitle: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.search_history_title)

    private val clearDescription: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.search_a11y_clear)

    @Test
    fun 初期表示でEmptyStateのdescriptionが見える() {
        // Arrange: Fake のみの ViewModel で SearchRoute を表示（未入力の初期状態）
        val viewModel = makeViewModel()
        composeTestRule.setContent {
            SearchRoute(viewModel = viewModel)
        }

        // Act: 操作なし（初期表示のみ）

        // Assert: Idle 状態の EmptyState の説明文（search_empty_idle_description）が見える
        composeTestRule
            .onNode(hasText(idleDescription))
            .assertIsDisplayed()
    }

    @Test
    fun クエリ入力後にリポジトリ行が表示される() {
        // Arrange: 1 件ヒットする Fake 検索結果で SearchRoute を表示
        val repo = RepositoryEntity(
            id = 1,
            fullName = "kotlin/kotlinx.coroutines",
            description = "coroutines",
            starCount = 100,
            language = "Kotlin",
            ownerAvatarURL = null,
        )
        val searchRepo = FakeSearchRepository().apply {
            searchResult = Result.success(
                SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false),
            )
        }
        val viewModel = makeViewModel(searchRepo = searchRepo)

        composeTestRule.setContent {
            SearchRoute(viewModel = viewModel)
        }

        // Act: 検索欄に "kotlin" を入力し、debounce 500ms + search 完了を待機
        composeTestRule.onNode(hasSetTextAction()).performTextInput("kotlin")
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("kotlin/kotlinx.coroutines")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Assert: ヒットしたリポジトリ名の行が表示される
        composeTestRule.onNodeWithText("kotlin/kotlinx.coroutines").assertIsDisplayed()
    }

    @Test
    fun クリアボタンでIdle状態に戻る() {
        // Arrange: 1 件ヒットする Fake 検索結果で SearchRoute を表示し、検索済み（Loaded）状態にする
        val repo = RepositoryEntity(
            id = 1,
            fullName = "kotlin/kotlinx.coroutines",
            description = null,
            starCount = 0,
            language = null,
            ownerAvatarURL = null,
        )
        val searchRepo = FakeSearchRepository().apply {
            searchResult = Result.success(
                SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false),
            )
        }
        val viewModel = makeViewModel(searchRepo = searchRepo)

        composeTestRule.setContent {
            SearchRoute(viewModel = viewModel)
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("kotlin")
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("kotlin/kotlinx.coroutines")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Act: クリアボタンをタップ
        composeTestRule.onNodeWithContentDescription(clearDescription).performClick()

        // Assert: Idle 状態に戻り、検索済みのクエリが履歴に残っているため履歴一覧の見出し（search_history_title）が表示される
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText(historyTitle)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText(historyTitle).assertIsDisplayed()
    }

    private fun makeViewModel(
        searchRepo: FakeSearchRepository = FakeSearchRepository(),
        favoriteRepo: FakeFavoriteRepository = FakeFavoriteRepository(),
        searchHistory: FakeSearchHistoryDataSource = FakeSearchHistoryDataSource(),
    ): SearchViewModel = SearchViewModel(
        searchRepository = searchRepo,
        favoriteStore = FavoriteStore(favoriteRepo, CoroutineScope(Dispatchers.Default)),
        searchHistoryDataSource = searchHistory,
        savedStateHandle = SavedStateHandle(),
    )
}
