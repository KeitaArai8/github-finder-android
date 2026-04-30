/*
 * Abstract:
 * お気に入りテーブルのRoom DAOインターフェース。
 */

package com.example.githubfinder.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    suspend fun getAll(): List<FavoriteDbEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteDbEntity)

    @Query("DELETE FROM favorites WHERE repositoryID = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM favorites WHERE repositoryID = :id")
    suspend fun countById(id: Int): Int

    @Query("SELECT repositoryID FROM favorites")
    suspend fun getAllIds(): List<Int>
}
