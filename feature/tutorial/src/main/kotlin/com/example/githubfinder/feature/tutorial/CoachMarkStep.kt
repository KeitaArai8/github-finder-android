/*
 * Abstract:
 * チュートリアルの表示ステップ。
 */

package com.example.githubfinder.feature.tutorial

/**
 * チュートリアルの表示ステップ。表示文言は各 UI 層で stringResource 経由で解決する
 * （localization 対応のため、enum には文字列を持たせない）。
 */
internal enum class CoachMarkStep {
    SearchBar,
    FavoriteButton,
    FavoriteTab,
}
