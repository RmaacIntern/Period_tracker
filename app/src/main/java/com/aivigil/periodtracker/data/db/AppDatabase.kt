package com.aivigil.periodtracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aivigil.periodtracker.data.dao.DailyLogDao
import com.aivigil.periodtracker.data.dao.PeriodEntryDao
import com.aivigil.periodtracker.data.dao.UserSettingsDao
import com.aivigil.periodtracker.data.entity.DailyLog
import com.aivigil.periodtracker.data.entity.PeriodEntry
import com.aivigil.periodtracker.data.entity.UserSettings

@Database(
    entities = [UserSettings::class, PeriodEntry::class, DailyLog::class],
    version = 6,  // ✅ bumped
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun periodEntryDao(): PeriodEntryDao
    abstract fun dailyLogDao(): DailyLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1 → v2: Changed daily_logs PK from date to entryId + added entryNumber
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS daily_logs")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_logs (
                        entryId       INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date          TEXT    NOT NULL,
                        entryNumber   INTEGER NOT NULL DEFAULT 1,
                        flow          TEXT    NOT NULL DEFAULT '',
                        moods         TEXT    NOT NULL DEFAULT '',
                        symptoms      TEXT    NOT NULL DEFAULT '',
                        cervicalFluid TEXT    NOT NULL DEFAULT '',
                        basalTemp     REAL,
                        notes         TEXT    NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        // v2 → v3: Added loggedAt timestamp column
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_logs ADD COLUMN loggedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // v3 → v4: Added lhTestResult column
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_logs ADD COLUMN lhTestResult TEXT NOT NULL DEFAULT 'Not Tested'"
                )
            }
        }

        // ✅ v4 → v5: Added flow column to period_entries
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE period_entries ADD COLUMN flow TEXT NOT NULL DEFAULT ''"
                )
            }
        }
        // v5 → v6: unique index on startDate + clean existing duplicates
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Step 1: recreate table with UNIQUE constraint
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS period_entries_new (
                id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startDate   TEXT    NOT NULL UNIQUE,
                endDate     TEXT,
                cycleLength INTEGER,
                flow        TEXT    NOT NULL DEFAULT '',
                notes       TEXT    NOT NULL DEFAULT ''
            )
        """.trimIndent())

                // Step 2: insert only one row per startDate — keep the latest id
                // so if 2026-09-02 appears 3 times, only the LAST one survives
                db.execSQL("""
            INSERT OR IGNORE INTO period_entries_new 
                (id, startDate, endDate, cycleLength, flow, notes)
            SELECT id, startDate, endDate, cycleLength, flow, notes
            FROM period_entries
            WHERE id IN (
                SELECT MAX(id) FROM period_entries GROUP BY startDate
            )
            ORDER BY startDate ASC
        """.trimIndent())

                // Step 3: swap tables
                db.execSQL("DROP TABLE period_entries")
                db.execSQL("ALTER TABLE period_entries_new RENAME TO period_entries")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "periodtracker.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6  // ✅ added
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}