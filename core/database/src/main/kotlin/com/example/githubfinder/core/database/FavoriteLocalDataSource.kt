/*
 * Abstract:
 * Roomを使用したお気に入りのローカル永続化を担当するデータソース。
 */

package com.example.githubfinder.core.database

import com.example.githubfinder.core.database.dao.FavoriteDao
import com.example.githubfinder.core.database.model.FavoriteEntity
import com.example.githubfinder.core.database.model.asExternalModel
import com.example.githubfinder.core.model.Favorite
import com.example.githubfinder.core.model.GitHubRepo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FavoriteLocalDataSource @Inject constructor(
    private val favoriteDao: FavoriteDao,
) {
    /** Room の変更をそのまま流す（保存日時の新しい順）。 */
    val favorites: Flow<List<Favorite>> =
        favoriteDao.observeAll().map { list -> list.map { it.asExternalModel() } }

    suspend fun add(repository: GitHubRepo) {
        favoriteDao.insert(
            FavoriteEntity(
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
