/*
 * Abstract:
 * お気に入り画面で表示待ちの通知（SnackBar）。ViewModel の userMessages キューに積まれ、
 * View が表示し終えたら userMessageShown(id) で取り除かれる。
 */

package com.example.githubfinder.feature.favorite

/**
 * @property id 通知ごとの一意 ID。同じ文言が連続しても View が別の通知として扱えるようにするためだけに使う。
 * @property text 表示する文言。
 * @property undoID 非 null なら「元に戻す」アクション付きで、押されたら undoDelete(undoID) を呼ぶ対象の ID。
 */
data class UserMessage(
    val id: Long,
    val text: String,
    val undoID: Int? = null,
)
