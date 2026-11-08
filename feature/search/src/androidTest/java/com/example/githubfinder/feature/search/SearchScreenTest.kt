/*
 * Abstract:
 * SearchScreen の主要ユーザージャーニーをCompose UI Testで検証する。
 * 初期表示 / クエリ入力→結果表示 / クリアボタンでIdle復帰 の3シナリオをカバー。
 */

package com.example.githubfinder.feature.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.SavedStateHandle
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.model.RepositoryEntity
import com.example.githubfinder.core.model.SearchResultEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun 初期表示でEmptyStateのdescriptionが見える() {
        val viewModel = makeViewModel()

        composeTestRule.setContent {
            SearchRoute(viewModel = viewModel)
        }

        composeTestRule
            .onNode(
                androidx.compose.ui.test.hasText("キーワードを入力して", substring = true)
            )
            .assertIsDisplayed()
    }

    @Test
    fun クエリ入力後にリポジトリ行が表示される() {
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
                SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
            )
        }
        val viewModel = makeViewModel(searchRepo = searchRepo)

        composeTestRule.setContent {
            SearchRoute(viewModel = viewModel)
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("kotlin")

        // debounce 500ms + search 完了を待機
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("kotlin/kotlinx.coroutines")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithText("kotlin/kotlinx.coroutines").assertIsDisplayed()
    }

    @Test
    fun クリアボタンでIdle状態に戻る() {
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
                SearchResultEntity(totalCount = 1, items = listOf(repo), hasNextPage = false)
            )
        }
        val viewModel = makeViewModel(searchRepo = searchRepo)

        composeTestRule.setContent {
            SearchRoute(viewModel = viewModel)
        }

        // 検索を実行して Loaded 状態まで遷移させる
        composeTestRule.onNode(hasSetTextAction()).performTextInput("kotlin")
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule
                .onAllNodesWithText("kotlin/kotlinx.coroutines")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // クリアボタンタップで Idle に戻る
        composeTestRule.onNodeWithContentDescription("クリア").performClick()

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
