/*
 * Abstract:
 * Roomを使用したお気に入りのローカル永続化を担当するデータソース。
 */

package com.example.githubfinder.core.database

import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteLocalDataSource @Inject constructor(
    private val favoriteDao: FavoriteDao,
) {
    suspend fun getAll(): List<FavoriteEntity> =
        favoriteDao.getAll().map { it.toEntity() }

    suspend fun add(repository: RepositoryEntity) {
        favoriteDao.insert(
            FavoriteDbEntity(
                repositoryID = repository.id,
                fullName = repository.fullName,
                description = repository.description,
                starCount = repository.starCount,
                language = repository.language,
                ownerAvatarURL = repository.ownerAvatarURL,
            )
        )
    }

    suspend fun remove(id: Int) = favoriteDao.deleteById(id)

    suspend fun contains(id: Int): Boolean = favoriteDao.countById(id) > 0

    suspend fun allIDs(): Set<Int> = favoriteDao.getAllIds().toSet()
}
