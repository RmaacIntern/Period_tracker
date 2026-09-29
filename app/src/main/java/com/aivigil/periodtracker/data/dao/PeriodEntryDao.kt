package com.aivigil.periodtracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.aivigil.periodtracker.data.entity.PeriodEntry

@Dao
interface PeriodEntryDao {

    @Query("SELECT * FROM period_entries ORDER BY startDate DESC")
    fun observeAll(): LiveData<List<PeriodEntry>>

    @Query("SELECT * FROM period_entries ORDER BY startDate DESC")
    suspend fun getAll(): List<PeriodEntry>

    @Query("SELECT * FROM period_entries ORDER BY startDate DESC LIMIT 1")
    suspend fun getLatest(): PeriodEntry?

    @Query("SELECT * FROM period_entries ORDER BY startDate DESC LIMIT :n")
    suspend fun getRecent(n: Int): List<PeriodEntry>

    @Query("SELECT * FROM period_entries WHERE startDate = :date LIMIT 1")
    suspend fun getByDate(date: String): PeriodEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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