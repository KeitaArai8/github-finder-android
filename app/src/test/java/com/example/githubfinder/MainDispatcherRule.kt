package com.example.githubfinder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * テスト中だけ `Dispatchers.Main` を [testDispatcher] に差し替える JUnit4 Rule。
 * ViewModel の `viewModelScope`（Main.immediate）をテストから制御するための共通ボイラープレート。
 *
 * ※ モジュールを増やさないため、このクラスは app / feature:search / feature:favorite の `src/test` に同一内容で置いている。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
