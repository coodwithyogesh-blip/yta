package com.example.data.model

data class ImportedCredentialEntry(
    val positionInBatch: Int, // 1..500
    val globalLineNumber: Long, // 1..10,000,000
    val batchNumber: Long, // 1..20,000
    val credential: String
)
