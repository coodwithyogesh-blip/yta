package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.db.AppDatabase
import com.example.data.db.PasswordListMetadataEntity
import com.example.data.indexer.PasswordListIndexer
import com.example.data.indexer.SampleDataGenerator
import com.example.data.model.ImportedCredentialEntry
import com.example.data.model.IndexProgress
import com.example.data.model.PasswordListMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class PasswordListRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val metadataDao = db.passwordListMetadataDao()
    private val indexer = PasswordListIndexer(context)

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeImportJob: Job? = null

    private val _metadata = MutableStateFlow<PasswordListMetadata?>(null)
    val metadata: StateFlow<PasswordListMetadata?> = _metadata.asStateFlow()

    private val _indexProgress = MutableStateFlow(IndexProgress())
    val indexProgress: StateFlow<IndexProgress> = _indexProgress.asStateFlow()

    private val _currentBatchEntries = MutableStateFlow<List<ImportedCredentialEntry>>(emptyList())
    val currentBatchEntries: StateFlow<List<ImportedCredentialEntry>> = _currentBatchEntries.asStateFlow()

    private val _currentBatchIndex = MutableStateFlow(0L)
    val currentBatchIndex: StateFlow<Long> = _currentBatchIndex.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        repositoryScope.launch {
            loadPersistedMetadata()
        }
    }

    private suspend fun loadPersistedMetadata() = withContext(Dispatchers.IO) {
        val entity = metadataDao.getMetadata()
        if (entity != null) {
            val isAvailable = indexer.isListAvailable()
            val meta = PasswordListMetadata(
                fileName = entity.fileName,
                uriString = entity.uriString,
                totalLines = entity.totalLines,
                totalBatches = entity.totalBatches,
                batchSize = entity.batchSize,
                fileSizeBytes = entity.fileSizeBytes,
                importedTimestamp = entity.importedTimestamp,
                isAvailable = isAvailable
            )
            _metadata.value = meta
            if (isAvailable && meta.totalBatches > 0) {
                loadBatch(0L)
            } else if (!isAvailable) {
                _errorMessage.value = "Password list is no longer available. Please import it again."
            }
        }
    }

    fun startImportFromUri(uri: Uri, fileName: String, fileSize: Long) {
        if (activeImportJob?.isActive == true) return

        activeImportJob = repositoryScope.launch {
            try {
                _errorMessage.value = null
                _indexProgress.value = IndexProgress(
                    isBuilding = true,
                    statusMessage = "Opening file..."
                )

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open document stream for selected file.")

                val resultMetadata = indexer.indexInputStream(
                    sourceInputStream = inputStream,
                    estimatedTotalBytes = fileSize,
                    fileName = fileName,
                    uriString = uri.toString(),
                    onProgress = { progress ->
                        _indexProgress.value = progress
                    }
                )

                metadataDao.insertOrUpdate(
                    PasswordListMetadataEntity(
                        fileName = resultMetadata.fileName,
                        uriString = resultMetadata.uriString,
                        totalLines = resultMetadata.totalLines,
                        totalBatches = resultMetadata.totalBatches,
                        batchSize = resultMetadata.batchSize,
                        fileSizeBytes = resultMetadata.fileSizeBytes,
                        importedTimestamp = resultMetadata.importedTimestamp,
                        isAvailable = true
                    )
                )

                _metadata.value = resultMetadata
                loadBatch(0L)
            } catch (e: CancellationException) {
                _indexProgress.value = IndexProgress(
                    isBuilding = false,
                    statusMessage = "Cancelled"
                )
            } catch (e: Exception) {
                _indexProgress.value = IndexProgress(
                    isBuilding = false,
                    statusMessage = "Error"
                )
                _errorMessage.value = "Import failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                activeImportJob = null
            }
        }
    }

    fun startImportSample(count: Long) {
        if (activeImportJob?.isActive == true) return

        activeImportJob = repositoryScope.launch {
            try {
                _errorMessage.value = null
                _indexProgress.value = IndexProgress(
                    isBuilding = true,
                    statusMessage = "Generating sample list..."
                )

                val (stream, sizeBytes) = SampleDataGenerator.createSampleStream(count)
                val fileName = "sample_passwords_${count}.txt"

                val resultMetadata = indexer.indexInputStream(
                    sourceInputStream = stream,
                    estimatedTotalBytes = sizeBytes,
                    fileName = fileName,
                    uriString = null,
                    onProgress = { progress ->
                        _indexProgress.value = progress
                    }
                )

                metadataDao.insertOrUpdate(
                    PasswordListMetadataEntity(
                        fileName = resultMetadata.fileName,
                        uriString = null,
                        totalLines = resultMetadata.totalLines,
                        totalBatches = resultMetadata.totalBatches,
                        batchSize = resultMetadata.batchSize,
                        fileSizeBytes = resultMetadata.fileSizeBytes,
                        importedTimestamp = resultMetadata.importedTimestamp,
                        isAvailable = true
                    )
                )

                _metadata.value = resultMetadata
                loadBatch(0L)
            } catch (e: CancellationException) {
                _indexProgress.value = IndexProgress(
                    isBuilding = false,
                    statusMessage = "Cancelled"
                )
            } catch (e: Exception) {
                _indexProgress.value = IndexProgress(
                    isBuilding = false,
                    statusMessage = "Error"
                )
                _errorMessage.value = "Sample generation failed: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                activeImportJob = null
            }
        }
    }

    fun stopImport() {
        activeImportJob?.cancel()
        activeImportJob = null
        _indexProgress.value = IndexProgress(
            isBuilding = false,
            statusMessage = "Stopped"
        )
    }

    fun loadBatch(batchIndex: Long) {
        val meta = _metadata.value ?: return
        if (batchIndex < 0 || batchIndex >= meta.totalBatches) return

        repositoryScope.launch {
            // 1. Release old batch
            _currentBatchEntries.value = emptyList()
            // 2. Load requested batch from disk
            val entries = indexer.loadBatch(
                batchIndex = batchIndex,
                totalBatches = meta.totalBatches,
                totalLines = meta.totalLines
            )
            // 3. Update StateFlow
            _currentBatchIndex.value = batchIndex
            _currentBatchEntries.value = entries
        }
    }

    fun nextBatch() {
        val meta = _metadata.value ?: return
        val current = _currentBatchIndex.value
        if (current + 1 < meta.totalBatches) {
            loadBatch(current + 1)
        }
    }

    fun previousBatch() {
        val current = _currentBatchIndex.value
        if (current > 0) {
            loadBatch(current - 1)
        }
    }

    suspend fun clearStorage(): Boolean = withContext(Dispatchers.IO) {
        stopImport()
        val success = indexer.clearStorage()
        metadataDao.clear()
        _metadata.value = null
        _currentBatchEntries.value = emptyList()
        _currentBatchIndex.value = 0L
        _indexProgress.value = IndexProgress()
        _errorMessage.value = null
        success
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun getDataFileSize(): Long = indexer.getDataFileSize()
    fun getIndexFileSize(): Long = indexer.getIndexFileSize()
}
