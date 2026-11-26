/*
 * Abstract:
 * 検索画面の集約UI状態。UDF単一状態原則に従い、searchQuery/
 * ローディング・リフレッシュ・表示フェーズをすべて1つのsealed interfaceに統合する。
 *
 * 一度きりの通知（SnackBar 表示等）は uiState とは別の userMessages（UserMessage のキュー）として
 * ViewModel が保持し、View が表示し終えたら userMessageShown(id) で消す。
 */

package com.example.githubfinder.feature.search

import com.example.githubfinder.core.designsystem.UiText
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
        val message: UiText,
    ) : SearchUiState
}
