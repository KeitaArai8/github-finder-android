/*
 * Abstract:
 * CoroutineDispatcher を識別する Qualifier と、選べる Dispatcher の種類（Now in Android の NiaDispatchers 相当）。
 */

package com.example.githubfinder.core.common.network

import javax.inject.Qualifier

/**
 * 注入する CoroutineDispatcher の種類を指す Qualifier。
 * `Dispatchers.IO` などを直書きせず注入することで、テストでテスト用 Dispatcher に差し替えられる。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: GitHubFinderDispatchers)

/** [Dispatcher] で選べる Dispatcher の種類。 */
enum class GitHubFinderDispatchers {
    Default,
}
