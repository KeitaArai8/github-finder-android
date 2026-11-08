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
        // 現在のデフォルトネットワーク。onLost が「古いネットワークの喪失」か「現在のネットワークの喪失」かを区別するために持つ
        // （デフォルトの切り替え時は、新ネットワークの通知の後に旧ネットワークの onLost が届くことがあり、
        // 無条件に false を流すとオンラインのまま一瞬オフライン表示になるため）。
        var current: Network? = connectivityManager?.activeNetwork

        // 購読直後に現在状態を流す（機内モードなら activeNetwork が null で、即 false になる）。
        // コールバック登録より前に流すことで、登録直後に届く通知の値をこの初期値で上書きしないようにする
        trySend(current?.let { connectivityManager?.getNetworkCapabilities(it) }?.isReachable() == true)

        // onAvailable では流さない。接続直後は VALIDATED 前のことがあり、
        // 流すとキャプティブポータル等の「未検証ネットワーク」をオンラインと誤判定するため、onCapabilitiesChanged を待つ。
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                current = network
            }

            override fun onLost(network: Network) {
                if (network == current) trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                current = network
                trySend(capabilities.isReachable())
            }
        }

        // 登録に失敗しても（SecurityException や登録上限超過など）アプリを落とさず、オフライン扱いで継続する
        val registered = runCatching { connectivityManager?.registerDefaultNetworkCallback(callback) }
            .onFailure { e ->
                Timber.tag(TAG).w(e, "Failed to register default network callback")
                trySend(false)
            }
            .isSuccess

        awaitClose {
            if (registered) connectivityManager?.unregisterNetworkCallback(callback)
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
