package com.example.data.indexer

import android.content.Context
import android.net.Uri
import com.example.data.model.IndexProgress
import com.example.data.model.ImportedCredentialEntry
import com.example.data.model.PasswordListMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

class PasswordListIndexer(private val context: Context) {

    companion object {
        const val BATCH_SIZE = 500
        const val DATA_FILE_NAME = "imported_credentials.txt"
        const val INDEX_FILE_NAME = "batch_offsets.bin"
        const val BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer
    }

    private val dataFile: File
        get() = File(context.filesDir, DATA_FILE_NAME)

    private val indexFile: File
        get() = File(context.filesDir, INDEX_FILE_NAME)

    fun isListAvailable(): Boolean {
        return dataFile.exists() && indexFile.exists() && dataFile.length() > 0 && indexFile.length() > 0
    }

    fun getDataFileSize(): Long = if (dataFile.exists()) dataFile.length() else 0L
    fun getIndexFileSize(): Long = if (indexFile.exists()) indexFile.length() else 0L

    /**
     * Streams input from [sourceInputStream] to internal storage while building
     * a disk-based binary offset index. Memory usage is bounded by [BUFFER_SIZE].
     * Never loads the full file or all lines into memory.
     */
    suspend fun indexInputStream(
        sourceInputStream: InputStream,
        estimatedTotalBytes: Long,
        fileName: String,
        uriString: String?,
        onProgress: suspend (IndexProgress) -> Unit
    ): PasswordListMetadata = withContext(Dispatchers.IO) {
        val tempDestFile = File(context.filesDir, "${DATA_FILE_NAME}.tmp")
        val tempIndexFile = File(context.filesDir, "${INDEX_FILE_NAME}.tmp")

        if (tempDestFile.exists()) tempDestFile.delete()
        if (tempIndexFile.exists()) tempIndexFile.delete()

        var totalLines = 0L
        val startTime = System.currentTimeMillis()
        var lastEmitTime = startTime
        var lastEmitLines = 0L

        try {
            BufferedInputStream(sourceInputStream, BUFFER_SIZE).use { input ->
                BufferedOutputStream(FileOutputStream(tempDestFile), BUFFER_SIZE).use { output ->
                    DataOutputStream(BufferedOutputStream(FileOutputStream(tempIndexFile), 16 * 1024)).use { indexOutput ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var writtenByteOffset = 0L

                        // Batch 0 starts at offset 0
                        indexOutput.writeLong(0L)
                        var inBatchLineCount = 0

                        var hasPendingLine = false

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()

                            output.write(buffer, 0, bytesRead)

                            for (i in 0 until bytesRead) {
                                val b = buffer[i]
                                val currentBytePos = writtenByteOffset + i

                                if (b == '\n'.code.toByte()) {
                                    totalLines++
                                    inBatchLineCount++
                                    hasPendingLine = false

                                    if (inBatchLineCount == BATCH_SIZE) {
                                        // The NEXT batch starts at currentBytePos + 1
                                        val nextBatchOffset = currentBytePos + 1
                                        indexOutput.writeLong(nextBatchOffset)
                                        inBatchLineCount = 0
                                    }
                                } else if (b != '\r'.code.toByte()) {
                                    hasPendingLine = true
                                }
                            }

                            writtenByteOffset += bytesRead

                            // Throttled progress reporting (every 150ms)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= 150) {
                                val elapsedSec = (now - startTime) / 1000.0
                                val timeDelta = (now - lastEmitTime) / 1000.0
                                val linesDelta = totalLines - lastEmitLines
                                val speed = if (timeDelta > 0) (linesDelta / timeDelta).toLong() else 0L

                                val progressPercent = if (estimatedTotalBytes > 0) {
                                    (writtenByteOffset.toFloat() / estimatedTotalBytes.toFloat()).coerceIn(0f, 1f) * 100f
                                } else 0f

                                val estimatedTotalLines = if (progressPercent > 1f) {
                                    ((totalLines * 100f) / progressPercent).toLong()
                                } else 0L

                                val remaining = if (estimatedTotalLines > totalLines) estimatedTotalLines - totalLines else 0L
                                val eta = if (speed > 0 && remaining > 0) remaining / speed else 0L

                                onProgress(
                                    IndexProgress(
                                        processedLines = totalLines,
                                        totalEstimatedLines = estimatedTotalLines,
                                        progressPercent = progressPercent,
                                        remainingLines = remaining,
                                        speedEntriesPerSec = speed,
                                        etaSeconds = eta,
                                        isBuilding = true,
                                        statusMessage = "Indexing..."
                                    )
                                )

                                lastEmitTime = now
                                lastEmitLines = totalLines
                            }
                        }

                        // Handle trailing line without newline
                        if (hasPendingLine) {
                            totalLines++
                        }

                        indexOutput.flush()
                        output.flush()
                    }
                }
            }

