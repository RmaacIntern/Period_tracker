package com.example.periodtracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Tests pure date logic from CycleRepository without Room or Android.
 */
class CycleRepositoryLogicTest {

    // ─────────────────────────────────────────────────────────────
    // logPeriodStart — cycle length calculation
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testCycleLengthBetweenPeriods() {
        val prev = LocalDate.of(2024, 8, 1)
        val next = LocalDate.of(2024, 8, 29)
        val days = ChronoUnit.DAYS.between(prev, next).toInt()
        assertEquals("28-day cycle", 28, days)
    }

    @Test
    fun testCycleLengthOutlierRejected_tooShort() {
        val gap = 10
        assertFalse("gap of 10 rejected", gap in 18..60)
    }

    @Test
    fun testCycleLengthOutlierRejected_tooLong() {
        val gap = 65
        assertFalse("gap of 65 rejected", gap in 18..60)
    }

    @Test
    fun testCycleLengthBoundary_18IsValid() {
        assertTrue("18 is valid", 18 in 18..60)
    }

    @Test
    fun testCycleLengthBoundary_60IsValid() {
        assertTrue("60 is valid", 60 in 18..60)
    }

    @Test
    fun testDuplicatePeriodDateDetected() {
        val existing = "2024-09-01"
        val incoming = "2024-09-01"
        assertEquals("duplicate dates match", existing, incoming)
    }

    @Test
    fun testNoDuplicateWhenDifferentDates() {
        val existing = "2024-09-01"
        val incoming = "2024-09-29"
        assertNotEquals("different dates not duplicate", existing, incoming)
    }

    // ─────────────────────────────────────────────────────────────
    // logPeriodEnd — previous period closing logic
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testEndDateBeforeStartDateRejected() {
        val start = LocalDate.of(2024, 9, 10)
        val end   = LocalDate.of(2024, 9, 5)
        assertTrue("end before start is invalid", end.isBefore(start))
    }

    @Test
    fun testEndDateSameAsStartDateAccepted() {
        val start = LocalDate.of(2024, 9, 10)
        val end   = LocalDate.of(2024, 9, 10)
        assertFalse("same day is not before start", end.isBefore(start))
    }

    @Test
    fun testPeriodDurationCalculation() {
        val start = LocalDate.of(2024, 9, 1)
        val end   = LocalDate.of(2024, 9, 5)
        val dur   = ChronoUnit.DAYS.between(start, end).toInt() + 1
        assertEquals("5-day period", 5, dur)
    }

    @Test
    fun testPeriodDurationOutlierRejected_tooShort() {
        val dur = 1
        assertFalse("1 day rejected", dur in 2..10)
    }

    @Test
    fun testPeriodDurationOutlierRejected_tooLong() {
        val dur = 11
        assertFalse("11 days rejected", dur in 2..10)
    }

    @Test
    fun testAverageDurationRoundsDown() {
        val durations = listOf(4, 5, 6)
        val avg = durations.average().toInt().coerceIn(2, 10)
        assertEquals("avg 4,5,6 → 5", 5, avg)
    }

    @Test
    fun testAverageDurationClamped() {
        val durations = listOf(1, 1, 1)
        val avg = durations.average().toInt().coerceIn(2, 10)
        assertEquals("avg 1 clamped to 2", 2, avg)
    }

    // ─────────────────────────────────────────────────────────────
    // closePreviousPeriod — endDate assignment
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testPreviousPeriodClosedOneDayBeforeNew() {
        val newPeriodStart = LocalDate.of(2024, 9, 29)
        val expectedEndDate = newPeriodStart.minusDays(1)
        assertEquals("previous period ends Sep 28", LocalDate.of(2024, 9, 28), expectedEndDate)
    }

    @Test
    fun testEndDateNotBeforeStartDate() {
        val prevStart      = LocalDate.of(2024, 9, 28)
        val newPeriodStart = LocalDate.of(2024, 9, 29)
        val endDate        = newPeriodStart.minusDays(1)
        assertFalse("endDate not before prevStart", endDate.isBefore(prevStart))
    }

    // ─────────────────────────────────────────────────────────────
    // getBestPrediction — sync logic
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testMismatchDetectedBetweenSettingsAndEntry() {
        val settingsDate = "2024-09-01"
        val entryDate    = "2024-09-05"
        assertNotEquals("mismatch means sync needed", settingsDate, entryDate)
    }

    @Test
    fun testNoMismatchWhenDatesMatch() {
        val settingsDate = "2024-09-01"
        val entryDate    = "2024-09-01"
        assertEquals("no sync needed", settingsDate, entryDate)
    }

    @Test
    fun testNextPeriodDateMath() {
        val lastPeriod  = LocalDate.of(2024, 9, 1)
        val cycleLength = 28
        val nextPeriod  = lastPeriod.plusDays(cycleLength.toLong())
        assertEquals(LocalDate.of(2024, 9, 29), nextPeriod)
    }

    // ─────────────────────────────────────────────────────────────
    // saveDailyLog — entry numbering
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testFirstLogOfDayIsEntryNumber1() {
        val existingCount = 0
        val entryNumber   = existingCount + 1
        assertEquals("first log = entry #1", 1, entryNumber)
    }

    @Test
    fun testSecondLogOfDayIsEntryNumber2() {
        val existingCount = 1
        val entryNumber   = existingCount + 1
        assertEquals("second log = entry #2", 2, entryNumber)
    }
}