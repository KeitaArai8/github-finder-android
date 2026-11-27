/*
 * Abstract:
 * DataStore ベースのローカルデータソースを使用するチュートリアルリポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.datastore.TutorialLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
internal class TutorialRepositoryImpl @Inject constructor(
    private val localDataSource: TutorialLocalDataSource,
) : TutorialRepository {

    override val isShown: Flow<Boolean> = localDataSource.isShown
    override suspend fun setShown() = localDataSource.setShown()
}
