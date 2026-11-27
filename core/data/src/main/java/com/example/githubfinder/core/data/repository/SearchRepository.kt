/*
 * Abstract:
 * リポジトリ検索のデータアクセス契約。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.model.SearchResultEntity

interface SearchRepository {
    /**
     * [query] の [page] ページ目を検索する。
     * 既定ではメモリキャッシュを先に読む。[refresh] が true のときはキャッシュを読まずにネットワークから取得し、
     * 結果でキャッシュを置き換える（同じクエリの他ページのキャッシュは古くなるため破棄する）。
     */
    suspend fun search(query: String, page: Int, refresh: Boolean = false): SearchResultEntity
}
