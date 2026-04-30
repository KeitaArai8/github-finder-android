/*
 * Abstract:
 * Roomデータベース定義。
 */

package com.example.githubfinder.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteDbEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
