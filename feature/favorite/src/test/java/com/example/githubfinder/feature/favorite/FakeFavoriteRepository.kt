package com.example.githubfinder.feature.favorite

import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * MutableStateFlow を SSOT にしたテスト用 Repository（本物の Room と同様、変更のたびに最新リストが流れる）。
 * [stored] へ代入すると購読者へ emission される。
 */
class FakeFavoriteRepository : FavoriteRepository {
    private val flow = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    override val favorites: Flow<List<FavoriteEntity>> = flow

    /** 現在保存されているお気に入り。代入すると Room の更新と同様に emission される。 */
    var stored: List<FavoriteEntity>
        get() = flow.value
        set(value) {
            flow.value = value
        }

    var removeCallCount = 0
    var lastRemovedID: Int? = null
    var shouldThrowOnRemove = false

    /** 非 null の間、[remove] はこの Deferred が完了するまで中断する（Room 書き込み中の ViewModel 破棄を再現する）。 */
    var removeGate: CompletableDeferred<Unit>? = null

    override suspend fun add(repository: RepositoryEntity) {
        stored = stored + FavoriteEntity(
            id = repository.id,
            fullName = repository.fullName,
            description = repository.description,
            starCount = repository.starCount,
            language = repository.language,
            ownerAvatarURL = repository.ownerAvatarURL,
            savedAt = 0L,
        )
    }

    override suspend fun remove(id: Int) {
        removeGate?.await()
        if (shouldThrowOnRemove) throw Exception("Mock error")
        removeCallCount++
        lastRemovedID = id
        stored = stored.filterNot { it.id == id }
    }
}
