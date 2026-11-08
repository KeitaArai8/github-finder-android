/*
 * Abstract:
 * お気に入りリポジトリの一覧画面。
 * 通常モード: スワイプ削除 / 行タップで GitHub ページを開く
 * 編集モード: 複数選択 → 一括削除（確認ダイアログ付き）
 */

package com.example.githubfinder.feature.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import com.example.githubfinder.core.designsystem.AppIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.githubfinder.core.designsystem.EmptyStateView
import com.example.githubfinder.core.designsystem.ErrorView
import com.example.githubfinder.core.designsystem.RepositoryListItem
import com.example.githubfinder.core.designsystem.GitHubFinderTheme
import com.example.githubfinder.core.designsystem.R as DesignR
import com.example.githubfinder.core.designsystem.githubUrlFor
import com.example.githubfinder.core.designsystem.launchCustomTab
import com.example.githubfinder.core.designsystem.shareRepository
import com.example.githubfinder.core.model.FavoriteEntity
import com.example.githubfinder.core.model.RepositoryDisplayItem

/** お気に入り画面の入口。ViewModel から State を集め、ViewModel を知らない [FavoriteScreen] へ値とラムダだけを渡す。 */
@Composable
fun FavoriteRoute(
    modifier: Modifier = Modifier,
    viewModel: FavoriteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userMessages by viewModel.userMessages.collectAsStateWithLifecycle()
    FavoriteScreen(
        uiState = uiState,
        userMessages = userMessages,
        isDeletePending = viewModel::isDeletePending,
        onUserMessageShown = viewModel::userMessageShown,
        onUndoDelete = viewModel::undoDelete,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onRemoveFavorite = viewModel::removeFavorite,
        onSetFilterLanguage = viewModel::setFilterLanguage,
        onSetSortKey = viewModel::setSortKey,
        onEnterEditMode = viewModel::enterEditMode,
        onExitEditMode = viewModel::exitEditMode,
        onToggleSelection = viewModel::toggleSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onRequestRemoveSelected = viewModel::requestRemoveSelected,
        onConfirmRemoveSelected = viewModel::confirmRemoveSelected,
        onCancelRemoveSelected = viewModel::cancelRemoveSelected,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    uiState: FavoriteUiState,
    userMessages: List<UserMessage>,
    isDeletePending: (Int) -> Boolean,
    onUserMessageShown: (Long) -> Unit,
    onUndoDelete: (Int) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRemoveFavorite: (FavoriteEntity) -> Unit,
    onSetFilterLanguage: (String?) -> Unit,
    onSetSortKey: (FavoriteSortKey) -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onToggleSelection: (Int) -> Unit,
    onToggleSelectAll: () -> Unit,
    onRequestRemoveSelected: () -> Unit,
    onConfirmRemoveSelected: () -> Unit,
    onCancelRemoveSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val undoActionLabel = stringResource(R.string.favorite_undo_delete_action)

    // 通知キューの先頭を 1 件ずつ SnackBar に出し、表示し終えたら onUserMessageShown で消す。
    // 先頭の ID をキーにするので、表示中に後ろへ通知が積まれても前の SnackBar は途切れず、順番に表示される。
    // Undo 猶予後の実削除は ViewModel のタイマーが行うため、ここでは「元に戻す」だけを扱う。
    val currentMessage = userMessages.firstOrNull()
    // LaunchedEffect 内は Composable でないため、文字列化は外で済ませてから渡す
    val messageText = currentMessage?.text?.asString()
    LaunchedEffect(currentMessage?.id) {
        val message = currentMessage ?: return@LaunchedEffect
        if (messageText == null) return@LaunchedEffect
        // 既に実削除済みで Undo できない通知（回転直後の表示遅延など）は、SnackBar を出さずに消費だけする。
        if (message.undoID != null && !isDeletePending(message.undoID)) {
            onUserMessageShown(message.id)
            return@LaunchedEffect
        }
        if (message.undoID != null) {
            // Gmail スタイル: 「元に戻す」付き。duration を明示するのは、アクション付きの既定が Indefinite のため。
            val result = snackBarHostState.showSnackbar(
                message = messageText,
                actionLabel = undoActionLabel,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndoDelete(message.undoID)
        } else {
            snackBarHostState.showSnackbar(messageText)
        }
        onUserMessageShown(message.id)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FavoriteTopBar(
                state = uiState,
                onSortSelected = onSetSortKey,
                onEnterEditMode = onEnterEditMode,
                onExitEditMode = onExitEditMode,
                onToggleSelectAll = onToggleSelectAll,
                onRequestRemoveSelected = onRequestRemoveSelected,
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) },
    ) { padding ->
        when (val state = uiState) {
            is FavoriteUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.favorite_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            is FavoriteUiState.Loaded -> {
                val context = LocalContext.current
                val displayed = state.displayedFavorites
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.padding(padding),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 編集モード中はフィルタ操作を抑止（誤操作防止）
                        if (!state.isEditMode) {
                            LanguageFilterBar(
                                languages = state.availableLanguages,
                                selected = state.filterLanguage,
                                onSelect = onSetFilterLanguage,
                            )
                        }
                        if (displayed.isEmpty()) {
                            // フィルタ後 0 件 — DesignSystem の EmptyStateView で他の Empty と統一感を出す
                            EmptyStateView(
                                icon = AppIcons.FilterList,
                                title = stringResource(R.string.favorite_filter_no_match_title),
                                description = stringResource(R.string.favorite_filter_no_match_description),
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(displayed, key = { it.id }) { favorite ->
                                    FavoriteRow(
                                        favorite = favorite,
                                        isEditMode = state.isEditMode,
                                        isSelected = state.selectedIDs.contains(favorite.id),
                                        onDismiss = { onRemoveFavorite(favorite) },
                                        onRowTap = {
                                            if (state.isEditMode) {
                                                onToggleSelection(favorite.id)
                                            } else {
                                                launchCustomTab(context, githubUrlFor(favorite.fullName))
                                            }
                                        },
                                        onShare = {
                                            context.shareRepository(favorite.fullName)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.isDeleteConfirmationShown) {
                    DeleteConfirmationDialog(
                        count = state.selectedIDs.size,
                        onConfirm = onConfirmRemoveSelected,
                        onDismiss = onCancelRemoveSelected,
                    )
                }
            }
            is FavoriteUiState.Empty -> {
                EmptyStateView(
                    icon = AppIcons.FavoriteBorder,
                    title = stringResource(R.string.favorite_empty_title),
                    description = stringResource(R.string.favorite_empty_description),
                    modifier = Modifier.padding(padding),
                )
            }
            is FavoriteUiState.Error -> {
                ErrorView(
                    message = state.message.asString(),
                    onRetry = onRetry,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteTopBar(
    state: FavoriteUiState,
    onSortSelected: (FavoriteSortKey) -> Unit,
    onEnterEditMode: () -> Unit,
    onExitEditMode: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onRequestRemoveSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loaded = state as? FavoriteUiState.Loaded
    when {
        loaded?.isEditMode == true -> {
            TopAppBar(
                modifier = modifier,
                title = { Text(stringResource(R.string.favorite_selected_count, loaded.selectedIDs.size)) },
                navigationIcon = {
                    IconButton(onClick = onExitEditMode) {
                        Icon(
                            AppIcons.Close,
                            contentDescription = stringResource(R.string.favorite_exit_edit_mode),
                        )
                    }
                },
                actions = {
                    SelectAllToggleButton(
                        isAllSelected = loaded.isAllSelected,
                        onToggle = onToggleSelectAll,
                    )
                    IconButton(
                        onClick = onRequestRemoveSelected,
                        enabled = loaded.selectedIDs.isNotEmpty(),
                    ) {
                        Icon(
                            AppIcons.Delete,
                            contentDescription = stringResource(R.string.favorite_delete_action),
                            tint = if (loaded.selectedIDs.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    // iOS と同じ「完了」テキストボタン（Close icon と並列で提供し、ユーザに馴染みやすい選択肢を残す）
                    TextButton(onClick = onExitEditMode) {
                        Text(stringResource(R.string.favorite_done_edit))
                    }
                },
            )
        }
        else -> {
            TopAppBar(
                modifier = modifier,
                title = { Text(stringResource(R.string.favorite_tab_title)) },
                actions = {
                    if (loaded != null) {
                        SortMenuButton(
                            currentSortKey = loaded.sortKey,
                            onSortSelected = onSortSelected,
                        )
                        TextButton(onClick = onEnterEditMode) {
                            Text(stringResource(R.string.favorite_edit))
                        }
                    }
                },
            )
        }
    }
}

/**
 * 「全選択 ⇔ 全解除」トグル。状態に応じてアイコンと a11y ラベルが切り替わる。
 * 判定ロジック (isAllSelected) は ViewModel 側に集約済みで、ここはそれを描画するだけ。
 */
@Composable
private fun SelectAllToggleButton(
    isAllSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelRes = if (isAllSelected) R.string.favorite_deselect_all else R.string.favorite_select_all
    val icon = if (isAllSelected) AppIcons.RemoveDone else AppIcons.DoneAll
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
        )
    }
}

@Composable
private fun SortMenuButton(
    currentSortKey: FavoriteSortKey,
    onSortSelected: (FavoriteSortKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = AppIcons.SwapVert,
                contentDescription = stringResource(R.string.favorite_sort_a11y),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FavoriteSortKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(key.labelRes),
                            // 現在選択中の項目は太字表示で区別
                            style = if (key == currentSortKey) {
                                MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                        )
                    },
                    onClick = {
                        onSortSelected(key)
                        expanded = false
                    },
                    leadingIcon = if (key == currentSortKey) {
                        { Icon(AppIcons.CheckCircle, contentDescription = null) }
                    } else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageFilterBar(
    languages: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (languages.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // iOS と統一：FilterChip 自体の選択色変化のみで状態を表現し、leading icon は持たない
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.favorite_filter_all)) },
        )
        Spacer(modifier = Modifier.width(8.dp))
        languages.forEach { language ->
            FilterChip(
                // 同じ言語をタップで選択解除（トグル動作）
                selected = selected == language,
                onClick = { onSelect(if (selected == language) null else language) },
                label = { Text(language) },
                colors = FilterChipDefaults.filterChipColors(),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorite_delete_confirmation_title, count)) },
        text = { Text(stringResource(R.string.favorite_delete_confirmation_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.favorite_delete_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(DesignR.string.common_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteRow(
    favorite: FavoriteEntity,
    isEditMode: Boolean,
    isSelected: Boolean,
    onDismiss: () -> Unit,
    onRowTap: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 編集モード中はスワイプ削除・シェアを無効化する（誤操作防止）
    if (isEditMode) {
        RowContent(
            modifier = modifier,
            favorite = favorite,
            isEditMode = true,
            isSelected = isSelected,
            onRowTap = onRowTap,
            onShare = null,
        )
    } else {
        val dismissState = rememberSwipeToDismissBoxState()
        // 方向制限は enableDismissFromStartToEnd = false で行い、完全スワイプ（EndToStart 確定）を onDismiss で受けて削除する
        SwipeToDismissBox(
            modifier = modifier,
            state = dismissState,
            // ★ 段階的 UX: 左スワイプで RowContent がずれた分だけ赤背景 + ゴミ箱が露出する。
            //   完全スワイプで初めて削除実行されるため、ユーザは途中で離して取り消せる。
            //   ※ background は常にフル描画。手前の RowContent が opaque surface のため、
            //     スワイプしていない間は完全に隠れる（Material 3 標準パターン）。
            backgroundContent = { DismissBackground() },
            // 右方向スワイプ（StartToEnd）は機能割当なしのため無効化
            enableDismissFromStartToEnd = false,
            onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDismiss() },
        ) {
            RowContent(
                favorite = favorite,
                isEditMode = false,
                isSelected = false,
                onRowTap = onRowTap,
                onShare = onShare,
            )
        }
    }
}

/**
 * SwipeToDismissBox の背景に出る削除アクションのビジュアル。
 *
 * 仕組み:
 * - この background は常にフル不透明で配置される（赤背景 + 右端ゴミ箱）
 * - 手前の RowContent が不透明な surface 背景を持つため、スワイプしていない間は隠れる
 * - 左スワイプで RowContent が左にずれると、ずれた分だけ赤がめくれて見える ＝ Material 3 標準挙動
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = AppIcons.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .padding(end = 24.dp)
                .size(28.dp),
        )
    }
}

@Composable
private fun RowContent(
    favorite: FavoriteEntity,
    isEditMode: Boolean,
    isSelected: Boolean,
    onRowTap: () -> Unit,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    RepositoryListItem(
        item = favorite.toDisplayItem(),
        onClick = onRowTap,
        // ★ 必須: SwipeToDismissBox の backgroundContent（赤）が透けないよう surface 色で塗りつぶす。
        //   これがないと、スワイプしていなくても背景の赤が常に表示される。
        //   RepositoryListItem 内の .clickable より前に適用されるので、ripple は surface 上で正しく描画される。
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        onShare = onShare,
        compactActions = true,
        leadingContent = if (isEditMode) {
            {
                Icon(
                    imageVector = if (isSelected) AppIcons.CheckCircle else AppIcons.RadioButtonUnchecked,
                    contentDescription = stringResource(
                        if (isSelected) DesignR.string.a11y_selected else DesignR.string.a11y_unselected
                    ),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            null
        },
    )
}

/** お気に入り（常に登録済み）を共通行 UI の表示モデルへ変換する。 */
private fun FavoriteEntity.toDisplayItem() = RepositoryDisplayItem(
    id = id,
    fullName = fullName,
    description = description,
    starCount = starCount,
    language = language,
    ownerAvatarURL = ownerAvatarURL,
    isFavorite = true,
)

private fun previewFavorite(
    id: Int,
    fullName: String,
    starCount: Int,
    language: String? = "Kotlin",
): FavoriteEntity = FavoriteEntity(
    id = id,
    fullName = fullName,
    description = "Sample repository description",
    starCount = starCount,
    language = language,
    ownerAvatarURL = null,
    savedAt = 0L,
)

@Preview(name = "Row - 通常")
@Composable
private fun RowContentNormalPreview() {
    GitHubFinderTheme {
        RowContent(
            favorite = previewFavorite(1, "jetpackcompose/samples", 12_500),
            isEditMode = false,
            isSelected = false,
            onRowTap = {},
            onShare = {},
        )
    }
}

@Preview(name = "Row - 編集モード（未選択）")
@Composable
private fun RowContentEditModePreview() {
    GitHubFinderTheme {
        RowContent(
            favorite = previewFavorite(2, "android/architecture-samples", 44_000),
            isEditMode = true,
            isSelected = false,
            onRowTap = {},
            onShare = null,
        )
    }
}

@Preview(name = "Row - 編集モード（選択中）")
@Composable
private fun RowContentSelectedPreview() {
    GitHubFinderTheme {
        RowContent(
            favorite = previewFavorite(3, "android/nowinandroid", 17_500, language = "Kotlin"),
            isEditMode = true,
            isSelected = true,
            onRowTap = {},
            onShare = null,
        )
    }
}

@Preview(name = "LanguageFilterBar")
@Composable
private fun LanguageFilterBarPreview() {
    GitHubFinderTheme {
        LanguageFilterBar(
            languages = listOf("Kotlin", "Swift", "TypeScript", "Rust"),
            selected = "Kotlin",
            onSelect = {},
        )
    }
}

@Preview(name = "Delete Confirmation Dialog")
@Composable
private fun DeleteConfirmationDialogPreview() {
    GitHubFinderTheme {
        DeleteConfirmationDialog(count = 3, onConfirm = {}, onDismiss = {})
    }
}

