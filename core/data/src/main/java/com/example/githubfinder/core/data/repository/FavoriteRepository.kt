/*
 * Abstract:
 * お気に入りのデータアクセス契約。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    /** お気に入りの一覧（保存日時の新しい順）。追加・削除のたびに最新リストが流れる。 */
    val favorites: Flow<List<FavoriteEntity>>
    suspend fun add(repository: RepositoryEntity)
    suspend fun remove(id: Int)
}
