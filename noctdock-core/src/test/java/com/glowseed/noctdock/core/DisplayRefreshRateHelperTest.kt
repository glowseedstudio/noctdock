package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayRefreshRateHelperTest {
    @Test
    fun smooth60HzPreferenceDefaultsOff() {
        assertEquals(Smooth60HzMode.Off, PerformanceSettings().smooth60HzMode)
    }

    @Test
    fun shouldRequestOnlyForAlwaysMode() {
        assertFalse(DisplayRefreshRateHelper.shouldRequestOnConsoleStart(Smooth60HzMode.Off))
        assertFalse(DisplayRefreshRateHelper.shouldRequestOnConsoleStart(Smooth60HzMode.AskOnStart))
        assertTrue(DisplayRefreshRateHelper.shouldRequestOnConsoleStart(Smooth60HzMode.Always))
    }

    @Test
    fun offModeReturnsNotRequestedStatus() {
        val status = RefreshRateHelperStatus()
        assertEquals(RefreshRateHelperResult.NotRequested, status.result)
        assertFalse(status.requested60Hz)
    }

    @Test
    fun resultLabelsAreUserFriendly() {
        assertEquals(
            "Unsupported on this device",
            RefreshRateHelperResult.Unsupported.let {
                RefreshRateHelperStatus(result = it).resultLabel()
            },
        )
    }
}
