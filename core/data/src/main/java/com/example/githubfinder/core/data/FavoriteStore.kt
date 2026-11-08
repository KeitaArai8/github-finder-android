/*
 * Abstract:
 * お気に入り状態を一元管理する共有ストア。SearchViewModelとFavoriteViewModelの両方がこのストアを参照する。
 *
 * 状態の SSOT は Room（Repository.favorites の Flow）。ここは手製キャッシュを持たず、
 * アプリ寿命スコープで Flow を購読して StateFlow として保持するだけの薄い橋渡し。
 * add / remove は Repository へ委譲するだけで、Room の emission が favorites / favoriteIDs を更新する。
 * Eagerly にしているのは、タブ復帰時に初期値を経由する Loading 点滅を避け、
 * SearchViewModel が購読する favoriteIDs を常に最新に保つため。
 *
 * favorites は「Room の最初の emission が届くまで null」。空リストを初期値にすると、
 * コールドスタート直後に購読者が「お気に入り 0 件」と誤認して Empty を一瞬描画してしまうため。
 * アプリ寿命スコープでの購読なので、Room のエラーでプロセスが落ちないよう upstream で catch して空リストに倒す。
 * 注意: Room の読み取りに失敗すると、アプリを再起動するまで favorites は空リストに劣化したまま（購読は再開しない）。
 */

package com.example.githubfinder.core.data

import com.example.githubfinder.core.data.di.ApplicationScope
import com.example.githubfinder.core.data.repository.FavoriteRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteStore @Inject constructor(
    private val favoriteRepository: FavoriteRepository,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    /** お気に入り一覧。最初の emission が届くまで null（= 読み込み中）。 */
    val favorites: StateFlow<List<FavoriteEntity>?> = favoriteRepository.favorites
        .catch { e ->
            // アプリスコープの購読が例外で落ちるとプロセスごと落ちるため、ログに残して空リストとして扱う
            Timber.tag("FavoriteStore").e(e, "Favorites collection failed")
            emit(emptyList())
        }
        .distinctUntilChanged()
        .stateIn(applicationScope, SharingStarted.Eagerly, null)

    /** お気に入り ID 集合。読み込み中（null）は空集合として扱う。 */
    val favoriteIDs: StateFlow<Set<Int>> = favorites
        .map { list -> list?.map { it.id }?.toSet() ?: emptySet() }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptySet())

    suspend fun add(repository: RepositoryEntity) = favoriteRepository.add(repository)

    suspend fun remove(id: Int) = favoriteRepository.remove(id)
}
