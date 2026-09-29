package com.example.data.model

data class SuccessfulConnectionResult(
    val credential: String,
    val source: CredentialSource,
    val globalLineNumber: Long?,
    val totalLines: Long?,
    val batchNumber: Long?,
    val totalBatches: Long?,
    val positionInBatch: Int?,
    val batchSize: Int?,
    val ssid: String,
    val ipAddress: String,
    val gateway: String,
    val timestamp: Long = System.currentTimeMillis()
)
