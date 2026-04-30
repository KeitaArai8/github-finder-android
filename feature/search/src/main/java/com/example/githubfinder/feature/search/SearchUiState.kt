/*
 * Abstract:
 * 検索画面の集約UI状態。UDF単一状態原則に従い、searchQuery/
 * ローディング・リフレッシュ・表示フェーズをすべて1つのsealed interfaceに統合する。
 *
 * 一度きりのイベント（SnackBar 表示等）は SearchUiEvent として Channel 経由で発火し、
 * View で receiveAsFlow().collect で消化する（State には保持しない）。
 */

package com.example.githubfinder.feature.search

import com.example.githubfinder.core.model.RepositoryDisplayItem

sealed interface SearchUiState {
    val searchQuery: String

    data class Idle(
        override val searchQuery: String = "",
        /** Idle 時のみ意味を持つ「最近の検索履歴」。 */
        val history: List<String> = emptyList(),
    ) : SearchUiState

    data class Loading(
        override val searchQuery: String,
    ) : SearchUiState

    data class Loaded(
        override val searchQuery: String,
        val items: List<RepositoryDisplayItem>,
        val isLoadingMore: Boolean = false,
        val isRefreshing: Boolean = false,
    ) : SearchUiState

    data class Empty(
        override val searchQuery: String,
    ) : SearchUiState

    data class Error(
        override val searchQuery: String,
        val message: String,
    ) : SearchUiState
}

/**
 * 検索画面の一度きりのイベント。
 * State には含めず Channel 経由で発火する。
 */
sealed interface SearchUiEvent {
    /**
     * SnackBar を 1 回だけ表示するイベント。
     * 「snackBar」と分割表記しているのは IDE の spell checker が "snackbar"（一語）を
     * 辞書未登録扱いするため。意味は Material 3 の `Snackbar` と同一。
     */
    data class ShowSnackBar(val message: String) : SearchUiEvent
}
