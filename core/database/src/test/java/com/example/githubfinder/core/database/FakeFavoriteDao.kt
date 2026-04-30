package com.example.githubfinder.core.database

/**
 * テスト用のin-memory FavoriteDao実装。
 * FavoriteLocalDataSourceに差し込んで永続化動作を検証するために使用する。
 */
class FakeFavoriteDao : FavoriteDao {
    private val store = mutableListOf<FavoriteDbEntity>()
    private var nextSavedAt = 0L

    override suspend fun getAll(): List<FavoriteDbEntity> =
        store.sortedByDescending { it.savedAt }

    override suspend fun insert(entity: FavoriteDbEntity) {
        store.removeAll { it.repositoryID == entity.repositoryID }
        nextSavedAt += 1
        store.add(entity.copy(savedAt = nextSavedAt))
    }

    override suspend fun deleteById(id: Int) {
        store.removeAll { it.repositoryID == id }
    }

    override suspend fun countById(id: Int): Int = store.count { it.repositoryID == id }

    override suspend fun getAllIds(): List<Int> = store.map { it.repositoryID }

    /**
     * テスト用に明示的な savedAt で挿入するヘルパー。
     */
    fun insertWithSavedAt(entity: FavoriteDbEntity, savedAt: Long) {
        store.removeAll { it.repositoryID == entity.repositoryID }
        store.add(entity.copy(savedAt = savedAt))
    }
}
