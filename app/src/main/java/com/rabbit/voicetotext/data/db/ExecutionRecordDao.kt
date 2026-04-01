package com.rabbit.voicetotext.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExecutionRecordDao {
    @Insert
    suspend fun insert(record: ExecutionRecord)

    @Query("SELECT * FROM execution_records ORDER BY timestamp DESC")
    suspend fun getAll(): List<ExecutionRecord>

    @Query("DELETE FROM execution_records WHERE id = :id")
    suspend fun deleteById(id: Int)
}
