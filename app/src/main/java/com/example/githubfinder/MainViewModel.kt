/*
 * Abstract:
 * アプリ最上位の状態を持つ ViewModel。現在はネットワーク接続状態（オフラインバナー用）だけを公開する。
 */

package com.example.githubfinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubfinder.core.data.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    /** オンラインか。最初の判定が届くまでは true（起動直後にオフラインバナーを一瞬出さない）。 */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
