/*
 * Abstract:
 * GitHub APIのリポジトリ情報をデシリアライズするためのDTO。
 */

package com.example.githubfinder.core.network.dto

import com.example.githubfinder.core.model.RepositoryEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RepositoryDTO(
    val id: Int,
    @SerialName("full_name") val fullName: String,
    val description: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int,
    val language: String? = null,
    val owner: OwnerDTO,
) {
    fun toEntity() = RepositoryEntity(
        id = id,
        fullName = fullName,
        description = description,
        starCount = stargazersCount,
        language = language,
        ownerAvatarURL = owner.avatarUrl,
    )
}

@Serializable
data class OwnerDTO(
    @SerialName("avatar_url") val avatarUrl: String,
)
