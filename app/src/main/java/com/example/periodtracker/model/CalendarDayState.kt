package com.example.periodtracker.model

import java.time.LocalDate

enum class DayType {
    NONE, PERIOD, PERIOD_ESTIMATED, FERTILE, FERTILE_LIGHT
}

data class CalendarDayState(
    val date: LocalDate,
    val type: DayType = DayType.NONE,
    val isToday: Boolean = false
)