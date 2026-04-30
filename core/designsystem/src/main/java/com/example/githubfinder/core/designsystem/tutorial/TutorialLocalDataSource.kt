/*
 * Abstract:
 * チュートリアル完了フラグの永続化を抽象化する interface。
 * 実装は TutorialLocalDataSourceImpl、テスト用には Fake を差し替えて使う。
 */

package com.example.githubfinder.core.designsystem.tutorial

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface TutorialLocalDataSource {
    val hasCompleted: Boolean
    fun markCompleted()
}

@Singleton
class TutorialLocalDataSourceImpl @Inject constructor(
    @ApplicationContext context: Context,
) : TutorialLocalDataSource {
    private val prefs = context.getSharedPreferences("tutorial", Context.MODE_PRIVATE)

    override val hasCompleted: Boolean
        get() = prefs.getBoolean(KEY_HAS_COMPLETED, false)

    override fun markCompleted() {
        prefs.edit().putBoolean(KEY_HAS_COMPLETED, true).apply()
    }

    companion object {
        private const val KEY_HAS_COMPLETED = "hasCompletedTutorial"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TutorialModule {
    @Binds
    @Singleton
    abstract fun bindTutorialLocalDataSource(impl: TutorialLocalDataSourceImpl): TutorialLocalDataSource
}
