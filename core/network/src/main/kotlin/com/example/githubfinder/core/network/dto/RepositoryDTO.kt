/*
 * Abstract:
 * GitHub APIのリポジトリ情報をデシリアライズするためのDTO。
 */

package com.example.githubfinder.core.network.dto

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
)

@Serializable
data class OwnerDTO(
    @SerialName("avatar_url") val avatarUrl: String,
)
