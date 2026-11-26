/*
 * Abstract:
 * Repository interface と実装のHilt DIバインド定義。
 */

package com.example.githubfinder.core.data.di

import com.example.githubfinder.core.data.datasource.SearchHistoryDataSource
import com.example.githubfinder.core.data.datasource.SearchHistoryDataSourceImpl
import com.example.githubfinder.core.data.network.ConnectivityManagerNetworkMonitor
import com.example.githubfinder.core.data.network.NetworkMonitor
import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.data.repository.FavoriteRepositoryImpl
import com.example.githubfinder.core.data.repository.SearchRepository
import com.example.githubfinder.core.data.repository.SearchRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindFavoriteRepository(impl: FavoriteRepositoryImpl): FavoriteRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryDataSource(impl: SearchHistoryDataSourceImpl): SearchHistoryDataSource

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor

    companion object {
        /**
         * アプリ寿命のスコープ。SupervisorJob により、1 件の失敗が他の処理を巻き込まない。
         * launch で捕捉されなかった例外は CoroutineExceptionHandler でログに残し、プロセスを落とさない。
         */
        @Provides
        @Singleton
        @ApplicationScope
        fun providesApplicationScope(
            @Dispatcher(GitHubFinderDispatchers.Default) dispatcher: CoroutineDispatcher,
        ): CoroutineScope = CoroutineScope(
            SupervisorJob() + dispatcher +
                CoroutineExceptionHandler { _, e -> Timber.tag("ApplicationScope").e(e, "Unhandled exception in application scope") },
        )
    }
}
