/*
 * Abstract:
 * SearchScreen の主要ユーザージャーニーをCompose UI Testで検証する。
 * 初期表示 / クエリ入力→結果表示 / クリアボタンでIdle復帰 の3シナリオをカバー。
 */

package com.example.githubfinder.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.githubfinder.core.data.FavoriteStore
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

    @Test
    fun 初期表示でEmptyStateのdescriptionが見える() {
        // Arrange: Fake のみの ViewModel で SearchRoute を表示（未入力の初期状態）
        val viewModel = makeViewModel()
        composeTestRule.setContent {
            SearchRoute(viewModel = viewModel)
        }

        // Act: 操作なし（初期表示のみ）

        // Assert: 「キーワードを入力して」を含む EmptyState の説明文が見える
        composeTestRule
            .onNode(
                androidx.compose.ui.test.hasText("キーワードを入力して", substring = true),
            )
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
        composeTestRule.onNodeWithContentDescription("クリア").performClick()

        // Assert: Idle 状態の EmptyState 説明文が再び表示される
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("キーワードを入力して", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
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
