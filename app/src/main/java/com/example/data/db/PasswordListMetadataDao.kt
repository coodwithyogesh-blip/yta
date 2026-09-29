package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordListMetadataDao {
    @Query("SELECT * FROM password_list_metadata WHERE id = 1 LIMIT 1")
    fun getMetadataFlow(): Flow<PasswordListMetadataEntity?>

    @Query("SELECT * FROM password_list_metadata WHERE id = 1 LIMIT 1")
    suspend fun getMetadata(): PasswordListMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: PasswordListMetadataEntity)

    @Query("UPDATE password_list_metadata SET isAvailable = :isAvailable WHERE id = 1")
    suspend fun updateAvailability(isAvailable: Boolean)

    @Query("DELETE FROM password_list_metadata")
    suspend fun clear()
}
