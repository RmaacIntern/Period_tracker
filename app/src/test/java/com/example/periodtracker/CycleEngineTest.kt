package com.example.periodtracker



import com.aivigil.periodtracker.domain.CycleEngine  // ✅ import the real class
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test                                 // ✅ FIX 2: JUnit imported
import java.time.LocalDate

// ✅ FIX 3: Log stub REMOVED — android.util.Log is available in test/ automatically

// ─── Helper ───────────────────────────────────────────────────────────────────
private fun date(str: String): LocalDate = LocalDate.parse(str)

// ─── Test class ───────────────────────────────────────────────────────────────
class CycleEngineTest {

    // ─────────────────────────────────────────────────────────────
    // A. CYCLE ENGINE
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testCycleDay() {
        val start = date("2024-09-01")
        assertEquals("Day 1 on start date", 1, CycleEngine.cycleDay(start, 28, start))
        assertEquals("Day 14 mid-cycle", 14, CycleEngine.cycleDay(start, 28, date("2024-09-14")))
        assertEquals("cycleDay clamped at 28 when 40 days elapsed", 28,
            CycleEngine.cycleDay(start, 28, date("2024-10-11")))
    }

    @Test
    fun testDaysLate() {
        // Use a date close to TODAY so it's not actually late
        val recentStart = LocalDate.now().minusDays(3)   // ✅ 3 days ago on a 28-day cycle = not late
        assertEquals("daysLate = 0 when not late", 0,
            CycleEngine.daysLate(recentStart, 28))
    }

    @Test
    fun testNextPeriodDate() {
        val start = date("2024-09-01")
        assertEquals("nextPeriodDate = start + 28",
            date("2024-09-29"), CycleEngine.nextPeriodDate(start, 28))
    }

    @Test
    fun testDaysUntilNextPeriod() {
        val start = date("2024-09-01")
        assertEquals("daysUntil = 0 when already past", 0,
            CycleEngine.daysUntilNextPeriod(start, 28, date("2024-10-10")))
        assertEquals("daysUntil = 14 at midpoint", 14,
            CycleEngine.daysUntilNextPeriod(start, 28, date("2024-09-15")))
    }

    // ─────────────────────────────────────────────────────────────
    // A. WEIGHTED AVERAGE
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testWeightedAverageCycleLength() {
        assertEquals("Empty list → 28", 28,
            CycleEngine.weightedAverageCycleLength(emptyList()))
        assertEquals("All-28 list → 28", 28,
            CycleEngine.weightedAverageCycleLength(listOf(28, 28, 28)))

        val withOutlier = CycleEngine.weightedAverageCycleLength(listOf(5, 28, 30))
        assertTrue("Outlier filtered; result in 28–30", withOutlier in 28..30)

        val weighted = CycleEngine.weightedAverageCycleLength(listOf(20, 35))
        assertTrue("Recent cycles weighted more → result > 28", weighted > 28)

        val clamped = CycleEngine.weightedAverageCycleLength(listOf(18, 60))
        assertTrue("Result always in 18–60", clamped in 18..60)
    }

    @Test
    fun testAverageCycleLength() {
        assertNull("Only 1 date → null",
            CycleEngine.averageCycleLength(listOf(date("2024-09-01"))))

        val dates = listOf(
            date("2024-07-01"), date("2024-07-29"),
            date("2024-08-26"), date("2024-09-23")
        )
        assertEquals("Regular 28-day cycles → 28", 28,
            CycleEngine.averageCycleLength(dates))
    }

    // ─────────────────────────────────────────────────────────────
    // A. REGULARITY
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testCycleRegularity() {
        assertEquals("< 3 dates → Insufficient data", "Insufficient data",
            CycleEngine.cycleRegularity(listOf(date("2024-09-01"), date("2024-09-29"))))

        val regularDates = listOf(
            date("2024-07-01"), date("2024-07-29"),
            date("2024-08-26"), date("2024-09-23")
        )
        assertEquals("Variance ≤ 2 → Regular", "Regular",
            CycleEngine.cycleRegularity(regularDates))

        val irregularDates = listOf(
            date("2024-01-01"), date("2024-01-29"),
            date("2024-03-05"), date("2024-04-24")
        )
        assertEquals("Variance > 5 → Irregular", "Irregular",
            CycleEngine.cycleRegularity(irregularDates))
    }

