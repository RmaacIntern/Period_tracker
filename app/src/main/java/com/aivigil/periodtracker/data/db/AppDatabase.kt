package com.aivigil.periodtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aivigil.periodtracker.data.dao.DailyLogDao
import com.aivigil.periodtracker.data.dao.PeriodEntryDao
import com.aivigil.periodtracker.data.dao.UserSettingsDao
import com.aivigil.periodtracker.data.entity.DailyLog
import com.aivigil.periodtracker.data.entity.PeriodEntry
import com.aivigil.periodtracker.data.entity.UserSettings

@Database(
    entities = [UserSettings::class, PeriodEntry::class, DailyLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun periodEntryDao(): PeriodEntryDao
    abstract fun dailyLogDao(): DailyLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "periodtracker.db"
                )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}