/*
 * Abstract:
 * チュートリアル表示済みフラグの永続化を抽象化する interface。
 * 実装は TutorialLocalDataSourceImpl（Preferences DataStore）、テスト用には Fake を差し替えて使う。
 */

package com.example.githubfinder.core.designsystem.tutorial

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TutorialLocalDataSource {
    /** チュートリアルを最後まで（またはスキップして）表示済みなら true。未記録は false。 */
    val isShown: Flow<Boolean>

    /** 表示済みとして永続化する。 */
    suspend fun setShown()
}

class TutorialLocalDataSourceImpl @Inject constructor(
    @TutorialDataStore private val dataStore: DataStore<Preferences>,
) : TutorialLocalDataSource {

    override val isShown: Flow<Boolean> = dataStore.data.map { it[KEY_SHOWN] ?: false }

    override suspend fun setShown() {
        dataStore.edit { it[KEY_SHOWN] = true }
    }

    companion object {
        // 旧 SharedPreferences ("tutorial") と同じキー名。移行時にそのまま引き継ぐ。
        internal val KEY_SHOWN = booleanPreferencesKey("hasCompletedTutorial")
    }
}

/** チュートリアル専用の `DataStore<Preferences>` を指す Qualifier。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class TutorialDataStore

/** [CoachMarkManager] が表示済みフラグを書き込むための、アプリ寿命の CoroutineScope を指す Qualifier。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class TutorialScope

private const val LEGACY_PREFS_NAME = "tutorial"

/** 旧 SharedPreferences からの移行を伴う DataStore。既にチュートリアルを見たユーザーに再表示しないため。 */
private val Context.tutorialDataStore by preferencesDataStore(
    name = "tutorial",
    produceMigrations = { context -> listOf(SharedPreferencesMigration(context, LEGACY_PREFS_NAME)) },
)

@Module
@InstallIn(SingletonComponent::class)
abstract class TutorialModule {
    @Binds
    @Singleton
    abstract fun bindTutorialLocalDataSource(impl: TutorialLocalDataSourceImpl): TutorialLocalDataSource

    companion object {
        @Provides
        @Singleton
        @TutorialDataStore
        fun provideTutorialDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.tutorialDataStore

        @Provides
        @Singleton
        @TutorialScope
        fun provideTutorialScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
