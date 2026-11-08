/*
 * Abstract:
 * ConnectivityManager のコールバックから [NetworkMonitor] を実装する。
 * 「現在のデフォルトネットワークが INTERNET かつ VALIDATED か」だけを状態とする。
 * 複数ネットワーク（Wi-Fi とモバイル）の集合を自前で管理せず、デフォルトネットワークの選択は OS に任せる
 * （Wi-Fi が切れてモバイルへ切り替わると、新しいデフォルトネットワークの onCapabilitiesChanged で状態が更新される）。
 */

package com.example.githubfinder.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityManagerNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    override val isOnline: Flow<Boolean> = callbackFlow {
        // onAvailable では流さない。接続直後は VALIDATED 前のことがあり、
        // 流すとキャプティブポータル等の「未検証ネットワーク」をオンラインと誤判定するため、onCapabilitiesChanged を待つ。
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.isReachable())
            }
        }

        connectivityManager?.registerDefaultNetworkCallback(callback)

        // 購読直後に現在状態を流す（機内モードなら activeNetwork が null で、即 false になる）
        val active = connectivityManager?.activeNetwork
        trySend(active?.let { connectivityManager.getNetworkCapabilities(it) }?.isReachable() == true)

        awaitClose {
            connectivityManager?.unregisterNetworkCallback(callback)
        }
    }
        // conflate() は不要: 唯一の購読側（MainViewModel の stateIn）が StateFlow で既に conflate する
        .distinctUntilChanged()
        .onEach { online -> Timber.tag(TAG).i("Network state changed: online=%s", online) }

    private fun NetworkCapabilities.isReachable(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object {
        const val TAG = "Network"
    }
}
