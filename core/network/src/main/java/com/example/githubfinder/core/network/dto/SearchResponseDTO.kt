/*
 * Abstract:
 * GitHub APIの検索レスポンスをデシリアライズするためのDTO。
 */

package com.example.githubfinder.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDTO(
    @SerialName("total_count") val totalCount: Int,
    val items: List<RepositoryDTO>,
)
