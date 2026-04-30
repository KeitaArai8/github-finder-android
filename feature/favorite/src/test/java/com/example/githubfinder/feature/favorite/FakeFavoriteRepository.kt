package com.example.githubfinder.feature.favorite

import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity

class FakeFavoriteRepository : FavoriteRepository {
    var favorites: MutableList<FavoriteEntity> = mutableListOf()
    var favoriteIDs: MutableSet<Int> = mutableSetOf()
    var removeCallCount = 0
    var lastRemovedID: Int? = null
    var shouldThrowOnGetAll = false
    var shouldThrowOnRemove = false
    var getAllError: Exception? = null

    override suspend fun getAll(): List<FavoriteEntity> {
        getAllError?.let { throw it }
        if (shouldThrowOnGetAll) throw Exception("Mock error")
        return favorites.toList()
    }
    override suspend fun add(repository: RepositoryEntity) { favoriteIDs.add(repository.id) }
    override suspend fun remove(id: Int) {
        if (shouldThrowOnRemove) throw Exception("Mock error")
        removeCallCount++
        lastRemovedID = id
        favorites.removeAll { it.id == id }
        favoriteIDs.remove(id)
    }
    override suspend fun contains(id: Int): Boolean = favoriteIDs.contains(id)
    override suspend fun allFavoriteIDs(): Set<Int> = favoriteIDs.toSet()
}
