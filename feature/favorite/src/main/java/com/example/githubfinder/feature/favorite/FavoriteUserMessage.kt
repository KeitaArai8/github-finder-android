/*
 * Abstract:
 * お気に入り画面で表示待ちの通知。共通の [UserMessage] に「元に戻す」対象 ID を添えたもの。
 * Undo は削除通知にしか無い概念なので、共有型には持たせずこの画面側で包む。
 */

package com.example.githubfinder.feature.favorite

import com.example.githubfinder.core.designsystem.UserMessage

/**
 * @property message 表示する通知本体。
 * @property undoID 非 null なら「元に戻す」アクション付きで、押されたら undoDelete(undoID) を呼ぶ対象の ID。
 */
data class FavoriteUserMessage(
    val message: UserMessage,
    val undoID: Int? = null,
)
