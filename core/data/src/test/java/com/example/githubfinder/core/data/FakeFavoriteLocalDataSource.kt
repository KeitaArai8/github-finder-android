package com.example.githubfinder.core.data

import com.example.githubfinder.core.database.FavoriteDao
import com.example.githubfinder.core.database.FavoriteDbEntity
import com.example.githubfinder.core.database.FavoriteLocalDataSource
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * テスト用のin-memory FavoriteDao実装。
 * FavoriteLocalDataSourceに差し込んで永続化動作を検証するために使用する。
 */
class FakeFavoriteDao : FavoriteDao {
    private val store = mutableListOf<FavoriteDbEntity>()
    private val snapshot = MutableStateFlow<List<FavoriteDbEntity>>(emptyList())

    override fun observeAll(): Flow<List<FavoriteDbEntity>> = snapshot

    /** store の変更を購読者へ流す（Room のテーブル更新通知の代わり）。 */
    private fun publish() {
        snapshot.value = store.sortedByDescending { it.savedAt }
    }

    override suspend fun insert(entity: FavoriteDbEntity) {
        store.removeAll { it.repositoryID == entity.repositoryID }
        store.add(entity)
        publish()
    }

    override suspend fun deleteById(id: Int) {
        store.removeAll { it.repositoryID == id }
        publish()
    }
}

fun makeFavoriteLocalDataSource(dao: FavoriteDao = FakeFavoriteDao()): FavoriteLocalDataSource = FavoriteLocalDataSource(dao)

fun makeRepositoryEntity(id: Int): RepositoryEntity = RepositoryEntity(
    id = id,
    fullName = "owner/repo$id",
    description = null,
    starCount = 0,
    language = null,
    ownerAvatarURL = null,
)

fun makeFavoriteEntity(id: Int, savedAt: Long = 0L): FavoriteEntity = FavoriteEntity(
    id = id,
    fullName = "owner/repo$id",
    description = null,
    starCount = 0,
    language = null,
    ownerAvatarURL = null,
    savedAt = savedAt,
)
