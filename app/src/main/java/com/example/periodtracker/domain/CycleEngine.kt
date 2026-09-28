package com.example.periodtracker.domain

import android.util.Log
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * All cycle math — pure functions, no Android deps, fully unit-testable.
 *
 * Architecture:
 *   A. Cycle engine   – period dates, cycle day, history
 *   B. Ovulation engine – BBT (sustained shift), cervical progression, LH surge
 *   C. Symptom engine – PMS detection; NEVER modifies period/ovulation dates
 */
object CycleEngine {

    private const val TAG = "CycleEngine"

    // ─────────────────────────────────────────────────────────────
    // A. CYCLE ENGINE
    // ─────────────────────────────────────────────────────────────

    /**
     * Raw elapsed days since last period — NOT clamped.
     * Used internally for late detection only.
     */
    private fun rawCycleDay(
        lastPeriodStart: LocalDate,
        today: LocalDate = LocalDate.now()
    ): Int = ChronoUnit.DAYS.between(lastPeriodStart, today).toInt() + 1

    /**
     * 1-based cycle day clamped to [1, cycleLength].
     * Used for ring display and phase calculation — never exceeds cycle length.
     */
    fun cycleDay(
        lastPeriodStart: LocalDate,
        cycleLength: Int,
        today: LocalDate = LocalDate.now()
    ): Int = rawCycleDay(lastPeriodStart, today).coerceIn(1, cycleLength)

    /**
     * True when raw elapsed days exceed cycle length.
     * Uses rawCycleDay so clamping doesn't hide a late period.
     */
    fun isPeriodLate(lastPeriodStart: LocalDate, cycleLength: Int): Boolean =
        rawCycleDay(lastPeriodStart) > cycleLength

    /**
     * Days past the expected period date.
     * Uses rawCycleDay so clamping doesn't hide a late period.
     */
    fun daysLate(lastPeriodStart: LocalDate, cycleLength: Int): Int =
        (rawCycleDay(lastPeriodStart) - cycleLength).coerceAtLeast(0)

    /** Next predicted period = last period start + cycle length. */
    fun nextPeriodDate(lastPeriodStart: LocalDate, cycleLength: Int): LocalDate =
        lastPeriodStart.plusDays(cycleLength.toLong())

    fun daysUntilNextPeriod(
        lastPeriodStart: LocalDate,
        cycleLength: Int,
        today: LocalDate = LocalDate.now()
    ): Int = ChronoUnit.DAYS.between(
        today, nextPeriodDate(lastPeriodStart, cycleLength)
    ).toInt().coerceAtLeast(0)

    /**
     * Weighted average of recent cycle lengths (last 6, newest weighted most).
     * Ignores outliers outside 18–60 days.
     */
    fun weightedAverageCycleLength(cycleLengths: List<Int>): Int {
        val valid = cycleLengths.filter { it in 18..60 }.takeLast(6)
        if (valid.isEmpty()) return 28
        val weighted = valid.mapIndexed { i, len -> len * (i + 1) }
        val totalWeight = (1..valid.size).sum()
        val avg = weighted.sum().toDouble() / totalWeight
        Log.d(TAG, "weightedAvg: input=$valid → $avg")
        return avg.toInt().coerceIn(18, 60)
    }

    /**
     * Compute personalised cycle length from a list of confirmed period start dates.
     * Returns null if < 2 confirmed periods (caller should fall back to user setting).
     */
    fun averageCycleLength(startDates: List<LocalDate>): Int? {
        if (startDates.size < 2) return null
        val sorted = startDates.sorted()
        val gaps = (0 until sorted.size - 1).map {
            ChronoUnit.DAYS.between(sorted[it], sorted[it + 1]).toInt()
        }.filter { it in 18..60 }
        if (gaps.isEmpty()) return null
        return weightedAverageCycleLength(gaps)
    }

