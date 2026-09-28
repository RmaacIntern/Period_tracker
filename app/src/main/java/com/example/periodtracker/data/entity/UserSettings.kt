package com.example.periodtracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,          // singleton row
    val userName: String,
    val age: Int,
    val heightCm: Int,
    val weightKg: Float,
    val activityLevel: String,            // "Gentle" | "Balanced" | "Very Active"
    val goal: String,                     // "Track My Cycle" | "Ovulation & Fertility"
    val conditions: String,               // comma-separated, "" if none
    val cycleLength: Int,
    val periodDuration: Int,
    val lastPeriodStart: String,          // ISO date "2026-09-03"
    val memberSince: String               // ISO date "2026-09-03"
)