/*
 * Abstract:
 * ローカルデータソースを使用するお気に入りリポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.database.FavoriteLocalDataSource
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepositoryImpl @Inject constructor(
    private val localDataSource: FavoriteLocalDataSource,
) : FavoriteRepository {

    override suspend fun getAll(): List<FavoriteEntity> = localDataSource.getAll()
    override suspend fun add(repository: RepositoryEntity) = localDataSource.add(repository)
    override suspend fun remove(id: Int) = localDataSource.remove(id)
    override suspend fun contains(id: Int): Boolean = localDataSource.contains(id)
    override suspend fun allFavoriteIDs(): Set<Int> = localDataSource.allIDs()
}