    fun cycleRegularity(startDates: List<LocalDate>): String {
        if (startDates.size < 3) return "Insufficient data"
        val sorted = startDates.sorted()
        val gaps = (0 until sorted.size - 1).map {
            ChronoUnit.DAYS.between(sorted[it], sorted[it + 1]).toInt()
        }.filter { it in 18..60 }
        if (gaps.isEmpty()) return "Irregular"
        val avg = gaps.average()
        val variance = gaps.map { Math.abs(it - avg) }.average()
        return when {
            variance <= 2.0 -> "Regular"
            variance <= 5.0 -> "Slightly irregular"
            else            -> "Irregular"
        }
    }

    // ─────────────────────────────────────────────────────────────
    // A. PHASE
    // ─────────────────────────────────────────────────────────────

    enum class Phase { MENSTRUAL, FOLLICULAR, OVULATION, LUTEAL_EARLY, LUTEAL_LATE }

    /**
     * Returns the cycle phase for a given day.
     *
     * Handles short cycles correctly:
     * - Ovulation day is clamped to always be AFTER the period ends
     * - Fertile window start is clamped to always be AFTER the period ends
     * - Follicular phase can be 0 days on very short cycles (< 21 days)
     */
    fun phase(day: Int, cycleLength: Int, periodDuration: Int): Phase {
        // Ovulation = 14 days before next period, but must be after period ends
        val ovDay = (cycleLength - 14).coerceAtLeast(periodDuration + 2)

        // Fertile window starts 5 days before ovulation, but must be after period ends
        val fertileStart = (ovDay - 5).coerceAtLeast(periodDuration + 1)

        return when {
            day <= periodDuration           -> Phase.MENSTRUAL
            day < fertileStart              -> Phase.FOLLICULAR
            day in fertileStart until ovDay -> Phase.OVULATION   // fertile window
            day == ovDay                    -> Phase.OVULATION   // ovulation day
            day <= ovDay + 3               -> Phase.LUTEAL_EARLY
            else                           -> Phase.LUTEAL_LATE
        }
    }

    fun phaseName(phase: Phase) = when (phase) {
        Phase.MENSTRUAL    -> "Menstrual Phase"
        Phase.FOLLICULAR   -> "Follicular Phase"
        Phase.OVULATION    -> "Ovulatory Phase"
        Phase.LUTEAL_EARLY -> "Luteal Phase"
        Phase.LUTEAL_LATE  -> "Pre-Menstrual Phase"
    }

    fun phaseDescription(phase: Phase) = when (phase) {
        Phase.MENSTRUAL    -> "Rest and hydrate"
        Phase.FOLLICULAR   -> "Energy rising, great for workouts"
        Phase.OVULATION    -> "Peak fertility window"
        Phase.LUTEAL_EARLY -> "May feel bloated or moody"
        Phase.LUTEAL_LATE  -> "PMS symptoms may appear"
    }

    // ─────────────────────────────────────────────────────────────
    // A. CALENDAR SETS
    // ─────────────────────────────────────────────────────────────

    fun periodDays(lastPeriodStart: LocalDate, periodDuration: Int): Set<LocalDate> =
        (0 until periodDuration).map { lastPeriodStart.plusDays(it.toLong()) }.toSet()

    fun predictedPeriodDays(
        lastPeriodStart: LocalDate,
        cycleLength: Int,
        periodDuration: Int
    ): Set<LocalDate> {
        val nextStart = nextPeriodDate(lastPeriodStart, cycleLength)
        return (0 until periodDuration).map { nextStart.plusDays(it.toLong()) }.toSet()
    }

    fun fertileWindowDays(fertileStart: LocalDate, fertileEnd: LocalDate): Set<LocalDate> {
        val days = mutableSetOf<LocalDate>()
        var d = fertileStart
        while (!d.isAfter(fertileEnd)) { days.add(d); d = d.plusDays(1) }
        return days
    }

    fun isFertile(date: LocalDate, fertileStart: LocalDate, fertileEnd: LocalDate): Boolean =
        !date.isBefore(fertileStart) && !date.isAfter(fertileEnd)

    // ─────────────────────────────────────────────────────────────
    // B. OVULATION ENGINE
    // ─────────────────────────────────────────────────────────────

