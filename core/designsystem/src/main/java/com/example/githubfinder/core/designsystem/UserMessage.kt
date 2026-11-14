/*
 * Abstract:
 * 画面で表示待ちの通知（SnackBar）。ViewModel の userMessages キューに積まれ、
 * View が表示し終えたら userMessageShown(id) で取り除かれる。
 */

package com.example.githubfinder.core.designsystem

/**
 * @property id 通知ごとの一意 ID。同じ文言が連続しても View が別の通知として扱えるようにするためだけに使う。
 * @property text 表示する文言（文字列リソース + 引数。文字列化は View が行う）。
 */
data class UserMessage(
    val id: Long,
    val text: UiText,
)
