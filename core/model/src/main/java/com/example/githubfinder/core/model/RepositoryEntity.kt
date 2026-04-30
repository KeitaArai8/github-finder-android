/*
 * Abstract:
 * GitHubリポジトリを表すドメインエンティティ。
 */

package com.example.githubfinder.core.model

data class RepositoryEntity(
    val id: Int,
    val fullName: String,
    val description: String?,
    val starCount: Int,
    val language: String?,
    val ownerAvatarURL: String?,
)
