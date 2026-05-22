package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun themePersistenceModelDefaultsToPremiumMotion() {
        val settings = AppearanceSettings()

        assertEquals(NebulaTheme.CyanCore, settings.backgroundTheme)
        assertEquals(BackgroundMotionMode.AnimatedNebula, settings.backgroundMotionMode)
        assertEquals(AccentTheme.Cyan, settings.accentTheme)
        assertFalse(settings.reducedMotion)
        assertEquals(ScreenCloakMode.OFF, settings.screenCloakMode)
        assertFalse(settings.screenCloakOverlayDisabledDueToTvPictureIssue)
    }

    @Test
    fun reducedMotionDisablesAnimatedBackgroundIntent() {
        val settings = AppearanceSettings(reducedMotion = true, backgroundTheme = NebulaTheme.DeepSpace)

        assertTrue(settings.reducedMotion)
        assertEquals("Deep Space", AppearanceDefaults.backgroundLabel(settings.backgroundTheme))
        assertEquals(
            "Reduced Motion",
            BackgroundAmbiencePolicy.effectiveModeLabel(settings.backgroundMotionMode, settings.reducedMotion),
        )
    }

    @Test
    fun dockingStateTransitionsArePredictable() {
        assertEquals(
            DockingTransitionState.Pairing,
            DockingStateReducer.next(
                DockingTransitionState.Undocked,
                receiverTrusted = false,
                permissionGranted = false,
                streamActive = false,
            ),
        )
        assertEquals(
            DockingTransitionState.Permission,
            DockingStateReducer.next(
                DockingTransitionState.Preparing,
                receiverTrusted = true,
                permissionGranted = false,
                streamActive = false,
            ),
        )
        assertEquals(
            DockingTransitionState.Docked,
            DockingStateReducer.next(
                DockingTransitionState.Docking,
                receiverTrusted = true,
                permissionGranted = true,
                streamActive = true,
            ),
        )
    }
}
