/*
 * Abstract:
 * ConnectivityManager のコールバックから [NetworkMonitor] を実装する。
 * 複数のネットワーク（Wi-Fi とモバイルなど）が同時に存在しうるため、状態は「1 つの接続の有無」ではなく
 * 「到達可能なネットワークの集合が空でないか」で判定する（片方が切れても、もう片方が生きていればオンライン）。
 */

package com.example.githubfinder.core.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
        // 到達可能（INTERNET かつ VALIDATED）なネットワークの集合。
        // 初期化はこのコルーチンのスレッド、コールバックは ConnectivityManager のスレッドで走るため、アクセスは synchronized で守る。
        val networks = mutableSetOf<Network>()

        /** 集合を更新し、更新後の「到達可能なネットワークが 1 つ以上あるか」を流す（更新と判定を 1 つの排他区間にする）。 */
        fun updateAndSend(mutate: MutableSet<Network>.() -> Unit) {
            val isOnline = synchronized(networks) {
                networks.mutate()
                networks.isNotEmpty()
            }
            trySend(isOnline)
        }

        // 購読直後に現在状態を流せるよう、コールバック登録前に activeNetwork から集合を初期化する
        connectivityManager?.activeNetwork?.let { active ->
            val capabilities = connectivityManager.getNetworkCapabilities(active)
            if (capabilities != null && capabilities.isReachable()) synchronized(networks) { networks.add(active) }
        }

        // onAvailable では集合に加えない。接続直後は VALIDATED 前のことがあり、
        // 加えるとキャプティブポータル等の「未検証ネットワーク」をオンラインと誤判定するため、onCapabilitiesChanged を待つ。
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                updateAndSend { remove(network) }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateAndSend { if (capabilities.isReachable()) add(network) else remove(network) }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(request, callback)
        trySend(synchronized(networks) { networks.isNotEmpty() })

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
