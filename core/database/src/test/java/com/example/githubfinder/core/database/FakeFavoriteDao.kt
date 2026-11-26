package com.example.githubfinder.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * テスト用のin-memory FavoriteDao実装。
 * FavoriteLocalDataSourceに差し込んで永続化動作を検証するために使用する。
 */
class FakeFavoriteDao : FavoriteDao {
    private val store = mutableListOf<FavoriteDbEntity>()
    private var nextSavedAt = 0L
    private val snapshot = MutableStateFlow<List<FavoriteDbEntity>>(emptyList())

    override fun observeAll(): Flow<List<FavoriteDbEntity>> = snapshot

    /** store の変更を購読者へ流す（Room のテーブル更新通知の代わり）。 */
    private fun publish() {
        snapshot.value = store.sortedByDescending { it.savedAt }
    }

    override suspend fun insert(entity: FavoriteDbEntity) {
        store.removeAll { it.repositoryID == entity.repositoryID }
        nextSavedAt += 1
        store.add(entity.copy(savedAt = nextSavedAt))
        publish()
    }

    override suspend fun deleteById(id: Int) {
        store.removeAll { it.repositoryID == id }
        publish()
    }

    /**
     * テスト用に明示的な savedAt で挿入するヘルパー。
     */
    fun insertWithSavedAt(entity: FavoriteDbEntity, savedAt: Long) {
        store.removeAll { it.repositoryID == entity.repositoryID }
        store.add(entity.copy(savedAt = savedAt))
        publish()
    }
}
