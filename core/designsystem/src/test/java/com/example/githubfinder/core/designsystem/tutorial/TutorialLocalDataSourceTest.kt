package com.example.githubfinder.core.designsystem.tutorial

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TutorialLocalDataSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.makeDataSource(
        file: File,
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    ): TutorialLocalDataSourceImpl {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return TutorialLocalDataSourceImpl(dataStore)
    }

    @Test
    fun `未記録のときisShownはfalse`() = runTest {
        // Arrange: 何も書き込まれていない DataStore
        val dataSource = makeDataSource(File(tempFolder.root, "tutorial.preferences_pb"))

        // Act: 現在値を読む
        val shown = dataSource.isShown.first()

        // Assert: 未表示扱い（初回起動でチュートリアルが出る）
        assertFalse(shown)
    }

    @Test
    fun `setShown後はisShownがtrueになる`() = runTest {
        // Arrange: 未記録の DataStore
        val dataSource = makeDataSource(File(tempFolder.root, "tutorial.preferences_pb"))

        // Act: 表示済みとして記録して読む
        dataSource.setShown()
        val shown = dataSource.isShown.first()

        // Assert: 表示済みとして読める
        assertTrue(shown)
    }

    @Test
    fun `同じファイルを開き直しても表示済みが保持される`() = runTest {
        // Arrange: 1 つ目のインスタンスで setShown 済みのファイル
        val file = File(tempFolder.root, "tutorial.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        makeDataSource(file, firstScope).setShown()

        // Act: 1 つ目の scope を閉じ（同一ファイルの DataStore は同時に 1 つだけ）、別インスタンスで開く（アプリ再起動の代わり）
        firstScope.cancel()
        val reopenedShown = makeDataSource(file).isShown.first()

        // Assert: 永続化されている
        assertTrue(reopenedShown)
    }
}
