package com.example.githubfinder.core.data.repository

import app.cash.turbine.test
import com.example.githubfinder.core.data.util.NetworkMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkStatusRepositoryImplTest {

    private class FakeNetworkMonitor : NetworkMonitor {
        val flow = MutableSharedFlow<Boolean>()
        override val isOnline: Flow<Boolean> = flow
    }

    @Test
    fun `isOnlineはNetworkMonitorの値をそのまま流す`() = runTest {
        // Arrange: 値を外から流せる NetworkMonitor を持つ Repository
        val monitor = FakeNetworkMonitor()
        val repository = NetworkStatusRepositoryImpl(monitor)

        // Act: isOnline を購読し、オフライン → オンラインの順に流す
        repository.isOnline.test {
            monitor.flow.emit(false)
            val offline = awaitItem()
            monitor.flow.emit(true)
            val online = awaitItem()

            // Assert: 流した値がそのままの順で届く
            assertEquals(false, offline)
            assertEquals(true, online)
        }
    }
}
