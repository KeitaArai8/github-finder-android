package com.example.githubfinder.feature.search

import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity

class FakeFavoriteRepository : FavoriteRepository {
    var favorites: MutableList<FavoriteEntity> = mutableListOf()
    var favoriteIDs: MutableSet<Int> = mutableSetOf()
    var addCallCount = 0
    var removeCallCount = 0
    var lastAddedRepository: RepositoryEntity? = null
    var lastRemovedID: Int? = null
    var shouldThrowOnAdd = false

    override suspend fun getAll(): List<FavoriteEntity> = favorites.toList()
    override suspend fun add(repository: RepositoryEntity) {
        if (shouldThrowOnAdd) throw Exception("Mock error")
        addCallCount++
        lastAddedRepository = repository
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
        removeCallCount++
        lastRemovedID = id
        favoriteIDs.remove(id)
        favorites.removeAll { it.id == id }
    }
    override suspend fun contains(id: Int): Boolean = favoriteIDs.contains(id)
    override suspend fun allFavoriteIDs(): Set<Int> = favoriteIDs.toSet()
}
