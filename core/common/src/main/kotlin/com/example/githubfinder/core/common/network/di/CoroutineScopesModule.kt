/*
 * Abstract:
 * アプリ寿命の CoroutineScope を識別する Qualifier（[ApplicationScope]）と、その Hilt Provider。
 */

package com.example.githubfinder.core.common.network.di

import com.example.githubfinder.core.common.network.Dispatcher
import com.example.githubfinder.core.common.network.GitHubFinderDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

/**
 * アプリ寿命の CoroutineScope（[CoroutineScopesModule] が提供）を指す Qualifier。
 * 画面の ViewModel より長く生きる必要がある処理（スワイプ削除の実削除 commit など）に使う。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
internal object CoroutineScopesModule {

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