    // ─────────────────────────────────────────────────────────────
    // A. PHASE
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testPhase_standardCycle() {
        assertEquals("Day 1 = MENSTRUAL",    CycleEngine.Phase.MENSTRUAL,    CycleEngine.phase(1,  28, 5))
        assertEquals("Day 5 = MENSTRUAL",    CycleEngine.Phase.MENSTRUAL,    CycleEngine.phase(5,  28, 5))
        assertEquals("Day 6 = FOLLICULAR",   CycleEngine.Phase.FOLLICULAR,   CycleEngine.phase(6,  28, 5))
        assertEquals("Day 9 = OVULATION",    CycleEngine.Phase.OVULATION,    CycleEngine.phase(9,  28, 5))
        assertEquals("Day 14 = OVULATION",   CycleEngine.Phase.OVULATION,    CycleEngine.phase(14, 28, 5))
        assertEquals("Day 15 = LUTEAL_EARLY",CycleEngine.Phase.LUTEAL_EARLY, CycleEngine.phase(15, 28, 5))
        assertEquals("Day 18 = LUTEAL_LATE", CycleEngine.Phase.LUTEAL_LATE,  CycleEngine.phase(18, 28, 5))
        assertEquals("Day 28 = LUTEAL_LATE", CycleEngine.Phase.LUTEAL_LATE,  CycleEngine.phase(28, 28, 5))
    }

    @Test
    fun testPhase_shortCycle() {
        // 21-day cycle: ovDay = max(21-14=7, 5+2=7)=7; fertileStart = max(7-5=2, 5+1=6)=6
        assertEquals("Short cycle day 6 = OVULATION",
            CycleEngine.Phase.OVULATION, CycleEngine.phase(6, 21, 5))
    }

    // ─────────────────────────────────────────────────────────────
    // A. CALENDAR SETS
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testPeriodDays() {
        val start = date("2024-09-01")
        val days  = CycleEngine.periodDays(start, 5)
        assertEquals("periodDays has 5 entries", 5, days.size)
        assertTrue("starts on Sep 1",  date("2024-09-01") in days)
        assertTrue("ends on Sep 5",    date("2024-09-05") in days)
        assertFalse("excludes Sep 6",  date("2024-09-06") in days)
    }

    @Test
    fun testPredictedPeriodDays() {
        val days = CycleEngine.predictedPeriodDays(date("2024-09-01"), 28, 5)
        assertEquals("5 predicted days", 5, days.size)
        assertTrue("starts 28 days later", date("2024-09-29") in days)
    }

    @Test
    fun testFertileWindowDays() {
        val fStart = date("2024-09-10")
        val fEnd   = date("2024-09-15")
        val days   = CycleEngine.fertileWindowDays(fStart, fEnd)
        assertEquals("6 fertile days (inclusive)", 6, days.size)
        assertTrue("Sep 12 is fertile",  CycleEngine.isFertile(date("2024-09-12"), fStart, fEnd))
        assertFalse("Sep 16 not fertile", CycleEngine.isFertile(date("2024-09-16"), fStart, fEnd))
    }

    // ─────────────────────────────────────────────────────────────
    // ✅ NEW — futurePeriodDays
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testFuturePeriodDays() {
        val start = date("2024-09-01")
        // `today` must be injected. futurePeriodDays() now measures its cutoff from
        // TODAY rather than from lastPeriodStart (so the prediction window no longer
        // shrinks as a cycle progresses) and skips any period already in the past.
        // Without this the fixed 2024 start date yields an empty map.
        val future = CycleEngine.futurePeriodDays(start, 28, 5, monthsAhead = 3, today = start)

        assertTrue("≥ 3 periods in 3 months", future.size >= 3)
        assertTrue("first period at Sep 29",  date("2024-09-29") in future)
        assertTrue("each period has 5 days",  future.values.all { it.size == 5 })
        assertTrue("all starts after lastPeriodStart", future.keys.all { it.isAfter(start) })
        assertTrue("none exceeds 3-month cutoff",
            future.keys.all { !it.isAfter(start.plusMonths(3)) })

        val future21 = CycleEngine.futurePeriodDays(start, 21, 5, monthsAhead = 3, today = start)
        assertTrue("21-day cycle → more periods than 28-day", future21.size > future.size)
    }

