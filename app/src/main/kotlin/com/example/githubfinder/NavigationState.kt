/*
 * Abstract:
 * タブごとの back stack と、選んだタブの履歴（top-level stack）をまとめて持つナビゲーション状態。
 * Now in Android の NavigationState / 公式レシピ「Multiple back stacks」と同じ形で、
 * タブを切り替えても各タブの ViewModel と保存可能な UI 状態（スクロール位置など）が残る。
 */

package com.example.githubfinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * @property startKey 開始タブのキー。アプリは常にこのタブから終了する。
 * @property topLevelStack 選んだタブの履歴。末尾が表示中のタブで、システム Back はここを 1 つ戻す。
 * @property tabStacks タブごとの back stack。タブを離れても stack は残るので、そのタブの状態も残る。
 */
internal class NavigationState(
    val startKey: NavKey,
    val topLevelStack: NavBackStack<NavKey>,
    val tabStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    val currentTopLevelKey: NavKey by derivedStateOf { topLevelStack.last() }
}

/** どの stack も `rememberNavBackStack` で持つので、画面回転とプロセス死後の復元で内容が戻る。 */
@Composable
internal fun rememberNavigationState(startKey: NavKey, topLevelKeys: Set<NavKey>): NavigationState {
    val topLevelStack = rememberNavBackStack(startKey)
    val tabStacks = topLevelKeys.associateWith { key -> rememberNavBackStack(key) }
    return remember(startKey, topLevelKeys) {
        NavigationState(startKey = startKey, topLevelStack = topLevelStack, tabStacks = tabStacks)
    }
}

/**
 * `NavDisplay` に渡す entry の並びを作る。
 * decorator（保存可能な UI 状態と ViewModelStore）は stack ごとに持つ。entry の状態が捨てられるのは
 * 自分の stack から取り除かれたときなので、[NavigationState.topLevelStack] からタブが外れても状態は残る。
 * 返すのは top-level stack に載っているタブの entry だけ（履歴の順）。
 */
@Composable
internal fun NavigationState.toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
    val decoratedEntries = tabStacks.mapValues { (_, stack) ->
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>(),
            ),
            entryProvider = entryProvider,
        )
    }
    return topLevelStack.flatMap { key -> decoratedEntries[key].orEmpty() }
}
