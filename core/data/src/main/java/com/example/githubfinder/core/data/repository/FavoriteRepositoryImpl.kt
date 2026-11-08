/*
 * Abstract:
 * ローカルデータソースを使用するお気に入りリポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.database.FavoriteLocalDataSource
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(
    private val localDataSource: FavoriteLocalDataSource,
) : FavoriteRepository {

    override val favorites: Flow<List<FavoriteEntity>> = localDataSource.favorites
    override suspend fun add(repository: RepositoryEntity) = localDataSource.add(repository)
    override suspend fun remove(id: Int) = localDataSource.remove(id)
}
