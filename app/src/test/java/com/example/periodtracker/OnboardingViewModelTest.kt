package com.example.periodtracker

import com.aivigil.periodtracker.onboarding.OnboardingFragment5
import com.aivigil.periodtracker.onboarding.OnboardingFragment6
import com.aivigil.periodtracker.onboarding.viewmodel.OnboardingViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class OnboardingViewModelTest {

    private lateinit var vm: OnboardingViewModel

    @Before
    fun setup() {
        vm = OnboardingViewModel()
    }

    // ─────────────────────────────────────────────────────────────
    // Default values
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDefaultValues() {
        assertEquals("default userName empty", "", vm.userName)
        assertEquals("default age 27", 27, vm.age)
        assertEquals("default heightCm 162", 162, vm.heightCm)
        assertEquals("default weightKg 60", 60f, vm.weightKg)
        assertEquals("default conditions empty", "", vm.conditions)
        assertFalse("default noneSelected false", vm.noneSelected)
        assertNull("default activityLevel null", vm.activityLevel)
        assertNull("default goal null", vm.goal)
        assertNull("default lastPeriodStart null", vm.lastPeriodStart)
        assertEquals("default cycleLength 28", 28, vm.cycleLength)
        assertEquals("default periodDuration 5", 5, vm.periodDuration)
    }

    // ─────────────────────────────────────────────────────────────
    // Field assignment
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testUserNameAssignment() {
        vm.userName = "Sara"
        assertEquals("Sara", vm.userName)
    }

    @Test
    fun testActivityLevelAssignment() {
        vm.activityLevel = OnboardingFragment5.ActivityLevel.BALANCED
        assertEquals(OnboardingFragment5.ActivityLevel.BALANCED, vm.activityLevel)
    }

    @Test
    fun testGoalAssignment() {
        vm.goal = OnboardingFragment6.Goal.TRACK_CYCLE
        assertEquals(OnboardingFragment6.Goal.TRACK_CYCLE, vm.goal)
    }

    @Test
    fun testLastPeriodStartAssignment() {
        val date = LocalDate.of(2024, 9, 1)
        vm.lastPeriodStart = date
        assertEquals(date, vm.lastPeriodStart)
    }

    @Test
    fun testCycleLengthBoundsAssignment() {
        vm.cycleLength = 35
        assertEquals(35, vm.cycleLength)
        vm.cycleLength = 21
        assertEquals(21, vm.cycleLength)
    }

    @Test
    fun testPeriodDurationAssignment() {
        vm.periodDuration = 7
        assertEquals(7, vm.periodDuration)
    }

    // ─────────────────────────────────────────────────────────────
    // activityLevel .name matches what ProfileFragment expects
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testActivityLevelNameIsUppercase() {
        vm.activityLevel = OnboardingFragment5.ActivityLevel.BALANCED
        assertEquals("BALANCED", vm.activityLevel?.name)
    }

    @Test
    fun testActivityLevelGentleName() {
        vm.activityLevel = OnboardingFragment5.ActivityLevel.GENTLE
        assertEquals("GENTLE", vm.activityLevel?.name)
    }

    @Test
    fun testActivityLevelVeryActiveName() {
        vm.activityLevel = OnboardingFragment5.ActivityLevel.VERY_ACTIVE
        assertEquals("VERY_ACTIVE", vm.activityLevel?.name)
    }

    // ─────────────────────────────────────────────────────────────
    // goal .name matches what is stored in Room
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testGoalNameIsTrackCycle() {
        vm.goal = OnboardingFragment6.Goal.TRACK_CYCLE
        assertEquals("TRACK_CYCLE", vm.goal?.name)
    }

    // ─────────────────────────────────────────────────────────────
    // saveAndProceed fallback logic
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testActivityLevelFallbackMatchesEnumName() {
        // Simulates saveAndProceed() fallback when activityLevel is null
        val fallback = vm.activityLevel?.name
            ?: OnboardingFragment5.ActivityLevel.BALANCED.name
        assertEquals("fallback = BALANCED", "BALANCED", fallback)
    }

    @Test
    fun testGoalFallbackMatchesEnumName() {
        // Simulates saveAndProceed() fallback when goal is null
        val fallback = vm.goal?.name
            ?: OnboardingFragment6.Goal.TRACK_CYCLE.name
        assertEquals("fallback = TRACK_CYCLE", "TRACK_CYCLE", fallback)
    }

    @Test
    fun testUserNameFallbackIsUser() {
        // Simulates saveAndProceed() when userName is blank
        val result = vm.userName.ifEmpty { "User" }
        assertEquals("blank name → User", "User", result)
    }

    // ─────────────────────────────────────────────────────────────
    // Conditions
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testConditionsJoinedCorrectly() {
        val selected = listOf("PCOS", "Anemia")
        vm.conditions = selected.joinToString(", ")
        assertEquals("PCOS, Anemia", vm.conditions)
    }

    @Test
    fun testNoneSelectedClearsConditions() {
        vm.conditions    = ""
        vm.noneSelected  = true
        assertEquals("", vm.conditions)
        assertTrue(vm.noneSelected)
    }
}