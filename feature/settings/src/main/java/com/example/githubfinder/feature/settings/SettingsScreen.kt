/*
 * Abstract:
 * アプリの設定画面。法的情報とバージョン情報を表示。
 */

package com.example.githubfinder.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.githubfinder.core.designsystem.GitHubFinderTheme
import com.example.githubfinder.core.designsystem.launchCustomTab

private const val PRIVACY_POLICY_URL = "https://www.apple.com/legal/privacy/"
private const val TERMS_URL = "https://www.apple.com/legal/internet-services/itunes/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_tab_title)) }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                stringResource(R.string.settings_section_legal),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingsRow(
                icon = Icons.Default.PrivacyTip,
                title = stringResource(R.string.settings_privacy_policy),
                onClick = { launchCustomTab(context, PRIVACY_POLICY_URL) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            SettingsRow(
                icon = Icons.Default.Description,
                title = stringResource(R.string.settings_terms_of_service),
                onClick = { launchCustomTab(context, TERMS_URL) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                stringResource(R.string.settings_section_app),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text(stringResource(R.string.settings_version), modifier = Modifier.padding(start = 16.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    getAppVersion(context),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Text(title, modifier = Modifier.padding(start = 16.dp).weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Suppress("DEPRECATION")
private fun getAppVersion(context: Context): String {
    return try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.versionCode})"
    } catch (e: PackageManager.NameNotFoundException) {
        "1.0"
    }
}

@Preview
@Composable
private fun SettingsRowPreview() {
    GitHubFinderTheme {
        SettingsRow(
            icon = Icons.Default.PrivacyTip,
            title = "プライバシーポリシー",
            onClick = {},
        )
    }
}
