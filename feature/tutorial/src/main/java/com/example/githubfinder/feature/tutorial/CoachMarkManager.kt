/*
 * Abstract:
 * コーチマーク型チュートリアルのステップ管理と表示制御。
 * 表示済みフラグの永続化は :core:data の TutorialRepository に任せる。
 */

package com.example.githubfinder.feature.tutorial

import androidx.compose.ui.geometry.Rect
import com.example.githubfinder.core.common.network.di.ApplicationScope
import com.example.githubfinder.core.data.repository.TutorialRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class CoachMarkManager @Inject constructor(
    private val tutorialRepository: TutorialRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _currentStep = MutableStateFlow<CoachMarkStep?>(null)
    internal val currentStep: StateFlow<CoachMarkStep?> = _currentStep.asStateFlow()

    /**
     * チュートリアルを表示中か。[startIfNeeded] が 永続化済みフラグの答えを得るまでは null（未確定）。
     * 未確定の間に本画面を描画すると、初回起動でチュートリアルの前に一瞬ちらつくため、呼び出し側は null のとき何も描画しない。
     */
    private val _isActive = MutableStateFlow<Boolean?>(null)
    val isActive: StateFlow<Boolean?> = _isActive.asStateFlow()

    private val _targetRects = MutableStateFlow<Map<CoachMarkStep, Rect>>(emptyMap())
    internal val targetRects: StateFlow<Map<CoachMarkStep, Rect>> = _targetRects.asStateFlow()

    internal fun updateTargetRect(step: CoachMarkStep, rect: Rect) {
        _targetRects.update { current -> current + (step to rect) }
    }

    /** 表示済みフラグを永続化から読み、未表示ならチュートリアルを開始する。 */
    suspend fun startIfNeeded() {
        if (tutorialRepository.isShown.first()) {
            _isActive.value = false
            return
        }
        _isActive.value = true
        _currentStep.value = CoachMarkStep.SearchBar
    }

    internal fun next() {
        val current = _currentStep.value ?: return
        val steps = CoachMarkStep.entries
        val currentIndex = steps.indexOf(current)
        val nextIndex = currentIndex + 1

        if (nextIndex < steps.size) {
            _currentStep.value = steps[nextIndex]
        } else {
            complete()
        }
    }

    /**
     * 特定の操作（検索成功、お気に入り追加、タブ切替など）が完了したタイミングで
     * 次のステップへ進める。現在ステップが一致しないときは何もしない（冪等）。
     */
    internal fun advance(after: CoachMarkStep) {
        if (_isActive.value != true) return
        if (_currentStep.value != after) return
        next()
    }

    internal fun skip() {
        complete()
    }

    private fun complete() {
        _currentStep.value = null
        _isActive.value = false
        // UI 操作（next / skip）は同期のまま、永続化だけ非同期で行う
        scope.launch { tutorialRepository.setShown() }
    }
}
