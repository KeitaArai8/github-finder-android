/*
 * Abstract:
 * Repository interface と実装のHilt DIバインド定義。
 */

package com.example.githubfinder.core.data.di

import com.example.githubfinder.core.data.datasource.SearchHistoryDataSource
import com.example.githubfinder.core.data.datasource.SearchHistoryDataSourceImpl
import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.data.repository.FavoriteRepositoryImpl
import com.example.githubfinder.core.data.repository.SearchRepository
import com.example.githubfinder.core.data.repository.SearchRepositoryImpl
import com.example.githubfinder.core.data.util.ConnectivityManagerNetworkMonitor
import com.example.githubfinder.core.data.util.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    internal abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    internal abstract fun bindFavoriteRepository(impl: FavoriteRepositoryImpl): FavoriteRepository

    @Binds
    @Singleton
    internal abstract fun bindSearchHistoryDataSource(impl: SearchHistoryDataSourceImpl): SearchHistoryDataSource

    @Binds
    @Singleton
    internal abstract fun bindNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor
}
