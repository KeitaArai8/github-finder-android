/*
 * Abstract:
 * 検索結果の表示用データクラス。Entityとお気に入り状態を統合しPassive Viewを実現。
 */

package com.example.githubfinder.core.model

data class RepositoryDisplayItem(
    val id: Int,
    val fullName: String,
    val description: String?,
    val starCount: Int,
    val language: String?,
    val ownerAvatarURL: String?,
    val isFavorite: Boolean,
) {
    companion object {
        fun from(repository: RepositoryEntity, isFavorite: Boolean) = RepositoryDisplayItem(
            id = repository.id,
            fullName = repository.fullName,
            description = repository.description,
            starCount = repository.starCount,
            language = repository.language,
            ownerAvatarURL = repository.ownerAvatarURL,
            isFavorite = isFavorite,
        )
    }
}
