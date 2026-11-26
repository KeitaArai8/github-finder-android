package com.example.githubfinder.core.designsystem.tutorial

import com.example.githubfinder.core.tutorial.TutorialLocalDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTutorialLocalDataSource(initialShown: Boolean) : TutorialLocalDataSource {
    private val shown = MutableStateFlow(initialShown)
    override val isShown: Flow<Boolean> = shown
    override suspend fun setShown() {
        shown.value = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoachMarkManagerTest {

    private fun TestScope.makeManager(dataSource: TutorialLocalDataSource) = CoachMarkManager(dataSource, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `startIfNeededがDataStoreの答えを得るまでisActiveは未確定のnullである`() = runTest {
        // Arrange: 読み取り完了を外から制御できるデータソース（DataStore の初回読み取り待ちを再現）
        val gate = CompletableDeferred<Boolean>()
        val dataSource = object : TutorialLocalDataSource {
            override val isShown: Flow<Boolean> = flow { emit(gate.await()) }
            override suspend fun setShown() = Unit
        }
        val manager = makeManager(dataSource)

        // Act: 開始判定を始めるが、読み取りは未完了のまま
        val job = launch { manager.startIfNeeded() }
        runCurrent()

        // Assert: 答えが出るまでは null（本画面もチュートリアルも描画しない）で、表示済みの答えが届くと false に確定する
        assertNull(manager.isActive.value)
        gate.complete(true)
        job.join()
        assertEquals(false, manager.isActive.value)
    }

    @Test
    fun `未表示ならstartIfNeededで最初のステップが有効になる`() = runTest {
        // Arrange: 表示済みフラグが false のデータソース
        val manager = makeManager(FakeTutorialLocalDataSource(initialShown = false))

        // Act: 開始判定
        manager.startIfNeeded()

        // Assert: SearchBar ステップでアクティブになる
        assertEquals(true, manager.isActive.value)
        assertEquals(CoachMarkStep.SearchBar, manager.currentStep.value)
    }

    @Test
    fun `表示済みならstartIfNeededは何もしない`() = runTest {
        // Arrange: 表示済みフラグが true のデータソース
        val manager = makeManager(FakeTutorialLocalDataSource(initialShown = true))

        // Act: 開始判定
        manager.startIfNeeded()

        // Assert: 非アクティブのまま
        assertEquals(false, manager.isActive.value)
        assertNull(manager.currentStep.value)
    }

    @Test
    fun `skipで完了し表示済みが永続化され次回は表示されない`() = runTest {
        // Arrange: 未表示で開始済みのマネージャ
        val dataSource = FakeTutorialLocalDataSource(initialShown = false)
        val manager = makeManager(dataSource)
        manager.startIfNeeded()

        // Act: スキップ後、新しいマネージャで再度開始判定する（再起動の代わり）
        manager.skip()
        val relaunched = makeManager(dataSource)
        relaunched.startIfNeeded()

        // Assert: 1 回目は非アクティブになり、再起動後は表示されない
        assertEquals(false, manager.isActive.value)
        assertEquals(false, relaunched.isActive.value)
    }

    @Test
    fun `最後のステップでnextすると完了する`() = runTest {
        // Arrange: 未表示で開始済み
        val dataSource = FakeTutorialLocalDataSource(initialShown = false)
        val manager = makeManager(dataSource)
        manager.startIfNeeded()

        // Act: ステップ数ぶん next
        repeat(CoachMarkStep.entries.size) { manager.next() }

        // Assert: 完了してステップが null、永続化済み
        assertNull(manager.currentStep.value)
        assertEquals(false, manager.isActive.value)
        assertTrue(dataSource.isShownNow())
    }
}

private suspend fun TutorialLocalDataSource.isShownNow(): Boolean = isShown.first()
