package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DashboardDao {
    // RF Codes
    @Query("SELECT * FROM rf_codes ORDER BY timestamp DESC")
    fun getAllRfCodes(): Flow<List<RfCode>>

    @Query("SELECT * FROM rf_codes WHERE name LIKE :searchQuery OR protocol LIKE :searchQuery ORDER BY timestamp DESC")
    fun searchRfCodes(searchQuery: String): Flow<List<RfCode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRfCode(code: RfCode)

    @Update
    suspend fun updateRfCode(code: RfCode)

    @Delete
    suspend fun deleteRfCode(code: RfCode)

    @Query("DELETE FROM rf_codes WHERE id = :id")
    suspend fun deleteRfCodeById(id: Int)


    // LoRa Codes
    @Query("SELECT * FROM lora_codes ORDER BY timestamp DESC")
    fun getAllLoraCodes(): Flow<List<LoraCode>>

    @Query("SELECT * FROM lora_codes WHERE name LIKE :searchQuery OR payload LIKE :searchQuery ORDER BY timestamp DESC")
    fun searchLoraCodes(searchQuery: String): Flow<List<LoraCode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoraCode(code: LoraCode)

    @Update
    suspend fun updateLoraCode(code: LoraCode)

    @Delete
    suspend fun deleteLoraCode(code: LoraCode)

    @Query("DELETE FROM lora_codes WHERE id = :id")
    suspend fun deleteLoraCodeById(id: Int)


    // Connection Logs
    @Query("SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT 100")
    fun getConnectionLogs(): Flow<List<ConnectionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnectionLog(log: ConnectionLog)

    @Query("DELETE FROM connection_logs")
    suspend fun clearConnectionLogs()
}
