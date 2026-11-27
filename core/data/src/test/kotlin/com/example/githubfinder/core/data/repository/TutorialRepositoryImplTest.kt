package com.example.githubfinder.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.example.githubfinder.core.datastore.TutorialLocalDataSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TutorialRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.makeDataSource(): TutorialLocalDataSource {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            produceFile = { File(tempFolder.root, "tutorial.preferences_pb") },
        )
        return TutorialLocalDataSource(dataStore)
    }

    @Test
    fun `isShownは未記録のデータソースの値をそのまま流す`() = runTest {
        // Arrange: 何も記録されていないデータソースを持つ Repository
        val repository = TutorialRepositoryImpl(makeDataSource())

        // Act: isShown を読む
        val shown = repository.isShown.first()

        // Assert: 未表示（false）が流れる
        assertFalse(shown)
    }

    @Test
    fun `setShownはデータソースへ届く`() = runTest {
        // Arrange: 未記録のデータソースを持つ Repository
        val dataSource = makeDataSource()
        val repository = TutorialRepositoryImpl(dataSource)

        // Act: Repository から表示済みにする
        repository.setShown()
        val dataSourceShown = dataSource.isShown.first()
        val repositoryShown = repository.isShown.first()

        // Assert: データソースにも Repository にも表示済み（true）として読める
        assertTrue(dataSourceShown)
        assertTrue(repositoryShown)
    }
}
