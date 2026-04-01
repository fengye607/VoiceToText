package com.rabbit.voicetotext.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ExecutionRecord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun executionRecordDao(): ExecutionRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voicetotext.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
