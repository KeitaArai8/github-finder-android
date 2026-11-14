/*
 * Abstract:
 * 初回起動時に表示するチュートリアル専用画面。
 * SearchScreen のレイアウトをモックで再現し、各UIに coachMarkAnchor を付与する。
 * 本機能側の実装には一切手を入れず、チュートリアル進行は TutorialScreen 内の
 * モック操作（TextField/ハート/タブのタップ）から発火させる。
 */

package com.example.githubfinder.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.githubfinder.R
import com.example.githubfinder.core.designsystem.AppIcons
import com.example.githubfinder.core.designsystem.gitHubFinderColors
import com.example.githubfinder.core.designsystem.tutorial.CoachMarkManager
import com.example.githubfinder.core.designsystem.tutorial.CoachMarkStep
import com.example.githubfinder.core.designsystem.tutorial.coachMarkAnchor

@Composable
fun TutorialScreen(coachMarkManager: CoachMarkManager) {
    val currentStep by coachMarkManager.currentStep.collectAsStateWithLifecycle()
    val targetRects by coachMarkManager.targetRects.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 状態バー・ジェスチャーナビに本文が潜らないよう、モック本体だけに safeDrawing を適用する。
            // CoachMarkOverlay は画面全体にスクリムを描くため、パディングの外側（Box 直下）に置く。
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                MockHeader()
                MockSearchBar(
                    onTap = { coachMarkManager.advance(after = CoachMarkStep.SearchBar) },
                    coachMarkManager = coachMarkManager,
                )
                Spacer(modifier = Modifier.height(16.dp))
                MockRepositoryRow(
                    onFavoriteTap = { coachMarkManager.advance(after = CoachMarkStep.FavoriteButton) },
                    coachMarkManager = coachMarkManager,
                )
                Spacer(modifier = Modifier.weight(1f))
                MockTabBar(
                    onFavoriteTabTap = { coachMarkManager.advance(after = CoachMarkStep.FavoriteTab) },
                    coachMarkManager = coachMarkManager,
                )
            }

            currentStep?.let { step ->
                CoachMarkOverlay(
                    step = step,
                    targetRects = targetRects,
                    onNext = { coachMarkManager.next() },
                    onSkip = { coachMarkManager.skip() },
                )
            }
        }
    }
}

@Composable
private fun MockHeader() {
    Text(
        text = stringResource(R.string.nav_search),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun MockSearchBar(onTap: () -> Unit, coachMarkManager: CoachMarkManager) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onTap,
            )
            .coachMarkAnchor(CoachMarkStep.SearchBar, coachMarkManager)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            stringResource(R.string.tutorial_mock_search_placeholder),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MockRepositoryRow(onFavoriteTap: () -> Unit, coachMarkManager: CoachMarkManager) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "apple/swift",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "The Swift Programming Language",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.Star,
                    contentDescription = null,
                    tint = MaterialTheme.gitHubFinderColors.star,
                    modifier = Modifier.size(14.dp),
                )
                Text("67k", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "C++",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onFavoriteTap,
            modifier = Modifier.coachMarkAnchor(CoachMarkStep.FavoriteButton, coachMarkManager),
        ) {
            Icon(
                AppIcons.FavoriteBorder,
                contentDescription = stringResource(R.string.nav_favorite),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MockTabBar(onFavoriteTabTap: () -> Unit, coachMarkManager: CoachMarkManager) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            MockTabItem(
                icon = AppIcons.Search,
                label = stringResource(R.string.nav_search),
                isSelected = true,
                onClick = {},
            )
            MockTabItem(
                icon = AppIcons.Favorite,
                label = stringResource(R.string.nav_favorite),
                isSelected = false,
                onClick = onFavoriteTabTap,
                modifier = Modifier.coachMarkAnchor(CoachMarkStep.FavoriteTab, coachMarkManager),
            )
            MockTabItem(
                icon = AppIcons.Settings,
                label = stringResource(R.string.nav_settings),
                isSelected = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun MockTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
