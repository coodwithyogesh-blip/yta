package com.example.data.model

data class PasswordListMetadata(
    val fileName: String,
    val uriString: String? = null,
    val totalLines: Long,
    val totalBatches: Long,
    val batchSize: Int = 500,
    val fileSizeBytes: Long = 0L,
    val importedTimestamp: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = true
)
