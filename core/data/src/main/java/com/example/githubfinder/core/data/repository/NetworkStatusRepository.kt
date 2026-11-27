/*
 * Abstract:
 * ネットワーク接続状態のデータアクセス契約。
 * 状態は「現在のデフォルトネットワークがインターネットに接続でき、疎通が検証済みか」の 1 値で表す。
 */

package com.example.githubfinder.core.data.repository

import kotlinx.coroutines.flow.Flow

interface NetworkStatusRepository {
    /** 現在のデフォルトネットワークが INTERNET を持ち、かつ VALIDATED（疎通検証済み）なら true。購読直後に現在値が流れる。 */
    val isOnline: Flow<Boolean>
}
