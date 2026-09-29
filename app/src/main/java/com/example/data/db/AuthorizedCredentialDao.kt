package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthorizedCredentialDao {
    @Query("SELECT * FROM authorized_credentials ORDER BY addedTimestamp DESC")
    fun getAll(): Flow<List<AuthorizedCredentialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(credential: AuthorizedCredentialEntity): Long

    @Query("DELETE FROM authorized_credentials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM authorized_credentials")
    suspend fun clearAll()
}
