/*
 * Abstract:
 * CoroutineDispatcher を識別する Qualifier と、注入できる Dispatcher を表す enum（Now in Android の NiaDispatchers 相当）。
 */

package com.example.githubfinder.core.common.network

import javax.inject.Qualifier

/**
 * 注入する CoroutineDispatcher を指す Qualifier。
 * `Dispatchers.Default` を直書きせず注入することで、テストでテスト用 Dispatcher に差し替えられる。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: GitHubFinderDispatchers)

/** [Dispatcher] で指定できる Dispatcher。このアプリが注入するのは Default だけ。 */
enum class GitHubFinderDispatchers {
    Default,
}
