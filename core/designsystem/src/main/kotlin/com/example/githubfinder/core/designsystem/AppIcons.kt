package com.example.githubfinder.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

/**
 * アプリ共通アイコン集。Now in Android の `NiaIcons` と同じ方針で、
 * `androidx.compose.material:material-icons-extended`（更新終了）の代わりに
 * Material Symbols の Android Vector Drawable（res/drawable/ic_*.xml）を `ImageVector` として公開する。
 *
 * Icons: (c) Google, licensed under the Apache License 2.0.
 * Source: https://github.com/google/material-design-icons （symbols/android, materialsymbolsoutlined）
 *
 * `vectorResource` は Composable 内でのみ解決できるため、各アイコンは `@Composable get()` で提供する。
 * `OpenInNew` は XML 側の `android:autoMirrored="true"` で RTL 反転を維持している。
 */
object AppIcons {
    val Search: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_search)

    val Favorite: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_favorite)

    val FavoriteBorder: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_favorite_outline)

    val Star: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_star)

    val Delete: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_delete)

    val Settings: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_settings)

    val Share: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_share)

    val Warning: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_warning)

    val Info: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_info)

    val CheckCircle: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_check_circle)

    val Close: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_close)

    val Description: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_description)

    val PrivacyTip: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_privacy_tip)

    val DoneAll: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_done_all)

    val RemoveDone: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_remove_done)

    val WifiOff: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_wifi_off)

    val History: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_history)

    val FilterList: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_filter_list)

    val SwapVert: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_swap_vert)

    val RadioButtonUnchecked: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_radio_button_unchecked)

    val OpenInNew: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_open_in_new)
}
