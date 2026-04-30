/*
 * Abstract:
 * お気に入りに保存されたリポジトリを表すドメインエンティティ。
 */

package com.example.githubfinder.core.model

data class FavoriteEntity(
    val id: Int,
    val fullName: String,
    val description: String?,
    val starCount: Int,
    val language: String?,
    val ownerAvatarURL: String?,
    val savedAt: Long,
)
