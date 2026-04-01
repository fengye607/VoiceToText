package com.rabbit.voicetotext.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_records")
data class ExecutionRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val functionId: String,
    val functionName: String,
    val inputText: String,
    val parsedContent: String,
    val result: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
