package com.example.githubfinder.core.testing

import com.example.githubfinder.core.database.FavoriteDbEntity
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryEntity

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

/** テスト用の [RepositoryEntity] を作る。fullName の既定は `owner/repo<id>`。 */
fun testRepository(
    id: Int = 1,
    fullName: String = "owner/repo$id",
): RepositoryEntity = RepositoryEntity(
    id = id,
    fullName = fullName,
    description = null,
    starCount = 0,
    language = null,
    ownerAvatarURL = null,
)

/** テスト用の [FavoriteDbEntity] を作る。savedAt は指定しない限り実時刻になる。 */
fun testFavoriteDb(id: Int = 1): FavoriteDbEntity = FavoriteDbEntity(
    repositoryID = id,
    fullName = "owner/repo$id",
    description = null,
    starCount = 0,
    language = null,
    ownerAvatarURL = null,
)
