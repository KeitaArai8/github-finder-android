/*
 * Abstract:
 * Hiltで管理されるFavoriteRepositoryをGlance Widgetから取得するためのEntryPoint。
 * Glance Widget は Hilt のスコープ外で動作するため、EntryPointAccessors 経由でアクセスする。
 */

package com.example.githubfinder.feature.widget

import com.example.githubfinder.core.data.repository.FavoriteRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun favoriteRepository(): FavoriteRepository
}
