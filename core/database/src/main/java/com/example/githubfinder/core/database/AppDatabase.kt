/*
 * Abstract:
 * Roomデータベース定義。
 */

package com.example.githubfinder.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.githubfinder.core.database.dao.FavoriteDao

/**
 * Room データベース。スキーマは `core/database/schemas/` に JSON で書き出してコミットする。
 *
 * スキーマを変更するときは version を上げ、`AutoMigration`（`@Database(autoMigrations = …)`）か
 * 手書きの `Migration` を必ず用意する。`fallbackToDestructiveMigration` でデータを捨てない
 * （お気に入りはユーザーが保存したデータのため）。
 */
@Database(entities = [FavoriteDbEntity::class], version = 1, exportSchema = true)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
}
