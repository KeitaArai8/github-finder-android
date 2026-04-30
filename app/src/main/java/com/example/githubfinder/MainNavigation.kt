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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.githubfinder.core.data.network.NetworkMonitor
import com.example.githubfinder.core.designsystem.OfflineBanner
import com.example.githubfinder.core.designsystem.tutorial.CoachMarkManager
import com.example.githubfinder.feature.favorite.FavoriteScreen
import com.example.githubfinder.feature.search.SearchScreen
import com.example.githubfinder.feature.settings.SettingsScreen
import com.example.githubfinder.tutorial.TutorialScreen

enum class TopLevelRoute(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Search("search", R.string.nav_search, Icons.Default.Search),
    Favorite("favorite", R.string.nav_favorite, Icons.Default.Favorite),
    Settings("settings", R.string.nav_settings, Icons.Default.Settings),
}

@Composable
fun MainNavigation(
    coachMarkManager: CoachMarkManager,
    networkMonitor: NetworkMonitor,
) {
    val isActive by coachMarkManager.isActive.collectAsStateWithLifecycle()
    val isConnected by networkMonitor.isConnected.collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(Unit) {
        coachMarkManager.startIfNeeded()
    }

    Column {
        AnimatedVisibility(
            visible = !isConnected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            OfflineBanner()
        }

        if (isActive) {
            TutorialScreen(coachMarkManager = coachMarkManager)
        } else {
            MainScaffold()
        }
    }
}

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { route ->
                    val label = stringResource(route.labelRes)
                    NavigationBarItem(
                        icon = { Icon(route.icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentDestination?.hierarchy?.any { it.route == route.route } == true,
                        onClick = {
                            navController.navigate(route.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelRoute.Search.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelRoute.Search.route) { SearchScreen() }
            composable(TopLevelRoute.Favorite.route) { FavoriteScreen() }
            composable(TopLevelRoute.Settings.route) { SettingsScreen() }
        }
    }
}