    /** Only Light / Medium / Heavy may start a new cycle. Never Spotting or None. */
    fun shouldStartNewCycle(flow: String): Boolean =
        flow in listOf("Light", "Medium", "Heavy")

    // ── BBT ────────────────────────────────────────────────────

    data class BbtLog(val date: LocalDate, val temp: Float)

    /**
     * Detects ovulation from BBT.
     * Requires TWO consecutive days ≥ 0.2°F above the 3-day pre-rise baseline.
     * Single isolated spikes are ignored.
     * Returns the day BEFORE the sustained rise (estimated ovulation day), or null.
     */
    fun detectOvulationFromBBT(logs: List<BbtLog>): LocalDate? {
        if (logs.size < 5) return null
        val sorted = logs.sortedBy { it.date }
        for (i in 3 until sorted.size - 1) {
            val baseline = sorted.subList(i - 3, i).map { it.temp }.average()
            val rise0 = sorted[i].temp     >= baseline + 0.2
            val rise1 = sorted[i + 1].temp >= baseline + 0.2
            if (rise0 && rise1) {
                val ov = sorted[i - 1].date
                Log.d(TAG, "BBT sustained shift: day=${sorted[i].date} " +
                        "baseline=$baseline → ovulation=$ov")
                return ov
            }
        }
        return null
    }

    // ── Cervical fluid ─────────────────────────────────────────

    private val CF_SCORE = mapOf(
        "Dry"               to 0,
        "Sticky"            to 1,
        "Creamy"            to 2,
        "Watery"            to 3,
        "Egg White Fertile" to 4
    )

    fun fertilityFromCervical(fluid: String) = when (fluid) {
        "Dry"               -> "Very Low"
        "Sticky"            -> "Low"
        "Creamy"            -> "Medium — approaching fertile window"
        "Watery"            -> "High — fertile window"
        "Egg White Fertile" -> "Peak — likely in fertile window"
        else                -> "Unknown"
    }

    data class CervicalSignal(
        val confidenceBoost: Int,
        val estimatedOvulation: LocalDate?
    )

    /**
     * Scores cervical fluid progression over the last 7 days.
     * Requires peak score ≥ 3 AND at least one upward progression step.
     */
    fun cervicalFertilityScore(logs: List<Pair<LocalDate, String>>): CervicalSignal {
        if (logs.isEmpty()) return CervicalSignal(0, null)
        val recent = logs.sortedBy { it.first }.takeLast(7)

        var peakScore = 0
        var peakDate: LocalDate? = null
        var progressionSteps = 0

        for (i in recent.indices) {
            val score = CF_SCORE[recent[i].second] ?: 0
            if (score > peakScore) { peakScore = score; peakDate = recent[i].first }
            if (i > 0 && score > (CF_SCORE[recent[i - 1].second] ?: 0)) progressionSteps++
        }

        val estimatedOvulation = if (peakScore >= 3 && progressionSteps >= 1)
            peakDate?.plusDays(1) else null

        val boost = when {
            peakScore == 4 && progressionSteps >= 2 -> 20
            peakScore >= 3 && progressionSteps >= 1 -> 12
            peakScore == 4                          -> 6
            else                                    -> 0
        }
        Log.d(TAG, "cervical: peak=$peakScore steps=$progressionSteps " +
                "boost=$boost estOv=$estimatedOvulation")
        return CervicalSignal(boost, estimatedOvulation)
    }

    // ── LH / Ovulation test ────────────────────────────────────

    data class LhLog(val date: LocalDate, val result: String)

    data class LhSignal(
        val confidenceBoost: Int,
        val estimatedOvulation: LocalDate?
    )

    /**
     * A positive LH test suggests ovulation is approaching (typically 24–36h later).
     * Does NOT confirm ovulation occurred.
     */
    fun lhEvidence(logs: List<LhLog>): LhSignal {
        val latestPositive = logs.filter { it.result == "Positive" }
            .maxByOrNull { it.date } ?: return LhSignal(0, null)
        val est = latestPositive.date.plusDays(1)
        Log.d(TAG, "LH positive on ${latestPositive.date} → estimated ovulation=$est")
        return LhSignal(15, est)
    }

