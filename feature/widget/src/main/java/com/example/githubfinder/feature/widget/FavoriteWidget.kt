/*
 * Abstract:
 * お気に入り上位5件をホーム画面に表示するGlance Widget。
 * タップでアプリを起動し、データは FavoriteRepository から取得する。
 */

package com.example.githubfinder.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.githubfinder.core.model.FavoriteEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class FavoriteWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val favorites = loadFavorites(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

        provideContent {
            GlanceTheme {
                FavoriteWidgetContent(
                    favorites = favorites,
                    launchIntent = launchIntent,
                )
            }
        }
    }

    private suspend fun loadFavorites(context: Context): List<FavoriteEntity> =
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                WidgetEntryPoint::class.java,
            )
            entryPoint.favoriteRepository().favorites.first().take(MAX_ITEMS)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    companion object {
        private const val MAX_ITEMS = 5
    }
}

@Composable
private fun FavoriteWidgetContent(
    favorites: List<FavoriteEntity>,
    launchIntent: Intent?,
) {
    val rootModifier = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.background)
        .cornerRadius(16.dp)
        .padding(12.dp)
        .let { modifier ->
            if (launchIntent != null) modifier.clickable(actionStartActivity(launchIntent))
            else modifier
        }

    Box(modifier = rootModifier) {
        if (favorites.isEmpty()) EmptyState() else FavoriteList(favorites = favorites)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "お気に入りがありません",
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = "検索画面から追加してください",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun FavoriteList(favorites: List<FavoriteEntity>) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Text(
            text = "お気に入り",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.padding(bottom = 8.dp),
        )
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            favorites.forEach { favorite ->
                item(itemId = favorite.id.toLong()) {
                    FavoriteRow(favorite = favorite)
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(favorite: FavoriteEntity) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = favorite.fullName,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = "★ ${favorite.starCount}",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}
