/*
 * Abstract:
 * ネットワーク接続状態を供給するデータソースの契約。実装（ConnectivityManager など）を差し替えられるよう interface にしている。
 * :core:data の外には公開せず、利用側は NetworkStatusRepository を使う。
 */

package com.example.githubfinder.core.data.util

import kotlinx.coroutines.flow.Flow

internal interface NetworkMonitor {
    /** 現在のデフォルトネットワークが INTERNET を持ち、かつ VALIDATED（疎通検証済み）なら true。購読直後に現在値が流れる。 */
    val isOnline: Flow<Boolean>
}
