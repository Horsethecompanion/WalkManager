package com.horse.walkmanager

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BpmEntity::class], version = 1, exportSchema = false)
abstract class BpmDatabase : RoomDatabase() {
    abstract fun bpmDao(): BpmDao

    companion object {
        @Volatile
        private var INSTANCE: BpmDatabase? = null

        fun getDatabase(context: Context): BpmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BpmDatabase::class.java,
                    "bpm_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
