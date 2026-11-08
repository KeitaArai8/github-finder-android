/*
 * Abstract:
 * お気に入りの変更をホーム画面のウィジェットへ反映する。
 * FavoriteStore.favorites の emission ごとに全ウィジェットを再描画する（updateAll）。
 */

package com.example.githubfinder.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.githubfinder.core.data.FavoriteStore
import com.example.githubfinder.core.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteWidgetUpdater internal constructor(
    private val favoriteStore: FavoriteStore,
    private val scope: CoroutineScope,
    private val updateWidgets: suspend () -> Unit,
) {
    @Inject
    constructor(
        favoriteStore: FavoriteStore,
        @ApplicationScope scope: CoroutineScope,
        @ApplicationContext context: Context,
    ) : this(favoriteStore, scope, { FavoriteWidget().updateAll(context) })

    /**
     * 購読を開始する（Application.onCreate から 1 回呼ぶ）。
     * 最初の emission（起動直後の現在値）も更新するが、updateAll は冪等なので drop はしない。
     */
    fun start(): Job = scope.launch {
        favoriteStore.favorites.collect {
            try {
                updateWidgets()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 更新失敗で購読を止めない（次の変更でまた更新される）
            }
        }
    }
}
