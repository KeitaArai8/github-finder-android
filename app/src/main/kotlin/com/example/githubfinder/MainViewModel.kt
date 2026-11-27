/*
 * Abstract:
 * アプリ最上位の状態を持つ ViewModel。現在はネットワーク接続状態（オフラインバナー用）だけを公開する。
 */

package com.example.githubfinder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.githubfinder.core.data.repository.NetworkStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    networkStatusRepository: NetworkStatusRepository,
) : ViewModel() {

    /** オンラインか。最初の判定が届くまでは true（起動直後にオフラインバナーを一瞬出さない）。 */
    val isOnline: StateFlow<Boolean> = networkStatusRepository.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}
