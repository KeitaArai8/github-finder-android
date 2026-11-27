/*
 * Abstract:
 * ローカルデータソースを使用するお気に入りリポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.database.FavoriteLocalDataSource
import com.example.githubfinder.core.model.Favorite
import com.example.githubfinder.core.model.GitHubRepo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
internal class FavoriteRepositoryImpl @Inject constructor(
    private val localDataSource: FavoriteLocalDataSource,
) : FavoriteRepository {

    override val favorites: Flow<List<Favorite>> = localDataSource.favorites
    override suspend fun add(repository: GitHubRepo) = localDataSource.add(repository)
    override suspend fun remove(id: Int) = localDataSource.remove(id)
}