    /** Regression test for the cutoff bug this fix addresses. */
    @Test
    fun futurePeriodDays_windowIsMeasuredFromToday_notFromLastPeriod() {
        val lastPeriod = date("2024-09-01")
        // Two months later: the old implementation capped the window at
        // lastPeriod + 3 months, leaving barely one predicted period. Measured from
        // today it must still return a full 3 months of predictions.
        val today = date("2024-11-01")
        val future = CycleEngine.futurePeriodDays(lastPeriod, 28, 5, monthsAhead = 3, today = today)

        assertTrue("still ≥ 3 predicted periods two months in", future.size >= 3)
        assertTrue("no past periods returned", future.keys.all { !it.isBefore(today) })
    }

    // ─────────────────────────────────────────────────────────────
    // Regression tests for the off-by-one late-period bug
    // ─────────────────────────────────────────────────────────────

    @Test
    fun periodIsNotLateOnTheDayItIsDue() {
        val start = date("2024-09-01")
        val due   = CycleEngine.nextPeriodDate(start, 28)   // 2024-09-29

        // Previously isPeriodLate() compared rawCycleDay (29 on the due date)
        // against cycleLength (28) and so reported "1 day late" on the exact day
        // the period was expected.
        assertFalse("not late the day before", CycleEngine.isPeriodLate(start, 28, due.minusDays(1)))
        assertFalse("not late ON the due date", CycleEngine.isPeriodLate(start, 28, due))
        assertTrue("late the day after",        CycleEngine.isPeriodLate(start, 28, due.plusDays(1)))

        assertEquals(0, CycleEngine.daysLate(start, 28, due))
        assertEquals(1, CycleEngine.daysLate(start, 28, due.plusDays(1)))
        assertEquals(3, CycleEngine.daysLate(start, 28, due.plusDays(3)))
    }

    @Test
    fun periodEndDateIsTheLastDayOfThePeriod() {
        val start = date("2024-09-01")
        // A 5-day period starting on the 1st ends on the 5th, matching
        // periodDays()'s `0 until periodDuration`. bestPrediction() previously used
        // start.plusDays(duration) — the first day AFTER the period — which pushed
        // the fertile-window clamp a day late.
        assertEquals(date("2024-09-05"), CycleEngine.periodEndDate(start, 5))
        assertEquals(CycleEngine.periodDays(start, 5).max(), CycleEngine.periodEndDate(start, 5))
    }

    @Test
    fun implausibleBioSignalIsRejected() {
        val start = date("2024-09-01")
        // A positive LH test mis-logged on day 2 of the period would otherwise place
        // ovulation inside the period and drag the fertile window before the cycle
        // even started.
        val badLh = CycleEngine.LhSignal(15, date("2024-09-02"))
        val pred = CycleEngine.bestPrediction(
            lastPeriodStart = start, avgCycleLength = 28, periodDuration = 5,
            lhSignal = badLh
        )
        assertTrue("ovulation must fall after the period ends",
            pred.ovulationDate.isAfter(CycleEngine.periodEndDate(start, 5)))
        assertEquals("rejected signal must not inflate confidence", "Formula", pred.dataSource)
    }

    // ─────────────────────────────────────────────────────────────
    // B. BBT
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDetectOvulationFromBBT_sustainedRise() {
        val logs = listOf(
            CycleEngine.BbtLog(date("2024-09-01"), 97.2f),
            CycleEngine.BbtLog(date("2024-09-02"), 97.1f),
            CycleEngine.BbtLog(date("2024-09-03"), 97.3f),
            CycleEngine.BbtLog(date("2024-09-04"), 97.2f),
            CycleEngine.BbtLog(date("2024-09-05"), 97.6f),
            CycleEngine.BbtLog(date("2024-09-06"), 97.7f)
        )
        val ov = CycleEngine.detectOvulationFromBBT(logs)
        assertNotNull("Sustained rise detected", ov)
        assertEquals("Ovulation = day before rise", date("2024-09-04"), ov)
    }

