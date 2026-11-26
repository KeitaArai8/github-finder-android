/*
 * Abstract:
 * 検索履歴の永続化を抽象化する DataSource インターフェース。
 * - 直近 MAX_ENTRIES 件（10）を新しい順に保持
 * - 同一クエリ追加時は重複排除して先頭に再挿入
 * - 空白のみ・空文字は無視
 *
 * 本番実装は DataStore Preferences を使う `SearchHistoryDataSourceImpl`。
 * ユニットテストでは Fake を渡せるよう interface 化している。
 */

package com.example.githubfinder.core.data.datasource

import kotlinx.coroutines.flow.Flow

interface SearchHistoryDataSource {
    /** 履歴の最新スナップショット Stream（新しい順）。 */
    val history: Flow<List<String>>

    /** 履歴に追加（重複排除 + 先頭挿入 + 上限切り詰め）。空白のみのクエリは無視する。 */
    suspend fun add(rawQuery: String)

    /** 単一エントリを削除する。 */
    suspend fun remove(query: String)

    /** 履歴を全消去する。 */
    suspend fun clear()
}
