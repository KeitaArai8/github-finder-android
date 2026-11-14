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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.githubfinder.core.designsystem.AppIcons
import com.example.githubfinder.core.designsystem.EmptyStateView
import com.example.githubfinder.core.designsystem.ErrorView
import com.example.githubfinder.core.designsystem.GitHubFinderTheme
import com.example.githubfinder.core.designsystem.R as DesignR
import com.example.githubfinder.core.designsystem.RepositoryListItem
import com.example.githubfinder.core.designsystem.UserMessage
import com.example.githubfinder.core.designsystem.githubUrlFor
import com.example.githubfinder.core.designsystem.launchCustomTab
import com.example.githubfinder.core.designsystem.shareRepository
import com.example.githubfinder.core.model.RepositoryDisplayItem

/** 検索画面の入口。ViewModel から State を集め、ViewModel を知らない [SearchScreen] へ値とラムダだけを渡す。 */
@Composable
fun SearchRoute(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessages by viewModel.userMessages.collectAsStateWithLifecycle()
    SearchScreen(
        uiState = uiState,
        userMessages = userMessages,
        onUserMessageShown = viewModel::userMessageShown,
        onQueryChange = viewModel::onQueryChanged,
        onSelectHistory = viewModel::selectHistory,
        onRemoveHistory = viewModel::removeHistory,
        onClearHistory = viewModel::clearHistory,
        onFavoriteTap = viewModel::toggleFavorite,
        onLoadMore = viewModel::onLoadMore,
        onRefresh = viewModel::onRefresh,
        modifier = modifier,
    )
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    userMessages: List<UserMessage>,
    onUserMessageShown: (Long) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onFavoriteTap: (RepositoryDisplayItem) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackBarHostState = remember { SnackbarHostState() }

    // 通知キューの先頭を 1 件ずつ SnackBar に出し、表示し終えたら onUserMessageShown で消す。
    // 先頭の ID をキーにするので、表示中に後ろへ通知が積まれても前の SnackBar は途切れず、順番に表示される。
    val currentMessage = userMessages.firstOrNull()
    // LaunchedEffect 内は Composable でないため、文字列化は外で済ませてから渡す
    val messageText = currentMessage?.text?.asString()
    LaunchedEffect(currentMessage?.id) {
        if (currentMessage != null && messageText != null) {
            snackBarHostState.showSnackbar(messageText)
            onUserMessageShown(currentMessage.id)
        }
    }

    // この画面は Scaffold を持たないため、自分で safeDrawing を適用する。
    // 親（NavigationSuiteScaffold・オフラインバナー）が消費済みの inset は 0 として扱われ、二重にはならない。
    Box(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TextField(
                value = uiState.searchQuery,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
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
                            onSelect = onSelectHistory,
                            onRemove = onRemoveHistory,
                            onClearAll = onClearHistory,
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
                        onFavoriteTap = onFavoriteTap,
                        onLoadMore = onLoadMore,
                        onRefresh = onRefresh,
                        onRowTap = { item ->
                            launchCustomTab(context, githubUrlFor(item.fullName))
                        },
                        onShare = { item ->
                            context.shareRepository(item.fullName)
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
                    ErrorView(message = state.message.asString(), onRetry = onRefresh)
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
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // snapshotFlow を張ったままの LaunchedEffect が古い items / onLoadMore を掴み続けないよう、最新値を経由して読む
    val currentItemCount by rememberUpdatedState(items.size)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= currentItemCount - 3) {
                    currentOnLoadMore()
                }
            }
    }

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                RepositoryListItem(
                    item = item,
                    onClick = { onRowTap(item) },
                    onShare = { onShare(item) },
                    onFavoriteToggle = { onFavoriteTap(item) },
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
private fun SearchHistoryList(
    history: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_history_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = onClearAll) {
                Text(
                    text = stringResource(R.string.search_history_clear_all),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history, key = { it }) { query ->
                val researchDescription = stringResource(R.string.search_history_research_a11y, query)
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
                            .semantics { contentDescription = researchDescription },
                    )
                    IconButton(
                        onClick = { onRemove(query) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = stringResource(R.string.search_history_remove_a11y, query),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SearchHistoryListPreview() {
    GitHubFinderTheme {
        Surface {
            SearchHistoryList(
                history = listOf("compose", "kotlin", "swift", "tca", "jetpack"),
                onSelect = {},
                onRemove = {},
                onClearAll = {},
            )
        }
    }
}
