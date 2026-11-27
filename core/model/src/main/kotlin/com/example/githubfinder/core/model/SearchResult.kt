/*
 * Abstract:
 * 検索結果を表すドメインモデル。ページネーション情報を含む。
 */

package com.example.githubfinder.core.model

data class SearchResult(
    val totalCount: Int,
    val items: List<GitHubRepo>,
    val hasNextPage: Boolean,
)
