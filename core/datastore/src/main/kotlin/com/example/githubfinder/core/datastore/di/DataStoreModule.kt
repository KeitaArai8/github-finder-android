/*
 * Abstract:
 * 用途ごとの `DataStore<Preferences>` を提供する Hilt モジュール。
 * DataStore のファイル名（"search_history" / "tutorial"）は保存済みデータの場所そのもの。変えると既存ユーザーのデータが読めなくなる。
 */

package com.example.githubfinder.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val LEGACY_TUTORIAL_PREFS_NAME = "tutorial"

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

/** SharedPreferences ("tutorial") からの移行を外してはならない。外すと既にチュートリアルを見たユーザーに再表示される。 */
private val Context.tutorialDataStore by preferencesDataStore(
    name = "tutorial",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, LEGACY_TUTORIAL_PREFS_NAME)) },
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @SearchHistoryDataStore
    internal fun provideSearchHistoryDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.searchHistoryDataStore

    @Provides
    @Singleton
    @TutorialDataStore
    internal fun provideTutorialDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.tutorialDataStore
}
