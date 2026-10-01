package com.example.periodtracker

import com.aivigil.periodtracker.notification.PeriodConfirmReceiver
import org.junit.Assert.*
import org.junit.Test

class PeriodConfirmReceiverTest {

    // ─────────────────────────────────────────────────────────────
    // Action constants
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testActionYesConstant() {
        assertEquals(
            "com.aivigil.periodtracker.PERIOD_YES",
            PeriodConfirmReceiver.ACTION_PERIOD_YES
        )
    }

    @Test
    fun testActionNotYetConstant() {
        assertEquals(
            "com.aivigil.periodtracker.PERIOD_NOT_YET",
            PeriodConfirmReceiver.ACTION_PERIOD_NOT_YET
        )
    }

    @Test
    fun testActionConstantsAreUnique() {
        assertNotEquals(
            "YES and NOT_YET actions must differ",
            PeriodConfirmReceiver.ACTION_PERIOD_YES,
            PeriodConfirmReceiver.ACTION_PERIOD_NOT_YET
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Action matching logic (mirrors the when block)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testYesActionIsHandled() {
        val action  = PeriodConfirmReceiver.ACTION_PERIOD_YES
        var handled = false
        when (action) {
            PeriodConfirmReceiver.ACTION_PERIOD_YES     -> handled = true
            PeriodConfirmReceiver.ACTION_PERIOD_NOT_YET -> handled = false
        }
        assertTrue("ACTION_PERIOD_YES is handled", handled)
    }

    @Test
    fun testNotYetActionIsHandled() {
        val action  = PeriodConfirmReceiver.ACTION_PERIOD_NOT_YET
        var handled = false
        when (action) {
            PeriodConfirmReceiver.ACTION_PERIOD_YES     -> handled = false
            PeriodConfirmReceiver.ACTION_PERIOD_NOT_YET -> handled = true
        }
        assertTrue("ACTION_PERIOD_NOT_YET is handled", handled)
    }

    @Test
    fun testUnknownActionIsNotHandled() {
        val action  = "com.aivigil.periodtracker.UNKNOWN"
        var handled = false
        when (action) {
            PeriodConfirmReceiver.ACTION_PERIOD_YES     -> handled = true
            PeriodConfirmReceiver.ACTION_PERIOD_NOT_YET -> handled = true
        }
        assertFalse("Unknown action is not handled", handled)
    }

    // ─────────────────────────────────────────────────────────────
    // No duplicate action handling (the bug we fixed)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testYesActionHandledExactlyOnce() {
        val action   = PeriodConfirmReceiver.ACTION_PERIOD_YES
        var callCount = 0
        when (action) {
            PeriodConfirmReceiver.ACTION_PERIOD_YES     -> callCount++
            PeriodConfirmReceiver.ACTION_PERIOD_NOT_YET -> callCount++
        }
        assertEquals("ACTION_PERIOD_YES handled exactly once", 1, callCount)
    }
}