    @Test
    fun testDetectOvulationFromBBT_singleSpike() {
        val logs = listOf(
            CycleEngine.BbtLog(date("2024-09-01"), 97.2f),
            CycleEngine.BbtLog(date("2024-09-02"), 97.1f),
            CycleEngine.BbtLog(date("2024-09-03"), 97.3f),
            CycleEngine.BbtLog(date("2024-09-04"), 97.2f),
            CycleEngine.BbtLog(date("2024-09-05"), 97.6f),  // spike
            CycleEngine.BbtLog(date("2024-09-06"), 97.2f)   // drops back
        )
        assertNull("Single spike ignored", CycleEngine.detectOvulationFromBBT(logs))
    }

    @Test
    fun testDetectOvulationFromBBT_notEnoughData() {
        assertNull("< 5 logs → null",
            CycleEngine.detectOvulationFromBBT(
                listOf(CycleEngine.BbtLog(date("2024-09-01"), 97.2f))
            ))
    }

    // ─────────────────────────────────────────────────────────────
    // B. CERVICAL
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testCervicalFertilityScore_peakWithProgression() {
        val logs = listOf(
            date("2024-09-08") to "Dry",
            date("2024-09-09") to "Sticky",
            date("2024-09-10") to "Creamy",
            date("2024-09-11") to "Watery",
            date("2024-09-12") to "Egg White Fertile"
        )
        val signal = CycleEngine.cervicalFertilityScore(logs)
        assertEquals("Boost = 20 (peak=4, steps≥2)", 20, signal.confidenceBoost)
        assertEquals("Ovulation = day after peak",
            date("2024-09-13"), signal.estimatedOvulation)
    }

    @Test
    fun testCervicalFertilityScore_noProgression() {
        val logs = listOf(
            date("2024-09-08") to "Dry",
            date("2024-09-09") to "Dry",
            date("2024-09-10") to "Dry"
        )
        val signal = CycleEngine.cervicalFertilityScore(logs)
        assertEquals("Boost = 0 when flat", 0, signal.confidenceBoost)
        assertNull("No ovulation estimate when flat", signal.estimatedOvulation)
    }

    // ─────────────────────────────────────────────────────────────
    // B. LH
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testLhEvidence_positive() {
        val logs = listOf(
            CycleEngine.LhLog(date("2024-09-10"), "Negative"),
            CycleEngine.LhLog(date("2024-09-11"), "Positive"),
            CycleEngine.LhLog(date("2024-09-12"), "Positive")
        )
        val signal = CycleEngine.lhEvidence(logs)
        assertEquals("LH boost = 15", 15, signal.confidenceBoost)
        assertEquals("Ovulation = latest positive + 1",
            date("2024-09-13"), signal.estimatedOvulation)
    }

    @Test
    fun testLhEvidence_noPositive() {
        val signal = CycleEngine.lhEvidence(
            listOf(CycleEngine.LhLog(date("2024-09-10"), "Negative"))
        )
        assertEquals("No positive → boost 0", 0, signal.confidenceBoost)
        assertNull("No positive → null estimate", signal.estimatedOvulation)
    }

    // ─────────────────────────────────────────────────────────────
    // ✅ NEW — resolvedOvulationDate
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testResolvedOvulationDate_bbtWins() {
        val result = CycleEngine.resolvedOvulationDate(
            date("2024-09-14"),
            date("2024-09-13"),                           // BBT
            CycleEngine.LhSignal(15, date("2024-09-14")),
            CycleEngine.CervicalSignal(20, date("2024-09-15"))
        )
        assertEquals("BBT always wins", date("2024-09-13"), result)
    }

    @Test
    fun testResolvedOvulationDate_lhAndCervicalAgree() {
        val result = CycleEngine.resolvedOvulationDate(
            date("2024-09-14"), null,
            CycleEngine.LhSignal(15, date("2024-09-14")),
            CycleEngine.CervicalSignal(20, date("2024-09-15"))  // 1 day apart → agree
        )
        assertEquals("LH+cervical agree → averaged", date("2024-09-14"), result)
    }

    @Test
    fun testResolvedOvulationDate_lhAndCervicalDisagree() {
        val result = CycleEngine.resolvedOvulationDate(
            date("2024-09-14"), null,
            CycleEngine.LhSignal(15, date("2024-09-14")),
            CycleEngine.CervicalSignal(20, date("2024-09-20"))  // 6 days apart → disagree
        )
        assertEquals("LH wins when signals disagree", date("2024-09-14"), result)
    }

