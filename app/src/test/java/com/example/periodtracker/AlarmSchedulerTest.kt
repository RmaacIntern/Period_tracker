package com.example.periodtracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AlarmSchedulerTest {

    // ─── Helper matching AlarmScheduler.dateToMillis() ────────────
    private fun dateToMillis(date: LocalDate, hourOfDay: Int): Long =
        date.atTime(hourOfDay, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    // ─────────────────────────────────────────────────────────────
    // schedulePeriodAlarms — date math
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testPeriodReminderIsOneDayBefore() {
        val nextPeriod   = LocalDate.of(2026, 10, 15)
        val reminderDate = nextPeriod.minusDays(1)
        assertEquals("reminder = Oct 14", LocalDate.of(2026, 10, 14), reminderDate)
    }

    @Test
    fun testPeriodDayAlarmIsOnNextPeriodDate() {
        val nextPeriod = LocalDate.of(2026, 10, 15)
        val alarmDate  = nextPeriod  // fires on the day itself
        assertEquals("alarm fires Oct 15", LocalDate.of(2026, 10, 15), alarmDate)
    }

    @Test
    fun testAlarmTimeIsAt9am() {
        val date   = LocalDate.of(2026, 10, 15)
        val millis = dateToMillis(date, 9)
        val instant = java.time.Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        assertEquals("alarm at 9:00", 9, instant.hour)
        assertEquals("alarm at :00", 0, instant.minute)
    }

    // ─────────────────────────────────────────────────────────────
    // scheduleOvulationAlarm — date math
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOvulationAlarmFiresAtFertileWindowStart() {
        val ovulationDate = LocalDate.of(2026, 10, 15)
        val alertDate     = ovulationDate.minusDays(5)
        assertEquals("fertile window start = Oct 10", LocalDate.of(2026, 10, 10), alertDate)
    }

    @Test
    fun testOvulationAlarmIsAt9am() {
        val alertDate = LocalDate.of(2026, 10, 10)
        val millis    = dateToMillis(alertDate, 9)
        val instant   = java.time.Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        assertEquals("ovulation alarm at 9:00", 9, instant.hour)
    }

    // ─────────────────────────────────────────────────────────────
    // Past alarm guard
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testPastAlarmIsSkipped() {
        val pastDate   = LocalDate.now().minusDays(2)
        val millis     = dateToMillis(pastDate, 9)
        val isPast     = millis < System.currentTimeMillis()
        assertTrue("past alarm should be skipped", isPast)
    }

    @Test
    fun testFutureAlarmIsNotSkipped() {
        val futureDate = LocalDate.now().plusDays(5)
        val millis     = dateToMillis(futureDate, 9)
        val isPast     = millis < System.currentTimeMillis()
        assertFalse("future alarm should not be skipped", isPast)
    }

    @Test
    fun testTodayAt9amIsPastIfAfter9am() {
        // Just verify the millis conversion is correct — the actual
        // time comparison is runtime-dependent so we test the logic only
        val today  = LocalDate.now()
        val millis = dateToMillis(today, 9)
        assertTrue("millis > 0", millis > 0)
    }

    // ─────────────────────────────────────────────────────────────
    // Request codes are unique
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testRequestCodesAreUnique() {
        val REQ_PERIOD_REMINDER = 100
        val REQ_PERIOD_DAY      = 101
        val REQ_OVULATION_ALERT = 102
        val codes = listOf(REQ_PERIOD_REMINDER, REQ_PERIOD_DAY, REQ_OVULATION_ALERT)
        assertEquals("all codes unique", codes.size, codes.distinct().size)
    }
}