/*
 * Abstract:
 * ネットワーク接続状態の契約。実装（ConnectivityManager など）を差し替えられるよう interface にしている。
 * UI 層では MainViewModel 経由で観察し、オフライン時に OfflineBanner を表示する。
 */

package com.example.githubfinder.core.data.network

import kotlinx.coroutines.flow.Flow

interface NetworkMonitor {
    /** インターネットへ到達できるネットワークが 1 つでもあれば true。購読直後に現在値が流れる。 */
    val isOnline: Flow<Boolean>
}