    @Test
    fun testResolvedOvulationDate_lhOnly() {
        val result = CycleEngine.resolvedOvulationDate(
            date("2024-09-14"), null,
            CycleEngine.LhSignal(15, date("2024-09-14")),
            CycleEngine.CervicalSignal(0, null)
        )
        assertEquals("LH only → LH date", date("2024-09-14"), result)
    }

    @Test
    fun testResolvedOvulationDate_cervicalOnly() {
        val result = CycleEngine.resolvedOvulationDate(
            date("2024-09-14"), null,
            CycleEngine.LhSignal(0, null),
            CycleEngine.CervicalSignal(20, date("2024-09-15"))
        )
        assertEquals("Cervical only → cervical date", date("2024-09-15"), result)
    }

    @Test
    fun testResolvedOvulationDate_formulaFallback() {
        val result = CycleEngine.resolvedOvulationDate(
            date("2024-09-14"), null,
            CycleEngine.LhSignal(0, null),
            CycleEngine.CervicalSignal(0, null)
        )
        assertEquals("No signals → formula", date("2024-09-14"), result)
    }

    // ─────────────────────────────────────────────────────────────
    // COMBINED — bestPrediction
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testBestPrediction_formulaOnly() {
        val p = CycleEngine.bestPrediction(date("2024-09-01"), 28)
        assertEquals("nextPeriod = Sep 29",  date("2024-09-29"), p.nextPeriodDate)
        assertEquals("ovulation = Sep 15",   date("2024-09-15"), p.ovulationDate)
        assertEquals("fertileStart = Sep 10",date("2024-09-10"), p.fertileStart)
        assertEquals("fertileEnd = Sep 16",  date("2024-09-16"), p.fertileEnd)
        assertEquals("confidence = 70",      70, p.confidence)
        assertEquals("source = Formula",     "Formula", p.dataSource)
    }

    @Test
    fun testBestPrediction_withBbt() {
        val p = CycleEngine.bestPrediction(
            date("2024-09-01"), 28,
            bbtOvulation = date("2024-09-13")
        )
        assertEquals("BBT confidence = 95",   95, p.confidence)
        assertEquals("ovulation = BBT date",  date("2024-09-13"), p.ovulationDate)
        assertEquals("source = BBT",          "BBT", p.dataSource)
    }

    @Test
    fun testBestPrediction_withLhOnly() {
        val p = CycleEngine.bestPrediction(
            date("2024-09-01"), 28,
            lhSignal = CycleEngine.LhSignal(15, date("2024-09-15"))
        )
        assertEquals("LH confidence = 80", 80, p.confidence)
        assertEquals("source = LH",        "LH", p.dataSource)
    }

    @Test
    fun testBestPrediction_shortCycleGuard() {
        val start = date("2024-09-01")
        val p = CycleEngine.bestPrediction(start, 21, periodDuration = 5)
        assertTrue("ovulation after period end", p.ovulationDate.isAfter(start.plusDays(5)))
        assertTrue("fertileStart after period end", p.fertileStart.isAfter(start.plusDays(5)))
    }

    // ─────────────────────────────────────────────────────────────
    // B. shouldStartNewCycle
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testShouldStartNewCycle() {
        assertTrue("Light starts cycle",  CycleEngine.shouldStartNewCycle("Light"))
        assertTrue("Medium starts cycle", CycleEngine.shouldStartNewCycle("Medium"))
        assertTrue("Heavy starts cycle",  CycleEngine.shouldStartNewCycle("Heavy"))
        assertFalse("Spotting does NOT",  CycleEngine.shouldStartNewCycle("Spotting"))
        assertFalse("None does NOT",      CycleEngine.shouldStartNewCycle("None"))
    }

    // ─────────────────────────────────────────────────────────────
    // C. SYMPTOM ENGINE
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDetectPmsOnsetDay() {
        val logs = listOf(
            Triple(date("2024-09-22"), "Cramps, Bloating", 22),
            Triple(date("2024-09-23"), "Irritable", 23),
            Triple(date("2024-09-10"), "Normal", 10)
        )
        assertEquals("PMS onset avg = 22", 22, CycleEngine.detectPmsOnsetDay(logs))
    }

    @Test
    fun testDetectPmsOnsetDay_noSymptoms() {
        assertNull("No PMS → null",
            CycleEngine.detectPmsOnsetDay(
                listOf(Triple(date("2024-09-10"), "Normal", 10))
            ))
    }
}