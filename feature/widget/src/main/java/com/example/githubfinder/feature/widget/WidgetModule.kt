/*
 * Abstract:
 * FavoriteWidgetUpdater が呼ぶ「全ウィジェットの再描画」を Hilt で提供する。
 * Glance の実体（FavoriteWidget().updateAll）を差し替え可能にし、updater をテストできるようにする。
 */

package com.example.githubfinder.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** 全ウィジェットを再描画する `suspend () -> Unit` を示す Qualifier。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class WidgetUpdate

@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {
    @Provides
    @WidgetUpdate
    fun provideWidgetUpdate(@ApplicationContext context: Context): @JvmSuppressWildcards suspend () -> Unit =
        { FavoriteWidget().updateAll(context) }
}
