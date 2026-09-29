package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "authorized_credentials")
data class AuthorizedCredentialEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ssid: String,
    val credential: String,
    val securityType: String,
    val source: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)
