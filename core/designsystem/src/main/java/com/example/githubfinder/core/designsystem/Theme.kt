/*
 * Abstract:
 * Material 3テーマ定義。Dynamic Color対応。
 */

package com.example.githubfinder.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** アプリ共通の Typography。現状は Material 3 の既定スケール（独自フォントを入れる時の差し込み口）。 */
private val AppTypography = Typography()

/**
 * アプリ共通の Shapes。値は Material 3 の既定トークンと同じで、画面側は角丸の dp を直書きせず
 * `MaterialTheme.shapes.*` を参照する（デザイン変更をここ 1 箇所に集約するため）。
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Material 3 のロールに存在しない「意味を持つ色」のホルダ（iOS DesignSystem の `Color.Brand.favorite` 相当）。
 * Dynamic Color は tertiary 等を壁紙由来の色（紫など）に置き換えるため、Star の琥珀色やお気に入りの赤のように
 * 色そのものが意味を持つものは colorScheme のロールに載せず、ここで固定値を持つ。
 *
 * @property star Star 数アイコンの色（琥珀色）。
 * @property favorite お気に入り済みハートの色（赤）。
 */
@Immutable
data class GitHubFinderColors(
    val star: Color,
    val favorite: Color,
)

private val LightGitHubFinderColors = GitHubFinderColors(
    star = Color(0xFFFFAB00),
    favorite = Color(0xFFD32F2F),
)

/** ダーク背景でも沈まないよう、明度を上げた変種。 */
private val DarkGitHubFinderColors = GitHubFinderColors(
    star = Color(0xFFFFC947),
    favorite = Color(0xFFFF6B6B),
)

private val LocalGitHubFinderColors = staticCompositionLocalOf { LightGitHubFinderColors }

/** [GitHubFinderTheme] が提供する意味色。`MaterialTheme.gitHubFinderColors.star` のように参照する。 */
val MaterialTheme.gitHubFinderColors: GitHubFinderColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGitHubFinderColors.current

@Composable
fun GitHubFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    CompositionLocalProvider(
        LocalGitHubFinderColors provides if (darkTheme) DarkGitHubFinderColors else LightGitHubFinderColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
