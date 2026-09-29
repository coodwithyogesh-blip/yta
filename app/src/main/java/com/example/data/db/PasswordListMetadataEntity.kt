package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "password_list_metadata")
data class PasswordListMetadataEntity(
    @PrimaryKey
    val id: Int = 1,
    val fileName: String,
    val uriString: String?,
    val totalLines: Long,
    val totalBatches: Long,
    val batchSize: Int = 500,
    val fileSizeBytes: Long = 0L,
    val importedTimestamp: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = true
)
