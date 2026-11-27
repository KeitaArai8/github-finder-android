/*
 * Abstract:
 * 検索履歴のデータアクセス契約。
 * - 直近 10 件を新しい順に保持
 * - 同一クエリ追加時は重複排除して先頭に再挿入
 * - 空白のみ・空文字は無視
 */

package com.example.githubfinder.core.data.repository

import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    /** 履歴の最新スナップショット Stream（新しい順）。 */
    val history: Flow<List<String>>

    /** 履歴に追加（重複排除 + 先頭挿入 + 上限切り詰め）。空白のみのクエリは無視する。 */
    suspend fun add(rawQuery: String)

    /** 単一エントリを削除する。 */
    suspend fun remove(query: String)

    /** 履歴を全消去する。 */
    suspend fun clear()
}
