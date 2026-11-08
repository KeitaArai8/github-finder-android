/*
 * Abstract:
 * 検索・お気に入り・設定の3タブナビゲーション定義。
 * チュートリアルが有効な間はTutorialScreen（専用モック画面）を全画面表示する。
 */

package com.example.githubfinder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import com.example.githubfinder.core.designsystem.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.example.githubfinder.core.designsystem.OfflineBanner
import com.example.githubfinder.core.designsystem.tutorial.CoachMarkManager
import com.example.githubfinder.feature.favorite.FavoriteRoute
import com.example.githubfinder.feature.search.SearchRoute
import com.example.githubfinder.feature.settings.SettingsScreen
import com.example.githubfinder.tutorial.TutorialScreen
import kotlinx.serialization.Serializable

/** 各タブの back stack キー。`rememberNavBackStack` がプロセス死後の復元でシリアライズする。 */
@Serializable
data object SearchKey : NavKey

@Serializable
data object FavoriteKey : NavKey

@Serializable
data object SettingsKey : NavKey

enum class TopLevelRoute(
    val key: NavKey,
    @StringRes val labelRes: Int,
) {
    Search(SearchKey, R.string.nav_search),
    Favorite(FavoriteKey, R.string.nav_favorite),
    Settings(SettingsKey, R.string.nav_settings),
    ;

    /** `vectorResource` は Composition 内でのみ解決できるため、コンストラクタ引数ではなく Composable getter で提供する。 */
    val icon: ImageVector
        @Composable get() = when (this) {
            Search -> AppIcons.Search
            Favorite -> AppIcons.Favorite
            Settings -> AppIcons.Settings
        }
}

@Composable
fun MainNavigation(
    coachMarkManager: CoachMarkManager,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val isActive by coachMarkManager.isActive.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        coachMarkManager.startIfNeeded()
    }

    Column {
        AnimatedVisibility(
            visible = !isOnline,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            OfflineBanner()
        }

        // バナーがステータスバー領域を使っている間は、下の画面で同じ inset を二重に避けないよう消費済みにする
        val contentModifier = if (isOnline) Modifier else Modifier.consumeWindowInsets(WindowInsets.statusBars)
        when (isActive) {
            // DataStore の答えが出るまでは背景だけを描く（本画面がチュートリアルの前に一瞬映るのを防ぐ）
            null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            )
            true -> TutorialScreen(coachMarkManager = coachMarkManager)
            false -> MainScaffold(modifier = contentModifier.weight(1f))
        }
    }
}

@Composable
private fun MainScaffold(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(SearchKey)

    // NiA の goToTopLevel と同じ規則: 検索（start）は stack をリセットし、他タブは末尾へ移動する。
    // 検索は常に root なので、タブ切替をまたいで検索結果が保持される。
    fun select(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        if (key == SearchKey) {
            // 開始タブ: Search だけを残して他を取り除く（Search は常に root）
            backStack.removeAll { it != SearchKey }
        } else {
            backStack.remove(key)
            backStack.add(key)
        }
    }

    // 画面幅に応じて NavigationBar（compact）/ NavigationRail（medium 以上）を自動で切り替える。
    // スイート自身がナビゲーション要素の inset を処理するため、別途 Scaffold の padding は持たない。
    // IME はスイート全体で避ける。各画面で個別に避けると、キーボードの背後に残るナビゲーション要素の高さ分
    // （NavigationBar なら 80dp）だけ、キーボードの上に余計な空きができる（SearchScreen で実測）。
    NavigationSuiteScaffold(
        modifier = modifier.imePadding(),
        navigationSuiteItems = {
            TopLevelRoute.entries.forEach { route ->
                // navigationSuiteItems は Composable ではないため、リソース解決は各スロットの中で行う
                item(
                    icon = { Icon(route.icon, contentDescription = stringResource(route.labelRes)) },
                    label = { Text(stringResource(route.labelRes)) },
                    selected = backStack.lastOrNull() == route.key,
                    onClick = { select(route.key) },
                )
            }
        },
    ) {
        // システム Back は stack の履歴を辿る（Search → Favorite → Settings なら Favorite → Search → 終了）。
        NavDisplay(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<SearchKey> { SearchRoute() }
                entry<FavoriteKey> { FavoriteRoute() }
                entry<SettingsKey> { SettingsScreen() }
            },
        )
    }
}
