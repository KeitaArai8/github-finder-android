/*
 * Abstract:
 * FavoriteDao のテスト用 in-memory 実装。
 * FavoriteLocalDataSource に差し込んで永続化動作を検証するために使う。
 */

package com.example.githubfinder.core.testing

import com.example.githubfinder.core.database.dao.FavoriteDao
import com.example.githubfinder.core.database.model.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 本物の DAO と同じく、同じ repositoryID の再 insert は置き換え（REPLACE）、observeAll は savedAt の新しい順で流す。
 * [insert] は呼び出し順に増える savedAt を割り当てる（実時刻に依存せず順序を確定させるため）。
 * savedAt を明示したい場合は [insertWithSavedAt] を使う。
 */
class FakeFavoriteDao : FavoriteDao {
    private val store = mutableListOf<FavoriteEntity>()
    private var nextSavedAt = 0L
    private val snapshot = MutableStateFlow<List<FavoriteEntity>>(emptyList())

    override fun observeAll(): Flow<List<FavoriteEntity>> = snapshot

    /** store の変更を購読者へ流す（Room のテーブル更新通知の代わり）。 */
    private fun publish() {
        snapshot.value = store.sortedByDescending { it.savedAt }
    }

    override suspend fun insert(entity: FavoriteEntity) {
        store.removeAll { it.repositoryID == entity.repositoryID }
        nextSavedAt += 1
        store.add(entity.copy(savedAt = nextSavedAt))
        publish()
    }

    override suspend fun deleteById(id: Int) {
        store.removeAll { it.repositoryID == id }
        publish()
    }

    /** 明示的な savedAt で挿入する（並び順の検証用）。 */
    fun insertWithSavedAt(entity: FavoriteEntity, savedAt: Long) {
        store.removeAll { it.repositoryID == entity.repositoryID }
        store.add(entity.copy(savedAt = savedAt))
        publish()
    }
}
