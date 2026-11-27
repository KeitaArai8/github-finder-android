/*
 * Abstract:
 * チュートリアル表示済みフラグのデータアクセス契約。
 */

package com.example.githubfinder.core.data.repository

import kotlinx.coroutines.flow.Flow

interface TutorialRepository {
    /** チュートリアルを最後まで（またはスキップして）表示済みなら true。未記録は false。 */
    val isShown: Flow<Boolean>

    /** 表示済みとして永続化する。 */
    suspend fun setShown()
}
