/*
 * Abstract:
 * お気に入りのデータアクセス契約。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity

interface FavoriteRepository {
    suspend fun getAll(): List<FavoriteEntity>
    suspend fun add(repository: RepositoryEntity)
    suspend fun remove(id: Int)
    suspend fun contains(id: Int): Boolean
    suspend fun allFavoriteIDs(): Set<Int>
}
