package com.enerix.sentryscan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DomainHitEntity::class, ScanResultEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun domainHitDao(): DomainHitDao
    abstract fun scanResultDao(): ScanResultDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sentryscan.db" // local file only — never synced, never uploaded
                ).build().also { instance = it }
            }
    }
}
