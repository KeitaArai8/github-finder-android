/*
 * Abstract:
 * お気に入りテーブルのRoom DAOインターフェース。
 */

package com.example.githubfinder.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    /** 保存日時の新しい順に全件を流す。テーブル更新のたびに最新リストが再送される（お気に入りの SSOT）。 */
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<FavoriteDbEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteDbEntity)

    @Query("DELETE FROM favorites WHERE repositoryID = :id")
    suspend fun deleteById(id: Int)
}
