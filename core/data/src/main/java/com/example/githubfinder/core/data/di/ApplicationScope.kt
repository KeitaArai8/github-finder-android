/*
 * Abstract:
 * アプリ寿命の CoroutineScope を識別する Qualifier。
 */

package com.example.githubfinder.core.data.di

import javax.inject.Qualifier

/**
 * アプリ寿命の CoroutineScope（[DataModule] が提供）を指す Qualifier。
 * 画面の ViewModel より長く生きる必要がある処理（スワイプ削除の実削除 commit など）に使う。
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
