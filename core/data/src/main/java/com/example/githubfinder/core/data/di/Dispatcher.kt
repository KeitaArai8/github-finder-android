/*
 * Abstract:
 * CoroutineDispatcher を識別する Qualifier（Now in Android 方式）。
 */

package com.example.githubfinder.core.data.di

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
