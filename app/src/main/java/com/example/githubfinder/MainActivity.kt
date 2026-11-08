/*
 * Abstract:
 * アプリケーションのメインActivity。Compose UIのエントリポイント。
 */

package com.example.githubfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.githubfinder.core.designsystem.GitHubFinderTheme
import com.example.githubfinder.core.designsystem.tutorial.CoachMarkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var coachMarkManager: CoachMarkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitHubFinderTheme {
                MainNavigation(coachMarkManager = coachMarkManager)
            }
        }
    }
}
