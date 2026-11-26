/*
 * Abstract:
 * アイコン・タイトル・説明文で空状態を表示する共通コンポーネント。
 */

package com.example.githubfinder.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewLightDark
@Composable
private fun EmptyStateViewSearchPreview() {
    GitHubFinderTheme {
        Surface {
            Surface {
                EmptyStateView(
                    icon = AppIcons.Search,
                    title = "結果が見つかりません",
                    description = "別のキーワードで検索してみてください",
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun EmptyStateViewFavoritePreview() {
    GitHubFinderTheme {
        Surface {
            Surface {
                EmptyStateView(
                    icon = AppIcons.FavoriteBorder,
                    title = "お気に入りがありません",
                    description = "検索画面からリポジトリを\nお気に入りに追加しましょう",
                )
            }
        }
    }
}
