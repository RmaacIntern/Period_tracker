package com.example.periodtracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.periodtracker.data.entity.DailyLog

@Dao
interface DailyLogDao {

    // All logs ordered newest first
    @Query("SELECT * FROM daily_logs ORDER BY date DESC, entryNumber ASC")
    fun observeAll(): LiveData<List<DailyLog>>

    // All entries for a specific date
    @Query("SELECT * FROM daily_logs WHERE date = :date ORDER BY entryNumber ASC")
    fun observeByDate(date: String): LiveData<List<DailyLog>>

    // All entries for a specific date (suspend)
    @Query("SELECT * FROM daily_logs WHERE date = :date ORDER BY entryNumber ASC")
    suspend fun getByDate(date: String): List<DailyLog>

    // First entry of today (oldest)
    @Query("SELECT * FROM daily_logs WHERE date = :date ORDER BY entryNumber ASC LIMIT 1")
    fun observeFirstByDate(date: String): LiveData<DailyLog?>

    // Most recent entry for today (newest loggedAt first — for Home summary display)
    @Query("SELECT * FROM daily_logs WHERE date = :date ORDER BY loggedAt DESC LIMIT 1")
    fun observeLatestByDate(date: String): LiveData<DailyLog?>

    // Count how many entries exist for a date
    @Query("SELECT COUNT(*) FROM daily_logs WHERE date = :date")
    suspend fun countByDate(date: String): Int

    // Recent logs (distinct days, last N days)
    @Query("SELECT * FROM daily_logs ORDER BY date DESC, entryNumber ASC LIMIT :n")
    suspend fun getRecent(n: Int): List<DailyLog>

    // Insert new entry
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DailyLog): Long

    // Delete a specific entry
    @Delete
    suspend fun delete(log: DailyLog)

    // Delete all entries for a date
    @Query("DELETE FROM daily_logs WHERE date = :date")
    suspend fun deleteByDate(date: String)

    // Delete all
    @Query("DELETE FROM daily_logs")
    suspend fun deleteAll()

    @Update
    suspend fun update(log: DailyLog)

    @Query("DELETE FROM daily_logs WHERE entryId = :id")
    suspend fun deleteById(id: Int)
}