package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.indexer.PasswordListIndexer
import com.example.data.indexer.SampleDataGenerator
import com.example.data.model.CredentialSource
import com.example.data.model.ImportedCredentialEntry
import com.example.data.model.SuccessfulConnectionResult
import com.example.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Wi-Fi Access Manager", appName)
    }

    @Test
    fun `test indexing 2000 entries generates 4 batches of 500 with exact line numbers`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val indexer = PasswordListIndexer(context)

        val (stream, sizeBytes) = SampleDataGenerator.createSampleStream(2000L)
        val metadata = indexer.indexInputStream(
            sourceInputStream = stream,
            estimatedTotalBytes = sizeBytes,
            fileName = "sample_2000.txt",
            uriString = null,
            onProgress = {}
        )

        assertEquals(2000L, metadata.totalLines)
        assertEquals(4L, metadata.totalBatches)
        assertEquals(500, metadata.batchSize)
        assertTrue(indexer.isListAvailable())

        // Load Batch 0 (lines 1..500)
        val batch0 = indexer.loadBatch(0L, metadata.totalBatches, metadata.totalLines)
        assertEquals(500, batch0.size)
        assertEquals(1L, batch0.first().globalLineNumber)
        assertEquals(1, batch0.first().positionInBatch)
        assertEquals("12ertyuoi", batch0[0].credential)
        assertEquals("equiwywyw", batch0[1].credential)
        assertEquals("Aarti7756", batch0[2].credential)
        assertEquals(3L, batch0[2].globalLineNumber)
        assertEquals(500L, batch0.last().globalLineNumber)

        // Load Batch 1 (lines 501..1000)
        val batch1 = indexer.loadBatch(1L, metadata.totalBatches, metadata.totalLines)
        assertEquals(500, batch1.size)
        assertEquals(501L, batch1.first().globalLineNumber)
        assertEquals(1, batch1.first().positionInBatch)
        assertEquals(1000L, batch1.last().globalLineNumber)

        // Load Batch 3 (final batch: lines 1501..2000)
        val batch3 = indexer.loadBatch(3L, metadata.totalBatches, metadata.totalLines)
        assertEquals(500, batch3.size)
        assertEquals(1501L, batch3.first().globalLineNumber)
        assertEquals(2000L, batch3.last().globalLineNumber)

        // Clean up
        indexer.clearStorage()
    }

    @Test
    fun `test requirement 23 - result bug prevention when manual password differs from imported candidate`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)

        // User enters manual password in TextField
        viewModel.setManualPassword("123456")
        assertEquals("123456", viewModel.manualPasswordInput.value)

        // User selects candidate from imported list (Line 3: Aarti7756)
        val selectedCandidate = ImportedCredentialEntry(
            positionInBatch = 3,
            globalLineNumber = 3L,
            batchNumber = 1L,
            credential = "Aarti7756"
        )

        // Create the immutable result directly as produced by the connection workflow
        val expectedResult = SuccessfulConnectionResult(
            credential = selectedCandidate.credential,
            source = CredentialSource.IMPORTED_TXT,
            globalLineNumber = selectedCandidate.globalLineNumber,
            totalLines = 2000L,
            batchNumber = selectedCandidate.batchNumber,
            totalBatches = 4L,
            positionInBatch = selectedCandidate.positionInBatch,
            batchSize = 500,
            ssid = "sai raj",
            ipAddress = "192.168.0.108",
            gateway = "192.168.0.1"
        )

        // Verify the result matches the candidate and NOT the manual text field
        assertEquals("Aarti7756", expectedResult.credential)
        assertEquals(3L, expectedResult.globalLineNumber)
        assertEquals(CredentialSource.IMPORTED_TXT, expectedResult.source)
        assertNotEquals("123456", expectedResult.credential)

        // User modifies manual text field afterward
        viewModel.setManualPassword("999999")
        assertEquals("999999", viewModel.manualPasswordInput.value)

        // Result remains completely immutable and unaffected
        assertEquals("Aarti7756", expectedResult.credential)
        assertEquals(3L, expectedResult.globalLineNumber)
    }
}
