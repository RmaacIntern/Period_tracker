package com.aivigil.periodtracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.aivigil.periodtracker.data.entity.PeriodEntry

@Dao
interface PeriodEntryDao {

    @Query("SELECT * FROM period_entries ORDER BY startDate DESC")
    fun observeAll(): LiveData<List<PeriodEntry>>
    @Query("SELECT * FROM period_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): PeriodEntry?

    @Query("SELECT * FROM period_entries ORDER BY startDate DESC")
    suspend fun getAll(): List<PeriodEntry>

    @Query("SELECT * FROM period_entries ORDER BY startDate DESC LIMIT 1")
    suspend fun getLatest(): PeriodEntry?

    @Query("SELECT * FROM period_entries ORDER BY startDate DESC LIMIT :n")
    suspend fun getRecent(n: Int): List<PeriodEntry>

    @Query("SELECT * FROM period_entries WHERE startDate = :date LIMIT 1")
    suspend fun getByDate(date: String): PeriodEntry?

    /**
     * FIX (silent data loss): this was OnConflictStrategy.REPLACE. With the UNIQUE
     * index on startDate, REPLACE means DELETE-then-INSERT — so re-logging a start
     * date that already existed silently destroyed that row's endDate and
     * cycleLength and gave it a new id. Worse, CycleRepository.logPeriodStart()
     * wraps this in a try/catch that expects a constraint violation to signal a
     * duplicate; REPLACE never throws, so that guard could never fire.
     *
     * ABORT throws SQLiteConstraintException on a duplicate, which is what the
     * caller already handles by returning the existing entry.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: PeriodEntry): Long

    @Update
    suspend fun update(entry: PeriodEntry)

    @Delete
    suspend fun delete(entry: PeriodEntry)

    @Query("DELETE FROM period_entries WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM period_entries")
    suspend fun deleteAll()
}