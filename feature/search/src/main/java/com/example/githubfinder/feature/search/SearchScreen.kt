/*
 * Abstract:
 * リポジトリ検索画面。キーワード入力、検索結果一覧、お気に入りトグルを提供。
 */

package com.example.githubfinder.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import android.content.Intent
import com.example.githubfinder.core.designsystem.AppIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.tooling.preview.Preview
import com.example.githubfinder.core.designsystem.EmptyStateView
import com.example.githubfinder.core.designsystem.ErrorView
import com.example.githubfinder.core.designsystem.GitHubFinderTheme
import com.example.githubfinder.core.designsystem.githubUrlFor
import com.example.githubfinder.core.designsystem.launchCustomTab
import com.example.githubfinder.core.model.RepositoryDisplayItem
import com.example.githubfinder.core.model.RepositoryEntity
import com.example.githubfinder.core.designsystem.R as DesignR
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackBarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refreshFavoriteIDs() }

    // 通知キューの先頭を 1 件ずつ SnackBar に出し、表示し終えたら userMessageShown で消す。
    LaunchedEffect(Unit) {
        viewModel.userMessages
            .map { it.firstOrNull() }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { message ->
                snackBarHostState.showSnackbar(message.text)
                viewModel.userMessageShown(message.id)
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onQueryChanged(it) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChanged("") }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(DesignR.string.a11y_clear))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    if (state.history.isEmpty()) {
                        EmptyStateView(
                            icon = AppIcons.Search,
                            title = stringResource(R.string.search_empty_idle_title),
                            description = stringResource(R.string.search_empty_idle_description),
                        )
                    } else {
                        SearchHistoryList(
                            history = state.history,
                            onSelect = viewModel::selectHistory,
                            onRemove = viewModel::removeHistory,
                            onClearAll = viewModel::clearHistory,
                        )
                    }
                }
                is SearchUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is SearchUiState.Loaded -> {
                    val context = LocalContext.current
                    RepositoryList(
                        items = state.items,
                        isLoadingMore = state.isLoadingMore,
                        isRefreshing = state.isRefreshing,
                        onFavoriteTap = viewModel::toggleFavorite,
                        onLoadMore = viewModel::onLoadMore,
                        onRefresh = viewModel::onRefresh,
                        onRowTap = { item ->
                            launchCustomTab(context, githubUrlFor(item.fullName))
                        },
                        onShare = { item ->
                            shareRepository(context, item.fullName)
                        },
                    )
                }
                is SearchUiState.Empty -> {
                    EmptyStateView(
                        icon = AppIcons.Search,
                        title = stringResource(R.string.search_empty_no_results_title),
                        description = stringResource(R.string.search_empty_no_results_description),
                    )
                }
                is SearchUiState.Error -> {
                    ErrorView(message = state.message, onRetry = viewModel::onRefresh)
                }
            }
        }

        SnackbarHost(
            hostState = snackBarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryList(
    items: List<RepositoryDisplayItem>,
    isLoadingMore: Boolean,
    isRefreshing: Boolean,
    onFavoriteTap: (RepositoryDisplayItem) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onRowTap: (RepositoryDisplayItem) -> Unit,
    onShare: (RepositoryDisplayItem) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= items.size - 3) {
                    onLoadMore()
                }
            }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                RepositoryRow(
                    item = item,
                    onFavoriteTap = { onFavoriteTap(item) },
                    onRowTap = { onRowTap(item) },
                    onShare = { onShare(item) },
                )
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RepositoryRow(
    item: RepositoryDisplayItem,
    onFavoriteTap: () -> Unit,
    onRowTap: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowTap)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.ownerAvatarURL,
            contentDescription = null,
            placeholder = ColorPainter(Color.Gray.copy(alpha = 0.3f)),
            modifier = Modifier.size(40.dp).clip(MaterialTheme.shapes.small),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            item.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Star, contentDescription = null, tint = Color(0xFFFFAB00), modifier = Modifier.size(14.dp))
                Text("${item.starCount}", style = MaterialTheme.typography.labelSmall)
                item.language?.let {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onShare) {
            Icon(
                imageVector = AppIcons.Share,
                contentDescription = "${item.fullName} をシェア",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onFavoriteTap) {
            Icon(
                imageVector = if (item.isFavorite) AppIcons.Favorite else AppIcons.FavoriteBorder,
                contentDescription = stringResource(
                    if (item.isFavorite) DesignR.string.a11y_favorite_remove
                    else DesignR.string.a11y_favorite_add
                ),
                tint = if (item.isFavorite) Color.Red else Color.Gray,
            )
        }
    }
}

@Composable
private fun SearchHistoryList(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近の検索",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = onClearAll) {
                Text(
                    text = "すべて消去",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history, key = { it }) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AppIcons.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "$query を再検索" },
                    )
                    IconButton(
                        onClick = { onRemove(query) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "$query を履歴から削除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** リポジトリの GitHub URL を Android 標準のシェアシートで共有する。 */
private fun shareRepository(context: android.content.Context, fullName: String) {
    val url = githubUrlFor(fullName)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        putExtra(Intent.EXTRA_SUBJECT, fullName)
    }
    context.startActivity(Intent.createChooser(sendIntent, "リポジトリをシェア"))
}

private fun previewRepositoryItem(
    id: Int,
    fullName: String,
    starCount: Int,
    language: String? = "Kotlin",
    isFavorite: Boolean = false,
): RepositoryDisplayItem = RepositoryDisplayItem.from(
    repository = RepositoryEntity(
        id = id,
        fullName = fullName,
        description = "Sample repository description",
        starCount = starCount,
        language = language,
        ownerAvatarURL = null,
    ),
    isFavorite = isFavorite,
)

@Preview(name = "Row - 通常")
@Composable
private fun RepositoryRowPreview() {
    GitHubFinderTheme {
        RepositoryRow(
            item = previewRepositoryItem(
                id = 1,
                fullName = "jetpackcompose/samples",
                starCount = 12_500,
            ),
            onFavoriteTap = {},
            onRowTap = {},
            onShare = {},
        )
    }
}

@Preview(name = "Row - お気に入り済")
@Composable
private fun RepositoryRowFavoritePreview() {
    GitHubFinderTheme {
        RepositoryRow(
            item = previewRepositoryItem(
                id = 2,
                fullName = "android/architecture-samples",
                starCount = 44_000,
                language = "Kotlin",
                isFavorite = true,
            ),
            onFavoriteTap = {},
            onRowTap = {},
            onShare = {},
        )
    }
}

@Preview(name = "Search History List")
@Composable
private fun SearchHistoryListPreview() {
    GitHubFinderTheme {
        SearchHistoryList(
            history = listOf("compose", "kotlin", "swift", "tca", "jetpack"),
            onSelect = {},
            onRemove = {},
            onClearAll = {},
        )
    }
}

