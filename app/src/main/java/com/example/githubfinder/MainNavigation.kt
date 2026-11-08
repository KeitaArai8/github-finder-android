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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import com.example.githubfinder.core.designsystem.AppIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
        if (isActive) {
            TutorialScreen(coachMarkManager = coachMarkManager)
        } else {
            MainScaffold(modifier = contentModifier)
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

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { route ->
                    val label = stringResource(route.labelRes)
                    NavigationBarItem(
                        icon = { Icon(route.icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = backStack.lastOrNull() == route.key,
                        onClick = { select(route.key) },
                    )
                }
            }
        },
    ) { padding ->
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
            // Scaffold の padding（status / navigation bar 分）を適用した上で消費済みにし、
            // 内側の画面の Scaffold / TopAppBar が同じ inset をもう一度加算（二重 padding）しないようにする。
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}
