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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteWidgetUpdater @Inject constructor(
    private val favoriteStore: FavoriteStore,
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
) {
    /**
     * 購読を開始する（Application.onCreate から 1 回呼ぶ）。
     * 最初の emission（起動直後の現在値）も更新するが、updateAll は冪等なので drop はしない。
     */
    fun start(): Job = scope.launch {
        favoriteStore.favorites.filterNotNull().collect {
            try {
                FavoriteWidget().updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 更新失敗で購読を止めない（次の変更でまた更新される）
                Timber.tag("FavoriteWidget").w(e, "Widget update failed")
            }
        }
    }
}
