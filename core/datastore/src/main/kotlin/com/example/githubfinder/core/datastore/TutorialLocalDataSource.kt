/*
 * Abstract:
 * チュートリアル表示済みフラグを DataStore Preferences に永続化するデータソース。
 */

package com.example.githubfinder.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.githubfinder.core.datastore.di.TutorialDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TutorialLocalDataSource @Inject constructor(
    @TutorialDataStore private val dataStore: DataStore<Preferences>,
) {

    /** チュートリアルを最後まで（またはスキップして）表示済みなら true。未記録は false。 */
    val isShown: Flow<Boolean> = dataStore.data.map { it[KEY_SHOWN] ?: false }

    /** 表示済みとして永続化する。 */
    suspend fun setShown() {
        dataStore.edit { it[KEY_SHOWN] = true }
    }

    private companion object {
        // キー名は SharedPreferences ("tutorial") の "hasCompletedTutorial" と一致させる必要がある。変えると移行で値が引き継がれない。
        val KEY_SHOWN = booleanPreferencesKey("hasCompletedTutorial")
    }
}
