/*
 * Abstract:
 * リポジトリ検索のデータアクセス契約。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.model.SearchResultEntity

interface SearchRepository {
    suspend fun search(query: String, page: Int): SearchResultEntity
}
