package com.example.githubfinder.feature.search

import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity

class FakeFavoriteRepository : FavoriteRepository {
    private val favorites = mutableListOf<FavoriteEntity>()
    private val favoriteIDs = mutableSetOf<Int>()

    override suspend fun getAll(): List<FavoriteEntity> = favorites.toList()

    override suspend fun add(repository: RepositoryEntity) {
        favoriteIDs.add(repository.id)
        favorites.add(
            FavoriteEntity(
                id = repository.id,
                fullName = repository.fullName,
                description = repository.description,
                starCount = repository.starCount,
                language = repository.language,
                ownerAvatarURL = repository.ownerAvatarURL,
                savedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun remove(id: Int) {
        favoriteIDs.remove(id)
        favorites.removeAll { it.id == id }
    }

    override suspend fun contains(id: Int): Boolean = favoriteIDs.contains(id)
    override suspend fun allFavoriteIDs(): Set<Int> = favoriteIDs.toSet()
}
