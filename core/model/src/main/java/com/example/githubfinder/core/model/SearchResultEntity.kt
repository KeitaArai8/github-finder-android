/*
 * Abstract:
 * 検索結果を表すドメインエンティティ。ページネーション情報を含む。
 */

package com.example.githubfinder.core.model

data class SearchResultEntity(
    val totalCount: Int,
    val items: List<RepositoryEntity>,
    val hasNextPage: Boolean,
)
