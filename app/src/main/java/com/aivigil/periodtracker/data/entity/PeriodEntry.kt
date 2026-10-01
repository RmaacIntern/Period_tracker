package com.aivigil.periodtracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


/**
 * A confirmed period.
 *
 * FIX (data integrity): MIGRATION_5_6 recreated this table with UNIQUE on
 * startDate, but the entity never declared that index — so the constraint existed
 * only for users who had UPGRADED through v5. Anyone installing fresh got a table
 * built from this entity, with no uniqueness at all, and could accumulate
 * duplicate period entries for the same day (which corrupts cycle-length
 * averaging and the history chart). Declaring it here makes both paths identical.
 */
@Entity(
    tableName = "period_entries",
    indices = [Index(value = ["startDate"], unique = true)]
)
data class PeriodEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val startDate: String,
    val endDate: String?,
    val cycleLength: Int?,
    val flow: String = "",   // ✅ "Light" | "Medium" | "Heavy"
    val notes: String = ""
)