package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCloakPolicyTest {
    @Test
    fun modeToBrightnessMappingMatchesExpectedLevels() {
        assertEquals(null, ScreenCloakPolicy.overlayBrightness(ScreenCloakMode.OFF))
        assertEquals(0.15f, ScreenCloakPolicy.overlayBrightness(ScreenCloakMode.DIM))
        assertEquals(0.05f, ScreenCloakPolicy.overlayBrightness(ScreenCloakMode.DARK))
        assertEquals(0.0f, ScreenCloakPolicy.overlayBrightness(ScreenCloakMode.MAXIMUM_DARK))
        assertEquals(38, ScreenCloakPolicy.fallbackBrightness(ScreenCloakMode.DIM))
        assertEquals(13, ScreenCloakPolicy.fallbackBrightness(ScreenCloakMode.DARK))
        assertEquals(0, ScreenCloakPolicy.fallbackBrightness(ScreenCloakMode.MAXIMUM_DARK))
    }

    @Test
    fun applyTwiceDoesNotOverwriteOriginalBrightness() {
        val initial = ScreenCloakSession()
        val captured = ScreenCloakSessionTracker.captureOriginal(initial, brightness = 120, brightnessMode = 1)
        val recaptured = ScreenCloakSessionTracker.captureOriginal(captured, brightness = 20, brightnessMode = 0)

        assertEquals(120, recaptured.originalBrightness)
        assertEquals(1, recaptured.originalBrightnessMode)
        assertTrue(recaptured.capturedOriginal)
    }

    @Test
    fun restoreIsIdempotent() {
        val active =
            ScreenCloakSessionTracker.markApplied(
                ScreenCloakSession(capturedOriginal = true),
                ScreenCloakMethod.SYSTEM_BRIGHTNESS_FALLBACK,
            )
        val restoredOnce = ScreenCloakSessionTracker.markRestored(active)
        val restoredTwice = ScreenCloakSessionTracker.markRestored(restoredOnce)

        assertTrue(restoredOnce.restored)
        assertTrue(restoredTwice.restored)
        assertEquals(ScreenCloakMethod.NONE, restoredTwice.appliedMethod)
    }

    @Test
    fun permissionDeniedStateIsReportedWhenNoMethodIsAvailable() {
        assertEquals(
            ScreenCloakState.PERMISSION_NEEDED,
            ScreenCloakPolicy.stateFor(
                mode = ScreenCloakMode.DARK,
                overlayPermissionGranted = false,
                systemWritePermissionGranted = false,
                overlayDisabledDueToTvPictureIssue = false,
            ),
        )
    }

    @Test
    fun contaminatedTvTestDisablesOverlayMethodAndFallsBack() {
        assertEquals(
            ScreenCloakMethod.SYSTEM_BRIGHTNESS_FALLBACK,
            ScreenCloakPolicy.preferredMethod(
                mode = ScreenCloakMode.DARK,
                overlayPermissionGranted = true,
                systemWritePermissionGranted = true,
                overlayDisabledDueToTvPictureIssue = true,
            ),
        )
        assertFalse(
            ScreenCloakPolicy.preferredMethod(
                mode = ScreenCloakMode.DARK,
                overlayPermissionGranted = true,
                systemWritePermissionGranted = false,
                overlayDisabledDueToTvPictureIssue = true,
            ) == ScreenCloakMethod.TRANSPARENT_OVERLAY,
        )
    }
}
