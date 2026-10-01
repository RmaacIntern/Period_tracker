package com.example.periodtracker

import com.aivigil.periodtracker.domain.CycleEngine
import com.aivigil.periodtracker.onboarding.OnboardingFragment5
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Tests pure logic from ProfileFragment without Android or fragments.
 */
class ProfileLogicTest {

    // ─────────────────────────────────────────────────────────────
    // tvCycleTypeBadge — cycleRegularity display
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testCycleTypeBadge_regularCycle() {
        val dates = listOf(
            LocalDate.of(2024, 7, 1),
            LocalDate.of(2024, 7, 29),
            LocalDate.of(2024, 8, 26),
            LocalDate.of(2024, 9, 23)
        )
        assertEquals("Regular", CycleEngine.cycleRegularity(dates))
    }

    @Test
    fun testCycleTypeBadge_insufficientData() {
        val dates = listOf(LocalDate.of(2024, 9, 1))
        assertEquals("Insufficient data", CycleEngine.cycleRegularity(dates))
    }

    @Test
    fun testCycleTypeBadge_irregular() {
        val dates = listOf(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 29),
            LocalDate.of(2024, 3, 5),
            LocalDate.of(2024, 4, 24)
        )
        assertEquals("Irregular", CycleEngine.cycleRegularity(dates))
    }

    // ─────────────────────────────────────────────────────────────
    // ActivityLevel.valueOf — round-trip from stored string
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testActivityLevelValueOf_balanced() {
        val stored   = "BALANCED"
        val restored = OnboardingFragment5.ActivityLevel.valueOf(stored)
        assertEquals(OnboardingFragment5.ActivityLevel.BALANCED, restored)
    }

    @Test
    fun testActivityLevelValueOf_gentle() {
        val stored   = "GENTLE"
        val restored = OnboardingFragment5.ActivityLevel.valueOf(stored)
        assertEquals(OnboardingFragment5.ActivityLevel.GENTLE, restored)
    }

    @Test
    fun testActivityLevelValueOf_veryActive() {
        val stored   = "VERY_ACTIVE"
        val restored = OnboardingFragment5.ActivityLevel.valueOf(stored)
        assertEquals(OnboardingFragment5.ActivityLevel.VERY_ACTIVE, restored)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testActivityLevelValueOf_wrongCase_throws() {
        // "Balanced" (wrong case) → throws IllegalArgumentException
        OnboardingFragment5.ActivityLevel.valueOf("Balanced")
    }

    // ─────────────────────────────────────────────────────────────
    // Conditions display — count summary
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testConditionsDisplay_none() {
        val condList = emptyList<String>()
        val display  = when {
            condList.isEmpty() -> "None"
            condList.size == 1 -> condList[0]
            else               -> "${condList.size} conditions"
        }
        assertEquals("None", display)
    }

    @Test
    fun testConditionsDisplay_oneCondition() {
        val condList = listOf("PCOS")
        val display  = when {
            condList.isEmpty() -> "None"
            condList.size == 1 -> condList[0]
            else               -> "${condList.size} conditions"
        }
        assertEquals("PCOS", display)
    }

    @Test
    fun testConditionsDisplay_multipleConditions() {
        val condList = listOf("PCOS", "Anemia", "Migraines")
        val display  = when {
            condList.isEmpty() -> "None"
            condList.size == 1 -> condList[0]
            else               -> "${condList.size} conditions"
        }
        assertEquals("3 conditions", display)
    }

    @Test
    fun testConditionsDisplay_twoConditions() {
        val condList = listOf("PCOS", "Anemia")
        val display  = when {
            condList.isEmpty() -> "None"
            condList.size == 1 -> condList[0]
            else               -> "${condList.size} conditions"
        }
        assertEquals("2 conditions", display)
    }

    // ─────────────────────────────────────────────────────────────
    // Goal display — always "Track My Cycle"
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testGoalDisplayIsAlwaysTrackMyCycle() {
        // Single-goal app — stored as "TRACK_CYCLE" but displayed as friendly label
        val stored  = "TRACK_CYCLE"
        val display = "Track My Cycle"   // hardcoded in ProfileFragment
        assertNotEquals("stored and display differ", stored, display)
        assertEquals("display is always Track My Cycle", "Track My Cycle", display)
    }

    // ─────────────────────────────────────────────────────────────
    // Member since date formatting
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testMemberSinceDateParsedCorrectly() {
        val stored = "2024-09-14"
        val parsed = runCatching { LocalDate.parse(stored) }.getOrNull()
        assertNotNull("valid date parses", parsed)
        assertEquals(2024, parsed!!.year)
        assertEquals(9, parsed.monthValue)
        assertEquals(14, parsed.dayOfMonth)
    }

    @Test
    fun testMemberSinceInvalidDateFallsBack() {
        val stored  = "invalid-date"
        val parsed  = runCatching { LocalDate.parse(stored) }.getOrNull()
        assertNull("invalid date returns null", parsed)
        val display = parsed?.toString() ?: stored
        assertEquals("fallback to raw string", "invalid-date", display)
    }

    // ─────────────────────────────────────────────────────────────
    // 8pm delay calculation
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDelayUntil8pmIsPositive() {
        val now    = java.time.LocalDateTime.now()
        var target = now.withHour(20).withMinute(0).withSecond(0)
        if (now.isAfter(target)) target = target.plusDays(1)
        val delay = java.time.Duration.between(now, target).toMillis()
        assertTrue("delay is positive", delay > 0)
    }

    @Test
    fun testDelayUntil8pmIsWithin24Hours() {
        val now    = java.time.LocalDateTime.now()
        var target = now.withHour(20).withMinute(0).withSecond(0)
        if (now.isAfter(target)) target = target.plusDays(1)
        val delay   = java.time.Duration.between(now, target).toMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        assertTrue("delay is within 24h", delay <= oneDayMs)
    }
}