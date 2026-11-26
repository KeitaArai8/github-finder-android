package com.example.githubfinder.core.testing

import com.example.githubfinder.core.model.FavoriteEntity

/** テスト用の [FavoriteEntity] を作る。テストごとに関係する項目だけ指定し、残りは既定値にする。 */
fun testFavorite(
    id: Int = 1,
    fullName: String = "owner/repo",
    description: String? = null,
    starCount: Int = 0,
    language: String? = null,
    ownerAvatarURL: String? = null,
    savedAt: Long = 0L,
): FavoriteEntity = FavoriteEntity(
    id = id,
    fullName = fullName,
    description = description,
    starCount = starCount,
    language = language,
    ownerAvatarURL = ownerAvatarURL,
    savedAt = savedAt,
)
