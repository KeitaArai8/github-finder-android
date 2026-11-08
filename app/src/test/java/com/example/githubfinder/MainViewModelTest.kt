package com.example.githubfinder

import com.example.githubfinder.core.testing.MainDispatcherRule
import com.example.githubfinder.core.data.network.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private class FakeNetworkMonitor : NetworkMonitor {
        val flow = MutableSharedFlow<Boolean>()
        override val isOnline: Flow<Boolean> = flow
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `最初の判定が届くまではオンライン扱いでバナーを出さない`() = runTest {
        // Arrange: まだ何も流れない NetworkMonitor と、購読者を 1 つ持つ MainViewModel
        val monitor = FakeNetworkMonitor()
        val viewModel = MainViewModel(monitor)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isOnline.collect {} }

        // Assert: 初期値は true（起動直後に OfflineBanner を一瞬出さない）
        assertTrue(viewModel.isOnline.value)
    }

    @Test
    fun `NetworkMonitorの値の変化がisOnlineに反映される`() = runTest {
        // Arrange: 購読者を持つ MainViewModel
        val monitor = FakeNetworkMonitor()
        val viewModel = MainViewModel(monitor)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.isOnline.collect {} }

        // Act: オフラインになり、その後オンラインに復帰する
        monitor.flow.emit(false)
        val afterOffline = viewModel.isOnline.value
        monitor.flow.emit(true)
        val afterOnline = viewModel.isOnline.value

        // Assert: false → true の順に追従する
        assertFalse(afterOffline)
        assertTrue(afterOnline)
    }
}
