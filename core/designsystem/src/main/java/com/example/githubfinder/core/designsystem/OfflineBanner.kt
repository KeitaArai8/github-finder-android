/*
 * Abstract:
 * オフライン時にアプリ最上部に表示するバナー Composable。
 * NetworkMonitor.isConnected が false のとき MainNavigation から呼び出される。
 */

package com.example.githubfinder.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val bannerDescription = stringResource(R.string.offline_banner_a11y)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = bannerDescription },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AppIcons.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.offline_banner_label),
            color = MaterialTheme.colorScheme.onError,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Preview
@Composable
private fun OfflineBannerPreview() {
    GitHubFinderTheme {
        OfflineBanner()
    }
}
