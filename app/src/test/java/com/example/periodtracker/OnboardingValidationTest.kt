package com.example.periodtracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Tests the validateStep() logic from OnboardingActivity
 * as pure functions — no Activity, no fragments needed.
 */
class OnboardingValidationTest {

    // ─── Mirrors OnboardingActivity.validateStep() for step 0 ────
    private fun validateName(name: String?): String? = when {
        name.isNullOrBlank()  -> "Please enter your name to continue"
        name.length < 3       -> "Name must be at least 3 characters"
        name.length > 15      -> "Name must be 15 characters or less"
        else                  -> null
    }

    // ─── Mirrors validateStep() for step 6 ───────────────────────
    private fun validateDate(date: LocalDate?): String? = when {
        date == null ->
            "Please select when your last period started"
        date.isAfter(LocalDate.now()) ->
            "Last period start cannot be in the future"
        date.isBefore(LocalDate.now().minusDays(90)) ->
            "Please enter a date within the last 90 days"
        else -> null
    }

    // ─────────────────────────────────────────────────────────────
    // Step 0 — name validation
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testNameNull_returnsError() {
        assertNotNull(validateName(null))
    }

    @Test
    fun testNameBlank_returnsError() {
        assertNotNull(validateName("   "))
    }

    @Test
    fun testNameEmpty_returnsError() {
        assertNotNull(validateName(""))
    }

    @Test
    fun testNameTooShort_returnsError() {
        val error = validateName("ab")
        assertEquals("Name must be at least 3 characters", error)
    }

    @Test
    fun testNameTooLong_returnsError() {
        val error = validateName("abcdefghijklmnop")   // 16 chars
        assertEquals("Name must be 15 characters or less", error)
    }

    @Test
    fun testNameExactly3Chars_passes() {
        assertNull("3 chars is valid", validateName("Ada"))
    }

    @Test
    fun testNameExactly15Chars_passes() {
        assertNull("15 chars is valid", validateName("AbcdefghijklmNo"))
    }

    @Test
    fun testNameValid_passes() {
        assertNull("normal name passes", validateName("Sara"))
    }

    @Test
    fun testNameWithSpaces_trimmedAndValid() {
        // Activity trims before validating
        val trimmed = "  Sara  ".trim()
        assertNull(validateName(trimmed))
    }

    // ─────────────────────────────────────────────────────────────
    // Step 6 — last period date validation
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDateNull_returnsError() {
        assertNotNull(validateDate(null))
    }

    @Test
    fun testFutureDate_returnsError() {
        val future = LocalDate.now().plusDays(1)
        assertEquals(
            "Last period start cannot be in the future",
            validateDate(future)
        )
    }

    @Test
    fun testDateTooOld_returnsError() {
        val tooOld = LocalDate.now().minusDays(91)
        assertEquals(
            "Please enter a date within the last 90 days",
            validateDate(tooOld)
        )
    }

    @Test
    fun testTodayIsValid() {
        assertNull("today is valid", validateDate(LocalDate.now()))
    }

    @Test
    fun testYesterdayIsValid() {
        assertNull("yesterday is valid", validateDate(LocalDate.now().minusDays(1)))
    }

    @Test
    fun testExactly90DaysAgoIsValid() {
        assertNull("90 days ago is valid", validateDate(LocalDate.now().minusDays(90)))
    }

    @Test
    fun test91DaysAgoIsInvalid() {
        assertNotNull("91 days ago is invalid", validateDate(LocalDate.now().minusDays(91)))
    }

    // ─────────────────────────────────────────────────────────────
    // saveAndProceed fallback string correctness
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testActivityLevelFallback_isUppercase() {
        val fallback = com.aivigil.periodtracker.onboarding.OnboardingFragment5
            .ActivityLevel.BALANCED.name
        assertEquals("BALANCED", fallback)
        assertNotEquals("Balanced", fallback)
    }

    @Test
    fun testGoalFallback_isUppercase() {
        val fallback = com.aivigil.periodtracker.onboarding.OnboardingFragment6
            .Goal.TRACK_CYCLE.name
        assertEquals("TRACK_CYCLE", fallback)
        assertNotEquals("Track My Cycle", fallback)
    }

    @Test
    fun testActivityLevelValueOf_roundTrips() {
        // Confirms ProfileFragment's ActivityLevel.valueOf(current) works
        val stored = com.aivigil.periodtracker.onboarding.OnboardingFragment5
            .ActivityLevel.BALANCED.name
        val restored = com.aivigil.periodtracker.onboarding.OnboardingFragment5
            .ActivityLevel.valueOf(stored)
        assertEquals(
            com.aivigil.periodtracker.onboarding.OnboardingFragment5.ActivityLevel.BALANCED,
            restored
        )
    }
}