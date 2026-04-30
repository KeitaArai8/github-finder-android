/*
 * Abstract:
 * お気に入り状態を一元管理する共有ストア。SearchViewModelとFavoriteViewModelの両方がこのストアを参照する。
 */

package com.example.githubfinder.core.data

import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteStore @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
) {
    private val _favoriteIDs = MutableStateFlow<Set<Int>>(emptySet())
    val favoriteIDs: StateFlow<Set<Int>> = _favoriteIDs.asStateFlow()

    private val _favorites = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    val favorites: StateFlow<List<FavoriteEntity>> = _favorites.asStateFlow()

    suspend fun refresh() {
        val all = favoriteRepository.getAll()
        _favorites.value = all
        _favoriteIDs.value = all.map { it.id }.toSet()
    }

    suspend fun add(repository: RepositoryEntity) {
        favoriteRepository.add(repository)
        refresh()
    }

    suspend fun remove(id: Int) {
        favoriteRepository.remove(id)
        refresh()
    }
}
