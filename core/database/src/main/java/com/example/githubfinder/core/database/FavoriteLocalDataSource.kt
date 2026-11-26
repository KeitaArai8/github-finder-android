/*
 * Abstract:
 * Roomを使用したお気に入りのローカル永続化を担当するデータソース。
 */

package com.example.githubfinder.core.database

import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FavoriteLocalDataSource @Inject constructor(
    private val favoriteDao: FavoriteDao,
) {
    /** Room の変更をそのまま流す（保存日時の新しい順）。 */
    val favorites: Flow<List<FavoriteEntity>> =
        favoriteDao.observeAll().map { list -> list.map { it.toEntity() } }

    suspend fun add(repository: RepositoryEntity) {
        favoriteDao.insert(
            FavoriteDbEntity(
                repositoryID = repository.id,
                fullName = repository.fullName,
                description = repository.description,
                starCount = repository.starCount,
                language = repository.language,
                ownerAvatarURL = repository.ownerAvatarURL,
            ),
        )
    }

    suspend fun remove(id: Int) = favoriteDao.deleteById(id)
}
