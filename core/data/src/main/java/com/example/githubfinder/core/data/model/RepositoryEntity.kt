/*
 * Abstract:
 * ネットワーク層の DTO をドメインモデルへ変換するマッピング。
 * 変換は取得側のデータ層が持ち、DTO（:core:network）はドメインモデルを参照しない。
 */

package com.example.githubfinder.core.data.model

import com.example.githubfinder.core.model.RepositoryEntity
import com.example.githubfinder.core.network.dto.RepositoryDTO

internal fun RepositoryDTO.asExternalModel() = RepositoryEntity(
    id = id,
    fullName = fullName,
    description = description,
    starCount = stargazersCount,
    language = language,
    ownerAvatarURL = owner.avatarUrl,
)
