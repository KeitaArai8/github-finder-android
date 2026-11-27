/*
 * Abstract:
 * 用途ごとの `DataStore<Preferences>` を区別する Qualifier。
 */

package com.example.githubfinder.core.datastore.di

import javax.inject.Qualifier

/** 検索履歴専用の `DataStore<Preferences>` を指す Qualifier。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class SearchHistoryDataStore

/** チュートリアル専用の `DataStore<Preferences>` を指す Qualifier。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class TutorialDataStore
