/*
 * Abstract:
 * お気に入り状態を一元管理する共有ストア。SearchViewModelとFavoriteViewModelの両方がこのストアを参照する。
 *
 * 状態の SSOT は Room（Repository.favorites の Flow）。ここは手製キャッシュを持たず、
 * アプリ寿命スコープで Flow を購読して StateFlow として保持するだけの薄い橋渡し。
 * add / remove は Repository へ委譲するだけで、Room の emission が favorites / favoriteIDs を更新する。
 * Eagerly にしているのは、タブ復帰時に初期値（空）を経由する Loading 点滅を避け、
 * SearchViewModel が購読する favoriteIDs を常に最新に保つため。
 */

package com.example.githubfinder.core.data

import com.example.githubfinder.core.data.di.ApplicationScope
import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteStore @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    val favorites: StateFlow<List<FavoriteEntity>> = favoriteRepository.favorites
        .distinctUntilChanged()
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())

    val favoriteIDs: StateFlow<Set<Int>> = favorites
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptySet())

    suspend fun add(repository: RepositoryEntity) = favoriteRepository.add(repository)

    suspend fun remove(id: Int) = favoriteRepository.remove(id)
}
