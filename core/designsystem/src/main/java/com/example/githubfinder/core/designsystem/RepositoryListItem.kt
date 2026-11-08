/*
 * Abstract:
 * 検索結果・お気に入り一覧で共有するリポジトリ 1 行の UI。
 * アバター / 名前 / 説明 / Star 数 / 言語 の本体は共通で、画面ごとに違う部分
 * （編集モードの選択アイコン・お気に入りトグルの有無・操作ボタンの大きさ）だけを引数と slot で受ける。
 *
 * 色はハードコードせずテーマ経由で参照する（ダークテーマ・Dynamic Color に追従させるため）:
 *  - Star / お気に入り済みのハート: [gitHubFinderColors]（色自体が意味を持つため。Dynamic Color で tertiary/error が
 *    壁紙由来の色に変わっても琥珀色・赤を保つ）
 *  - 未お気に入りのハート / アバターのプレースホルダ: colorScheme.onSurfaceVariant
 */

package com.example.githubfinder.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.githubfinder.core.model.RepositoryDisplayItem

/**
 * リポジトリ 1 行。
 *
 * @param item 表示内容。[RepositoryDisplayItem.isFavorite] はお気に入りトグルのアイコンにだけ使う。
 * @param onClick 行タップ。
 * @param onShare 非 null ならシェアボタンを出す。
 * @param onFavoriteToggle 非 null ならお気に入りトグルを出す（お気に入り一覧のように常に登録済みの画面は null）。
 * @param actionModifier シェアボタンに足す Modifier。お気に入り一覧は size(40.dp) で小さくする。
 * @param leadingContent 行頭の slot。編集モードの選択アイコンなど。アバターとの間隔（12dp）は呼び出し側が持たない。
 */
@Composable
fun RepositoryListItem(
    item: RepositoryDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(12.dp))
        }
        AsyncImage(
            model = item.ownerAvatarURL,
            contentDescription = null,
            placeholder = ColorPainter(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            item.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Star, contentDescription = null, tint = MaterialTheme.gitHubFinderColors.star, modifier = Modifier.size(14.dp))
                Text("${item.starCount}", style = MaterialTheme.typography.labelSmall)
                item.language?.let {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onShare != null) {
            IconButton(
                onClick = onShare,
                modifier = actionModifier,
            ) {
                Icon(
                    imageVector = AppIcons.Share,
                    contentDescription = stringResource(R.string.a11y_share_repository, item.fullName),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onFavoriteToggle != null) {
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (item.isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                    contentDescription = stringResource(
                        if (item.isFavorite) R.string.a11y_favorite_remove else R.string.a11y_favorite_add
                    ),
                    tint = if (item.isFavorite) MaterialTheme.gitHubFinderColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** [RepositoryListItem] のプレビュー用の状態: お気に入り済み / 未 / 長い名前・説明 / 言語なし。 */
private class RepositoryDisplayItemProvider : PreviewParameterProvider<RepositoryDisplayItem> {
    override val values = sequenceOf(
        RepositoryDisplayItem(
            id = 1,
            fullName = "jetpackcompose/samples",
            description = "Sample repository description",
            starCount = 12_500,
            language = "Kotlin",
            ownerAvatarURL = null,
            isFavorite = false,
        ),
        RepositoryDisplayItem(
            id = 2,
            fullName = "android/architecture-samples",
            description = "お気に入り済みの行（ハートが赤で塗られる）",
            starCount = 44_000,
            language = "Kotlin",
            ownerAvatarURL = null,
            isFavorite = true,
        ),
        RepositoryDisplayItem(
            id = 3,
            fullName = "a-very-long-organization-name/an-extremely-long-repository-name-that-overflows",
            description = "A deliberately long description that should wrap to two lines and then be truncated " +
                "so that the row height stays bounded no matter how much text the API returns.",
            starCount = 1_234_567,
            language = "TypeScript",
            ownerAvatarURL = null,
            isFavorite = true,
        ),
        RepositoryDisplayItem(
            id = 4,
            fullName = "octocat/no-description",
            description = null,
            starCount = 0,
            language = null,
            ownerAvatarURL = null,
            isFavorite = false,
        ),
    )
}

@PreviewLightDark
@Composable
private fun RepositoryListItemPreview(
    @PreviewParameter(RepositoryDisplayItemProvider::class) item: RepositoryDisplayItem,
) {
    GitHubFinderTheme {
        Surface {
            RepositoryListItem(
                item = item,
                onClick = {},
                onShare = {},
                onFavoriteToggle = {},
            )
        }
    }
}
