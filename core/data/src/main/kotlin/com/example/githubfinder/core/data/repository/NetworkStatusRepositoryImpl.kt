/*
 * Abstract:
 * NetworkMonitor（ConnectivityManager ベースのデータソース）を使用するネットワーク接続状態リポジトリの実装。
 */

package com.example.githubfinder.core.data.repository

import com.example.githubfinder.core.data.util.NetworkMonitor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
internal class NetworkStatusRepositoryImpl @Inject constructor(
    networkMonitor: NetworkMonitor,
) : NetworkStatusRepository {

    override val isOnline: Flow<Boolean> = networkMonitor.isOnline
}
