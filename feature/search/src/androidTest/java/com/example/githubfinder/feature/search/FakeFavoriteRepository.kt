package com.example.githubfinder.feature.search

import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * MutableStateFlow を SSOT にしたテスト用 Repository（本物の Room と同様、変更のたびに最新リストが流れる）。
 * [stored] へ代入すると購読者へ emission される。
 */
class FakeFavoriteRepository : FavoriteRepository {
    private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    override val favorites: Flow<List<FavoriteEntity>> = flow

    var stored: List<FavoriteEntity>
        get() = flow.value
        set(value) {
            flow.value = value
        }

    var addCallCount = 0
    var removeCallCount = 0
    var lastAddedRepository: RepositoryEntity? = null
    var lastRemovedID: Int? = null
    var shouldThrowOnAdd = false

    override suspend fun add(repository: RepositoryEntity) {
        if (shouldThrowOnAdd) throw Exception("Mock error")
        addCallCount++
        lastAddedRepository = repository
        stored = stored + FavoriteEntity(
            id = repository.id,
            fullName = repository.fullName,
            description = repository.description,
            starCount = repository.starCount,
            language = repository.language,
            ownerAvatarURL = repository.ownerAvatarURL,
            savedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun remove(id: Int) {
        removeCallCount++
        lastRemovedID = id
        stored = stored.filterNot { it.id == id }
    }
}
