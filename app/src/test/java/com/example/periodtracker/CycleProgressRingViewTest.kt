package com.example.periodtracker

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the phase boundary math from CycleProgressRingView
 * without needing the Android View or Canvas.
 */
class CycleProgressRingViewTest {

    // ─── Mirrors CycleProgressRingView private helpers ────────────
    private fun ovulationDay(cycleLength: Int, periodDuration: Int): Int =
        (cycleLength - 14).coerceAtLeast(periodDuration + 2)

    private fun fertileStart(cycleLength: Int, periodDuration: Int): Int =
        (ovulationDay(cycleLength, periodDuration) - 5).coerceAtLeast(periodDuration + 1)

    private fun fertileEnd(cycleLength: Int, periodDuration: Int): Int =
        ovulationDay(cycleLength, periodDuration)   // inclusive of ovulation day

    // ─────────────────────────────────────────────────────────────
    // Standard 28-day cycle
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOvulationDay_standardCycle() {
        assertEquals("28-day ovulation = day 14", 14, ovulationDay(28, 5))
    }

    @Test
    fun testFertileStart_standardCycle() {
        assertEquals("fertile start = day 9", 9, fertileStart(28, 5))
    }

    @Test
    fun testFertileEnd_standardCycle() {
        assertEquals("fertile end = ovulation day 14", 14, fertileEnd(28, 5))
    }

    @Test
    fun testFertileEndIncludesOvulationDay() {
        val ov  = ovulationDay(28, 5)
        val end = fertileEnd(28, 5)
        assertEquals("fertileEnd == ovulationDay", ov, end)
    }

    // ─────────────────────────────────────────────────────────────
    // Short cycle — clamp guards
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOvulationDay_shortCycle_clampedAfterPeriod() {
        // 19-day cycle, 5-day period
        // raw = 19-14 = 5 → but must be >= periodDuration+2 = 7
        val ov = ovulationDay(19, 5)
        assertTrue("ovulation after period ends (day > 5)", ov > 5)
        assertEquals("ovulation clamped to day 7", 7, ov)
    }

    @Test
    fun testFertileStart_shortCycle_clampedAfterPeriod() {
        // 19-day cycle, 5-day period
        // ov=7, raw fertile start = 7-5=2 → clamped to periodDuration+1=6
        val fs = fertileStart(19, 5)
        assertTrue("fertile start after period ends (day > 5)", fs > 5)
        assertEquals("fertile start clamped to day 6", 6, fs)
    }

    @Test
    fun testFertileStart_neverBeforeOvulation() {
        listOf(18, 21, 24, 28, 35).forEach { cycleLen ->
            val ov = ovulationDay(cycleLen, 5)
            val fs = fertileStart(cycleLen, 5)
            assertTrue("fertileStart <= ovulationDay for cycle=$cycleLen", fs <= ov)
        }
    }

    @Test
    fun testOvulationNeverInsidePeriod() {
        listOf(18, 19, 21, 24, 28, 35, 45).forEach { cycleLen ->
            val ov = ovulationDay(cycleLen, 5)
            assertTrue("ovulation day $ov > periodDuration 5 for cycle=$cycleLen", ov > 5)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Luteal phase = days after ovulation
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testLutealPhaseStartsAfterOvulation() {
        val ov          = ovulationDay(28, 5)
        val lutealStart = ov + 1
        assertEquals("luteal starts day 15", 15, lutealStart)
    }

    @Test
    fun testLutealPhaseCoversRemainingDays() {
        val cycleLength = 28
        val ov          = ovulationDay(cycleLength, 5)
        val lutealDays  = cycleLength - ov
        assertEquals("14 luteal days", 14, lutealDays)
    }

    // ─────────────────────────────────────────────────────────────
    // Off-by-one fix — "Next period in X days"
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDaysUntilNextPeriod_notOffByOne() {
        val cycleLength  = 28
        val selectedDay  = 20
        // Correct: cycleLength - selectedDay (NOT + 1)
        val daysUntil    = cycleLength - selectedDay
        assertEquals("8 days until period", 8, daysUntil)
    }

    @Test
    fun testDaysUntilNextPeriod_lastDay() {
        val cycleLength = 28
        val selectedDay = 28
        val daysUntil   = cycleLength - selectedDay
        assertEquals("0 days when on last day", 0, daysUntil)
    }

    // ─────────────────────────────────────────────────────────────
    // dayToAngle math
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDayToAngle_day1IsAtTop() {
        // day 1 → -90° (top of circle)
        val cycleLength = 28
        val angle = -90f + ((1f - 1f) / cycleLength.toFloat()) * 360f
        assertEquals(-90f, angle, 0.01f)
    }

    @Test
    fun testDayToAngle_halfway() {
        // day 15 of 28 → roughly 90° (right side)
        val cycleLength = 28
        val angle = -90f + ((15f - 1f) / cycleLength.toFloat()) * 360f
        assertEquals(90f, angle, 5f)
    }
}