            // Atomic rename of completed files
            if (dataFile.exists()) dataFile.delete()
            if (indexFile.exists()) indexFile.delete()

            if (!tempDestFile.renameTo(dataFile)) {
                tempDestFile.copyTo(dataFile, overwrite = true)
                tempDestFile.delete()
            }
            if (!tempIndexFile.renameTo(indexFile)) {
                tempIndexFile.copyTo(indexFile, overwrite = true)
                tempIndexFile.delete()
            }

            val totalBatches = if (totalLines == 0L) 0L else (totalLines + BATCH_SIZE - 1) / BATCH_SIZE

            onProgress(
                IndexProgress(
                    processedLines = totalLines,
                    totalEstimatedLines = totalLines,
                    progressPercent = 100f,
                    remainingLines = 0L,
                    speedEntriesPerSec = 0L,
                    etaSeconds = 0L,
                    isBuilding = false,
                    statusMessage = "Complete"
                )
            )

            PasswordListMetadata(
                fileName = fileName,
                uriString = uriString,
                totalLines = totalLines,
                totalBatches = totalBatches,
                batchSize = BATCH_SIZE,
                fileSizeBytes = dataFile.length(),
                importedTimestamp = System.currentTimeMillis(),
                isAvailable = true
            )
        } catch (e: Exception) {
            // Clean up temporary files on error or cancellation
            if (tempDestFile.exists()) tempDestFile.delete()
            if (tempIndexFile.exists()) tempIndexFile.delete()
            if (e is CancellationException) throw e
            throw e
        }
    }

    /**
     * Reads exactly the requested batch from disk using the offset index.
     * Bounded to at most [BATCH_SIZE] entries in memory.
     * [batchIndex] is 0-based (0 .. totalBatches - 1).
     */
    suspend fun loadBatch(
        batchIndex: Long,
        totalBatches: Long,
        totalLines: Long
    ): List<ImportedCredentialEntry> = withContext(Dispatchers.IO) {
        if (!isListAvailable()) return@withContext emptyList()
        if (batchIndex < 0 || batchIndex >= totalBatches) return@withContext emptyList()

        val batchOffset = readBatchOffset(batchIndex) ?: return@withContext emptyList()

        val entries = ArrayList<ImportedCredentialEntry>(BATCH_SIZE)

        RandomAccessFile(dataFile, "r").use { raf ->
            raf.seek(batchOffset)

            var count = 0
            var line: String?
            val baseGlobalLineNumber = batchIndex * BATCH_SIZE

            while (count < BATCH_SIZE) {
                line = raf.readLine() ?: break
                // Clean carriage return if present
                val cleaned = if (line.endsWith("\r")) line.substring(0, line.length - 1) else line

                count++
                val positionInBatch = count
                val globalLine = baseGlobalLineNumber + count

                entries.add(
                    ImportedCredentialEntry(
                        positionInBatch = positionInBatch,
                        globalLineNumber = globalLine,
                        batchNumber = batchIndex + 1,
                        credential = cleaned
                    )
                )

                if (globalLine >= totalLines) {
                    break
                }
            }
        }

        entries
    }

    /**
     * Reads the byte offset for [batchIndex] from the index file in O(1) time.
     */
    private fun readBatchOffset(batchIndex: Long): Long? {
        if (!indexFile.exists()) return null
        val offsetInIndex = batchIndex * 8L
        if (offsetInIndex + 8L > indexFile.length()) return null

        RandomAccessFile(indexFile, "r").use { raf ->
            raf.seek(offsetInIndex)
            return raf.readLong()
        }
    }

    /**
     * Deletes the stored data file, index file, and clears local caches.
     */
    suspend fun clearStorage(): Boolean = withContext(Dispatchers.IO) {
        var success = true
        if (dataFile.exists()) {
            success = dataFile.delete() && success
        }
        if (indexFile.exists()) {
            success = indexFile.delete() && success
        }
        val tmpData = File(context.filesDir, "${DATA_FILE_NAME}.tmp")
        if (tmpData.exists()) tmpData.delete()
        val tmpIndex = File(context.filesDir, "${INDEX_FILE_NAME}.tmp")
        if (tmpIndex.exists()) tmpIndex.delete()
        success
    }
}
