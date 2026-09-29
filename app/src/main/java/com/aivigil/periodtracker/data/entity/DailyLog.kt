package com.aivigil.periodtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey(autoGenerate = true)
    val entryId: Int = 0,

    val date: String,
    val entryNumber: Int = 1,
    val loggedAt: Long = System.currentTimeMillis(),

    val flow: String = "",
    val moods: String = "",
    val symptoms: String = "",
    val cervicalFluid: String = "",
    val basalTemp: Float? = null,
    val lhTestResult: String = "Not Tested",   // "Not Tested" | "Negative" | "Positive"
    val notes: String = ""
)