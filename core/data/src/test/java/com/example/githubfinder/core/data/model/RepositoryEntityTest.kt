package com.example.githubfinder.core.data.model

import com.example.githubfinder.core.model.RepositoryEntity
import com.example.githubfinder.core.network.dto.OwnerDTO
import com.example.githubfinder.core.network.dto.RepositoryDTO
import org.junit.Assert.assertEquals
import org.junit.Test

class RepositoryEntityTest {

    @Test
    fun `asExternalModelはDTOの各項目をドメインモデルへ対応づける`() {
        // Arrange: すべての項目を埋めた DTO
        val dto = RepositoryDTO(
            id = 7,
            fullName = "owner/repo",
            description = "説明",
            stargazersCount = 42,
            language = "Kotlin",
            owner = OwnerDTO(avatarUrl = "https://example.com/a.png"),
        )

        // Act: ドメインモデルへ変換する
        val entity = dto.asExternalModel()

        // Assert: stargazersCount は starCount、owner.avatarUrl は ownerAvatarURL に入り、他の項目はそのまま移る
        assertEquals(
            RepositoryEntity(
                id = 7,
                fullName = "owner/repo",
                description = "説明",
                starCount = 42,
                language = "Kotlin",
                ownerAvatarURL = "https://example.com/a.png",
            ),
            entity,
        )
    }

    @Test
    fun `asExternalModelは説明と言語がnullのDTOをnullのまま変換する`() {
        // Arrange: description と language が null の DTO
        val dto = RepositoryDTO(
            id = 1,
            fullName = "owner/repo",
            description = null,
            stargazersCount = 0,
            language = null,
            owner = OwnerDTO(avatarUrl = "https://example.com/a.png"),
        )

        // Act: ドメインモデルへ変換する
        val entity = dto.asExternalModel()

        // Assert: description と language は null のまま
        assertEquals(null, entity.description)
        assertEquals(null, entity.language)
    }
}