    // ─────────────────────────────────────────────────────────────
    // COMBINED PREDICTION  (single source of truth)
    // ─────────────────────────────────────────────────────────────

    data class CyclePrediction(
        val lastPeriodStart: LocalDate,
        val cycleLength: Int,
        val nextPeriodDate: LocalDate,
        val ovulationDate: LocalDate,
        val fertileStart: LocalDate,
        val fertileEnd: LocalDate,
        val confidence: Int,
        val confidenceLabel: String,
        val dataSource: String
    )

    /**
     * Builds the single prediction that all UI consumes.
     *
     * Period anchor : ALWAYS lastPeriodStart + cycleLength.
     * Ovulation     : nextPeriod − lutealPhase (14 days).
     * Guards        :
     *   - Ovulation is clamped to always fall AFTER the period ends.
     *   - Fertile window start is clamped to always fall AFTER the period ends.
     *   - Both guards handle abnormal cycle lengths (18–25 days) correctly.
     */
    fun bestPrediction(
        lastPeriodStart: LocalDate,
        avgCycleLength: Int,
        periodDuration: Int = 5,   // ✅ needed to guard against short cycles
        lutealPhase: Int = 14
    ): CyclePrediction {

        val nextPeriod = lastPeriodStart.plusDays(avgCycleLength.toLong())

        // ✅ Ovulation must be after period ends
        val periodEnd    = lastPeriodStart.plusDays(periodDuration.toLong())
        val rawOvulation = nextPeriod.minusDays(lutealPhase.toLong())
        val ovulation    = if (rawOvulation.isAfter(periodEnd)) rawOvulation
        else periodEnd.plusDays(1)

        if (rawOvulation != ovulation) {
            Log.w(TAG, "bestPrediction: ovulation clamped — " +
                    "raw=$rawOvulation falls inside period " +
                    "($lastPeriodStart + ${periodDuration}d) → clamped to $ovulation")
        }

        // ✅ Fertile window must also be after period ends
        val rawFertileStart = ovulation.minusDays(5)
        val fertileStart    = if (rawFertileStart.isAfter(periodEnd)) rawFertileStart
        else periodEnd.plusDays(1)
        val fertileEnd      = ovulation.plusDays(1)

        Log.d(TAG, "bestPrediction: lastPeriod=$lastPeriodStart " +
                "cycleLen=$avgCycleLength periodDuration=$periodDuration " +
                "nextPeriod=$nextPeriod ovulation=$ovulation " +
                "fertile=$fertileStart→$fertileEnd")

        return CyclePrediction(
            lastPeriodStart = lastPeriodStart,
            cycleLength     = avgCycleLength,
            nextPeriodDate  = nextPeriod,
            ovulationDate   = ovulation,
            fertileStart    = fertileStart,
            fertileEnd      = fertileEnd,
            confidence      = 85,
            confidenceLabel = "Based on your data",
            dataSource      = "Formula"
        )
    }

    // ─────────────────────────────────────────────────────────────
    // C. SYMPTOM ENGINE  (read-only; never modifies cycle dates)
    // ─────────────────────────────────────────────────────────────

    private val PMS_SYMPTOMS = setOf(
        "Cramps", "Bloating", "Tender Breasts", "Cravings", "Irritable"
    )

    /**
     * Returns the average cycle day on which PMS symptoms begin,
     * or null if insufficient data.
     * For insight display only — never affects predictions.
     */
    fun detectPmsOnsetDay(logs: List<Triple<LocalDate, String, Int>>): Int? {
        val pmsDays = logs.filter { (_, symptoms, _) ->
            symptoms.split(",").any { it.trim() in PMS_SYMPTOMS }
        }.map { it.third }
        if (pmsDays.isEmpty()) return null
        return pmsDays.average().toInt()
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private fun Boolean.toInt() = if (this) 1 else 0
}