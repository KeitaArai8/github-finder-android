/*
 * Abstract:
 * [Dispatcher] Qualifier 付きの CoroutineDispatcher を提供する Hilt モジュール。
 */

package com.example.githubfinder.core.common.network.di

import com.example.githubfinder.core.common.network.Dispatcher
import com.example.githubfinder.core.common.network.GitHubFinderDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
internal object DispatchersModule {

    @Provides
    @Dispatcher(GitHubFinderDispatchers.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
