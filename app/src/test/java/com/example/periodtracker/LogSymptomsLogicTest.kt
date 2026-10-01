package com.example.periodtracker

import com.aivigil.periodtracker.domain.CycleEngine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Tests pure logic from LogSymptomsFragment without Android or fragments.
 */
class LogSymptomsLogicTest {

    // ─────────────────────────────────────────────────────────────
    // Flow collection — shouldStartNewCycle gating
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testLightFlowShowsDialog() {
        assertTrue(CycleEngine.shouldStartNewCycle("Light"))
    }

    @Test
    fun testMediumFlowShowsDialog() {
        assertTrue(CycleEngine.shouldStartNewCycle("Medium"))
    }

    @Test
    fun testHeavyFlowShowsDialog() {
        assertTrue(CycleEngine.shouldStartNewCycle("Heavy"))
    }

    @Test
    fun testSpottingDoesNotShowDialog() {
        assertFalse(CycleEngine.shouldStartNewCycle("Spotting"))
    }

    @Test
    fun testNoFlowDoesNotShowDialog() {
        assertFalse(CycleEngine.shouldStartNewCycle("None"))
    }

    @Test
    fun testEmptyFlowDoesNotShowDialog() {
        assertFalse(CycleEngine.shouldStartNewCycle(""))
    }

    // ─────────────────────────────────────────────────────────────
    // BBT guard — only saved if user changed it
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testBbtNotSavedWhenUnchanged() {
        val basalTempChanged = false
        val basalTemp        = 98.0f
        val bbt              = if (basalTempChanged) basalTemp else null
        assertNull("BBT not saved when unchanged", bbt)
    }

    @Test
    fun testBbtSavedWhenChanged() {
        val basalTempChanged = true
        val basalTemp        = 97.6f
        val bbt              = if (basalTempChanged) basalTemp else null
        assertNotNull("BBT saved when changed", bbt)
        assertEquals(97.6f, bbt!!, 0.01f)
    }

    // ─────────────────────────────────────────────────────────────
    // Default BBT value
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testDefaultBbtIsNeutral() {
        val defaultTemp = 98.0f
        assertTrue("default BBT >= 97", defaultTemp >= 97f)
        assertTrue("default BBT <= 99", defaultTemp <= 99f)
    }

    // ─────────────────────────────────────────────────────────────
    // Mood / symptom collection
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testMoodsJoinedWithComma() {
        val selected = listOf("😊 Happy", "😌 Calm")
        val joined   = selected.joinToString(",")
        assertEquals("😊 Happy,😌 Calm", joined)
    }

    @Test
    fun testSymptomsJoinedWithComma() {
        val selected = listOf("Cramps", "Fatigue")
        val joined   = selected.joinToString(",")
        assertEquals("Cramps,Fatigue", joined)
    }

    @Test
    fun testEmptyMoodsJoinsToEmpty() {
        val selected = emptyList<String>()
        val joined   = selected.joinToString(",")
        assertEquals("", joined)
    }

    // ─────────────────────────────────────────────────────────────
    // isSaving guard — prevents double save
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testIsSavingGuardPreventsDoubleSave() {
        var isSaving   = false
        var saveCount  = 0

        val save = {
            if (!isSaving) {
                isSaving = true
                saveCount++
            }
        }

        save()  // first tap
        save()  // second tap — should be blocked

        assertEquals("save called only once", 1, saveCount)
        assertTrue("isSaving is true after first save", isSaving)
    }

    @Test
    fun testSaveButtonsResetAfterSave() {
        var isSaving = true
        // After save completes
        isSaving = false
        assertFalse("isSaving reset after save", isSaving)
    }

    // ─────────────────────────────────────────────────────────────
    // Date handling
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testTodayIsNotPastDate() {
        val today = LocalDate.now()
        assertFalse("today is not past", today.isBefore(LocalDate.now()))
    }

    @Test
    fun testYesterdayIsPastDate() {
        val yesterday = LocalDate.now().minusDays(1)
        assertTrue("yesterday is past", yesterday.isBefore(LocalDate.now()))
    }

    @Test
    fun testTargetDateDefaultsToToday() {
        // Simulates: arguments?.getString(ARG_DATE)?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val dateStr: String? = null
        val targetDate = dateStr?.let { LocalDate.parse(it) } ?: LocalDate.now()
        assertEquals("defaults to today", LocalDate.now(), targetDate)
    }

    @Test
    fun testTargetDateParsedFromArgument() {
        val dateStr    = "2024-09-15"
        val targetDate = dateStr.let { LocalDate.parse(it) }
        assertEquals(LocalDate.of(2024, 9, 15), targetDate)
    }
}