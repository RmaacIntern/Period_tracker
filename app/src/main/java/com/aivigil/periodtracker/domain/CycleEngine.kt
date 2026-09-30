package com.aivigil.periodtracker.domain

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
 *
 * Updated:
 *   + futurePeriodDays()        – predict 3+ months of periods on the calendar
 *   + resolvedOvulationDate()   – merge BBT / LH / cervical into one best date
 *   + bestPrediction()          – now accepts bio signals + dynamic confidence
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
        val ovDay        = (cycleLength - 14).coerceAtLeast(periodDuration + 2)
        val fertileStart = (ovDay - 5).coerceAtLeast(periodDuration + 1)
        return when {
            day <= periodDuration           -> Phase.MENSTRUAL
            day < fertileStart              -> Phase.FOLLICULAR
            day in fertileStart until ovDay -> Phase.OVULATION
            day == ovDay                    -> Phase.OVULATION
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

    /**
     * ✅ NEW — Predicts all period start dates and their day-sets for the next
     * [monthsAhead] months. Uses plusMonths() as the hard cutoff so the count
     * is always correct regardless of cycle length (no integer-division bug).
     *
     * Returns: Map<periodStartDate → Set<all days in that period>>
     *
     * Usage — calendar fragment:
     *   val upcoming = CycleEngine.futurePeriodDays(lastStart, cycleLen, periodDur)
     *   upcoming.values.flatten().forEach { date -> markRed(date) }
     */
    fun futurePeriodDays(
        lastPeriodStart: LocalDate,
        cycleLength: Int,
        periodDuration: Int,
        monthsAhead: Int = 3
    ): Map<LocalDate, Set<LocalDate>> {
        val cutoff = lastPeriodStart.plusMonths(monthsAhead.toLong())
        val result = mutableMapOf<LocalDate, Set<LocalDate>>()
        var cycleNumber = 1
        while (true) {
            val start = lastPeriodStart.plusDays((cycleLength * cycleNumber).toLong())
            if (start.isAfter(cutoff)) break
            result[start] = periodDays(start, periodDuration)
            Log.d(TAG, "futurePeriodDays: cycle #$cycleNumber → $start")
            cycleNumber++
        }
        Log.d(TAG, "futurePeriodDays: ${result.size} periods over " +
                "$monthsAhead months (cycleLen=$cycleLength)")
        return result
    }

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
     * ✅ NEW — Merges BBT, LH, and cervical signals into one best ovulation date.
     *
     * Trust hierarchy (highest → lowest):
     *   1. BBT sustained shift       — post-ovulation confirmation, gold standard
     *   2. LH + cervical agree       — two signals within ±2 days → averaged
     *   3. LH + cervical disagree    — LH wins (more objective than cervical)
     *   4. LH alone                  — likely but unconfirmed
     *   5. Cervical alone            — supportive signal only
     *   6. Formula fallback          — no bio data logged
     */
    fun resolvedOvulationDate(
        formulaOvulation: LocalDate,
        bbtOvulation: LocalDate?,
        lhSignal: LhSignal,
        cervicalSignal: CervicalSignal
    ): LocalDate {

        // 1. BBT is the gold standard — temperature shift confirms ovulation occurred
        if (bbtOvulation != null) {
            Log.d(TAG, "resolvedOvulation: BBT confirmed → $bbtOvulation")
            return bbtOvulation
        }

        val lhDate = lhSignal.estimatedOvulation
        val cfDate = cervicalSignal.estimatedOvulation

        // 2 & 3. Both LH and cervical available
        if (lhDate != null && cfDate != null) {
            val diff = ChronoUnit.DAYS.between(lhDate, cfDate).toInt()
            return if (kotlin.math.abs(diff) <= 2) {
                // Signals agree — average them
                val avgDate = lhDate.plusDays((diff / 2).toLong())
                Log.d(TAG, "resolvedOvulation: LH+cervical agree (diff=$diff) → $avgDate")
                avgDate
            } else {
                // Signals disagree — trust LH (it's objective)
                Log.d(TAG, "resolvedOvulation: LH+cervical disagree (diff=$diff) → LH $lhDate")
                lhDate
            }
        }

        // 4. LH alone
        if (lhDate != null) {
            Log.d(TAG, "resolvedOvulation: LH only → $lhDate")
            return lhDate
        }

        // 5. Cervical alone
        if (cfDate != null) {
            Log.d(TAG, "resolvedOvulation: cervical only → $cfDate")
            return cfDate
        }

        // 6. No bio data — formula fallback
        Log.d(TAG, "resolvedOvulation: no signals → formula fallback $formulaOvulation")
        return formulaOvulation
    }

    /**
     * Builds the single prediction that all UI consumes.
     *
     * ✅ Updated: now accepts optional bio signals (BBT / LH / cervical).
     *    - If bio signals exist, ovulation is resolved from them instead of formula.
     *    - Confidence score is now dynamic (50–95) based on available data.
     *    - confidenceLabel and dataSource reflect the actual signal used.
     *
     * Period anchor : ALWAYS lastPeriodStart + cycleLength.
     * Ovulation     : resolvedOvulationDate() — formula if no bio data.
     * Guards        :
     *   - Ovulation is clamped to always fall AFTER the period ends.
     *   - Fertile window start is clamped to always fall AFTER the period ends.
     */
    fun bestPrediction(
        lastPeriodStart: LocalDate,
        avgCycleLength: Int,
        periodDuration: Int = 5,
        lutealPhase: Int = 14,
        // ✅ Bio signal params — all optional, default to "no data"
        bbtOvulation: LocalDate?     = null,
        lhSignal: LhSignal           = LhSignal(0, null),
        cervicalSignal: CervicalSignal = CervicalSignal(0, null)
    ): CyclePrediction {

        val nextPeriod = lastPeriodStart.plusDays(avgCycleLength.toLong())
        val periodEnd  = lastPeriodStart.plusDays(periodDuration.toLong())

        // Formula ovulation (clamped for short cycles)
        val rawFormulaOv  = nextPeriod.minusDays(lutealPhase.toLong())
        val formulaOv     = if (rawFormulaOv.isAfter(periodEnd)) rawFormulaOv
        else periodEnd.plusDays(1)

        if (rawFormulaOv != formulaOv) {
            Log.w(TAG, "bestPrediction: formula ovulation clamped — " +
                    "raw=$rawFormulaOv inside period → $formulaOv")
        }

        // ✅ Resolve using bio signals when available
        val ovulation = resolvedOvulationDate(formulaOv, bbtOvulation, lhSignal, cervicalSignal)

        // Fertile window (also clamped for short cycles)
        val rawFertileStart = ovulation.minusDays(5)
        val fertileStart    = if (rawFertileStart.isAfter(periodEnd)) rawFertileStart
        else periodEnd.plusDays(1)
        val fertileEnd      = ovulation.plusDays(1)

        // ✅ Dynamic confidence based on signal quality
        val hasBbt       = bbtOvulation != null
        val hasLh        = lhSignal.estimatedOvulation != null
        val hasCervical  = cervicalSignal.estimatedOvulation != null
        val lhCfAgree    = hasLh && hasCervical &&
                kotlin.math.abs(
                    ChronoUnit.DAYS.between(
                        lhSignal.estimatedOvulation, cervicalSignal.estimatedOvulation
                    ).toInt()
                ) <= 2

        val confidence = when {
            hasBbt                  -> 95   // BBT = confirmed
            lhCfAgree               -> 88   // LH + cervical agree
            hasLh && hasCervical    -> 82   // LH + cervical but disagree
            hasLh                   -> 80   // LH only
            hasCervical             -> 72   // cervical only
            else                    -> 70   // formula only
        }

        val confidenceLabel = when {
            hasBbt               -> "Confirmed by BBT"
            hasLh && hasCervical -> "Based on LH test + cervical signs"
            hasLh                -> "Based on LH test"
            hasCervical          -> "Based on cervical signs"
            else                 -> "Based on your cycle data"
        }

        val dataSource = when {
            hasBbt               -> "BBT"
            hasLh && hasCervical -> "LH+Cervical"
            hasLh                -> "LH"
            hasCervical          -> "Cervical"
            else                 -> "Formula"
        }

        Log.d(TAG, "bestPrediction: lastPeriod=$lastPeriodStart " +
                "cycleLen=$avgCycleLength periodDuration=$periodDuration " +
                "nextPeriod=$nextPeriod ovulation=$ovulation " +
                "fertile=$fertileStart→$fertileEnd " +
                "confidence=$confidence source=$dataSource")

        return CyclePrediction(
            lastPeriodStart = lastPeriodStart,
            cycleLength     = avgCycleLength,
            nextPeriodDate  = nextPeriod,
            ovulationDate   = ovulation,
            fertileStart    = fertileStart,
            fertileEnd      = fertileEnd,
            confidence      = confidence,
            confidenceLabel = confidenceLabel,
            dataSource      = dataSource
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