package com.aivigil.periodtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "period_entries")
data class PeriodEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val startDate: String,
    val endDate: String?,
    val cycleLength: Int?,
    val flow: String = "",   // ✅ "Light" | "Medium" | "Heavy"
    val notes: String = ""
)