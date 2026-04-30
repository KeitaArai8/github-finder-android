/*
 * Abstract:
 * Roomで永続化するお気に入りレコードのエンティティ定義。
 */

package com.example.githubfinder.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.githubfinder.core.model.FavoriteEntity

@Entity(tableName = "favorites")
data class FavoriteDbEntity(
    @PrimaryKey val repositoryID: Int,
    val fullName: String,
    val description: String?,
    val starCount: Int,
    val language: String?,
    val ownerAvatarURL: String?,
    val savedAt: Long = System.currentTimeMillis(),
) {
    fun toEntity() = FavoriteEntity(
        id = repositoryID,
        fullName = fullName,
        description = description,
        starCount = starCount,
        language = language,
        ownerAvatarURL = ownerAvatarURL,
        savedAt = savedAt,
    )
}
