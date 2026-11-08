/*
 * Abstract:
 * お気に入り一覧画面の集約UI状態。UDF単一状態原則に従い、
 * フェーズ・リフレッシュ状態を1つのsealed interfaceに統合する。
 * Loaded 内には sortKey / filterLanguage を保持し、表示用の displayedFavorites / availableLanguages は
 * ViewModel が入力の変化時に計算して Loaded に載せる（アクセスのたびに再ソートしない）。
 *
 * 一度きりの通知（SnackBar 表示）は uiState とは別の userMessages（UserMessage のキュー）として
 * ViewModel が保持し、View が表示し終えたら userMessageShown(id) で消す。
 */

package com.example.githubfinder.feature.favorite

import com.example.githubfinder.core.model.FavoriteEntity

/** お気に入り一覧の並び替え基準。 */
enum class FavoriteSortKey(val displayName: String) {
    SAVED_AT_DESC("新しい順"),
    STAR_COUNT_DESC("Star が多い順"),
    FULL_NAME_ASC("名前順 (A→Z)"),
}

sealed interface FavoriteUiState {
    data object Idle : FavoriteUiState

    data object Loading : FavoriteUiState

    data class Loaded(
        val favorites: List<FavoriteEntity>,
        val isRefreshing: Boolean = false,
        val isEditMode: Boolean = false,
        val selectedIDs: Set<Int> = emptySet(),
        val isDeleteConfirmationShown: Boolean = false,
        val sortKey: FavoriteSortKey = FavoriteSortKey.SAVED_AT_DESC,
        val filterLanguage: String? = null,
        /**
         * Undo 待ちの削除 ID 集合。
         * UI からは即座に消したい（楽観的更新）が、Room からは Undo 猶予（ViewModel のタイマー）が経過するまで実削除しない。
         * commit 後に Room から削除され、Flow 経由で favorites からも消える。
         */
        val pendingDeleteIDs: Set<Int> = emptySet(),
        /**
         * sortKey/filterLanguage/pendingDelete を適用した表示用リスト（ViewModel が計算済みの値を載せる）。
         * favorites 等の入力を変えたら ViewModel 側で再計算する。既定の空リストのまま公開しないこと。
         */
        val displayedFavorites: List<FavoriteEntity> = emptyList(),
        /** 言語フィルタチップに出す選択肢（生データから抽出した重複なしソート済み。ViewModel が計算済みの値を載せる）。 */
        val availableLanguages: List<String> = emptyList(),
    ) : FavoriteUiState {

        /**
         * 表示中のお気に入りすべてが選択済みかどうか。
         * 「全選択 ⇔ 全解除」トグルボタンのラベル切り替えに使う（iOS Photos.app 同等の defacto）。
         * フィルタ後の表示集合に対する判定なので、フィルタ中は「表示中の全 5 件選択」で true になる。
         */
        val isAllSelected: Boolean
            get() = displayedFavorites.isNotEmpty() &&
                selectedIDs == displayedFavorites.map { it.id }.toSet()
    }

    data object Empty : FavoriteUiState

    data class Error(val message: String) : FavoriteUiState
}
