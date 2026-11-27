package com.example.githubfinder.core.testing

import com.example.githubfinder.core.database.model.FavoriteEntity
import com.example.githubfinder.core.model.Favorite
import com.example.githubfinder.core.model.GitHubRepo

/** テスト用の [Favorite] を作る。テストごとに関係する項目だけ指定し、残りは既定値にする。 */
fun testFavorite(
    id: Int = 1,
    fullName: String = "owner/repo",
    description: String? = null,
    starCount: Int = 0,
    language: String? = null,
    ownerAvatarURL: String? = null,
    savedAt: Long = 0L,
): Favorite = Favorite(
    id = id,
    fullName = fullName,
    description = description,
    starCount = starCount,
    language = language,
    ownerAvatarURL = ownerAvatarURL,
    savedAt = savedAt,
)

/** テスト用の [GitHubRepo] を作る。fullName の既定は `owner/repo<id>`。 */
fun testGitHubRepo(
    id: Int = 1,
    fullName: String = "owner/repo$id",
): GitHubRepo = GitHubRepo(
    id = id,
    fullName = fullName,
    description = null,
    starCount = 0,
    language = null,
    ownerAvatarURL = null,
)

/** テスト用の [FavoriteEntity] を作る。savedAt は指定しない限り実時刻になる。 */
fun testFavoriteEntity(id: Int = 1): FavoriteEntity = FavoriteEntity(
    repositoryID = id,
    fullName = "owner/repo$id",
    description = null,
    starCount = 0,
    language = null,
    ownerAvatarURL = null,
)
