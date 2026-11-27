/*
 * Abstract:
 * GitHubリポジトリを表すドメインモデル。
 */

package com.example.githubfinder.core.model

data class GitHubRepo(
    val id: Int,
    val fullName: String,
    val description: String?,
    val starCount: Int,
    val language: String?,
    val ownerAvatarURL: String?,
)
