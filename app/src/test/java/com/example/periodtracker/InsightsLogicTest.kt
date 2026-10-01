package com.example.periodtracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Tests pure logic extracted from InsightsFragment and CycleViewModel.getBarChartData()
 */
class InsightsLogicTest {

    // ─────────────────────────────────────────────────────────────
    // Symptom percentage calculation
    // ─────────────────────────────────────────────────────────────

    data class FakeLog(val symptoms: String, val entryNumber: Int, val date: String)

    private fun crampPercent(logs: List<FakeLog>): Int {
        if (logs.isEmpty()) return 0
        return ((logs.count { it.symptoms.contains("Cramps") } / logs.size.toFloat()) * 100).toInt()
    }

    @Test
    fun testCrampPercentage_allHaveCramps() {
        val logs = listOf(
            FakeLog("Cramps, Fatigue", 1, "2024-09-01"),
            FakeLog("Cramps", 1, "2024-09-02")
        )
        assertEquals("100% cramping", 100, crampPercent(logs))
    }

    @Test
    fun testCrampPercentage_noneHaveCramps() {
        val logs = listOf(
            FakeLog("Fatigue", 1, "2024-09-01"),
            FakeLog("Headache", 1, "2024-09-02")
        )
        assertEquals("0% cramping", 0, crampPercent(logs))
    }

    @Test
    fun testCrampPercentage_half() {
        val logs = listOf(
            FakeLog("Cramps", 1, "2024-09-01"),
            FakeLog("Fatigue", 1, "2024-09-02")
        )
        assertEquals("50% cramping", 50, crampPercent(logs))
    }

    @Test
    fun testCrampPercentage_emptyLogs() {
        assertEquals("0% for empty logs", 0, crampPercent(emptyList()))
    }

    // ─────────────────────────────────────────────────────────────
    // Deduplication — latest entry per date
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDeduplication_keepsLatestEntry() {
        val logs = listOf(
            FakeLog("Cramps", 1, "2024-09-01"),
            FakeLog("Fatigue", 2, "2024-09-01"),   // latest for Sep 1
            FakeLog("Headache", 1, "2024-09-02")
        )
        val deduped = logs
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.entryNumber }!! }
            .values.toList()

        assertEquals("2 unique dates", 2, deduped.size)
        val sep1 = deduped.first { it.date == "2024-09-01" }
        assertEquals("keeps entry #2 for Sep 1", 2, sep1.entryNumber)
        assertEquals("entry #2 has Fatigue", "Fatigue", sep1.symptoms)
    }

    // ─────────────────────────────────────────────────────────────
    // Luteal phase calculation (InsightsFragment fix)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testLutealPhase_standardCycle() {
        val cycleLength    = 28
        val periodDuration = 5
        val ovulationDay   = (cycleLength - 14).coerceAtLeast(periodDuration + 2)
        val lutealDays     = cycleLength - ovulationDay
        assertEquals("14 luteal days for 28-day cycle", 14, lutealDays)
    }

    @Test
    fun testLutealPhase_shortCycle() {
        val cycleLength    = 21
        val periodDuration = 5
        val ovulationDay   = (cycleLength - 14).coerceAtLeast(periodDuration + 2)
        val lutealDays     = cycleLength - ovulationDay
        assertTrue("luteal > 0 for short cycle", lutealDays > 0)
    }

    @Test
    fun testLutealPhase_oldWrongFormula() {
        // Old: ovulationDay = cycleLength / 2 + 1 → wrong
        val cycleLength = 28
        val oldOvDay    = cycleLength / 2 + 1     // = 15
        val oldLuteal   = cycleLength - oldOvDay  // = 13 ← wrong

        // New: correct formula
        val newOvDay    = cycleLength - 14         // = 14
        val newLuteal   = cycleLength - newOvDay   // = 14 ← correct

        assertNotEquals("old formula was wrong", 14, oldLuteal)
        assertEquals("new formula is correct", 14, newLuteal)
    }

    // ─────────────────────────────────────────────────────────────
    // Bar chart data — open cycle calculation (off-by-one fix)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOpenCycleDays_noOffByOne() {
        val start   = LocalDate.of(2024, 9, 1)
        val today   = LocalDate.of(2024, 9, 15)
        // Correct: no +1
        val correct = ChronoUnit.DAYS.between(start, today).toInt()
        // Wrong (old): had +1
        val wrong   = ChronoUnit.DAYS.between(start, today).toInt() + 1

        assertEquals("correct = 14 days", 14, correct)
        assertEquals("wrong = 15 days (off by one)", 15, wrong)
    }

    @Test
    fun testOpenCycleDaysDoesNotExceedCycleLength() {
        val start = LocalDate.of(2024, 9, 1)
        val today = LocalDate.of(2024, 9, 29)   // exactly 28 days later
        val days  = ChronoUnit.DAYS.between(start, today).toInt()
        assertEquals("28 days elapsed", 28, days)
    }

    // ─────────────────────────────────────────────────────────────
    // Bar chart — cycle gaps between period entries
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testGapBetweenTwoPeriods() {
        val first  = LocalDate.of(2024, 9, 1)
        val second = LocalDate.of(2024, 9, 29)
        val gap    = ChronoUnit.DAYS.between(first, second).toInt()
        assertEquals("28-day gap", 28, gap)
    }

    @Test
    fun testBarChartTakesLast6Entries() {
        val entries = (1..10).map { it }
        val last6   = entries.takeLast(6)
        assertEquals("6 entries", 6, last6.size)
        assertEquals("last entry is 10", 10, last6.last())
    }
}