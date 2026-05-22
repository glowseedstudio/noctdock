package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductModelsTest {
    @Test
    fun appLibrarySortingPrioritizesFavouritesRecentThenName() {
        val apps =
            listOf(
                LocalLibraryApp("b", "Beta", lastLaunchedAtMillis = 50),
                LocalLibraryApp("a", "Alpha", isFavourite = true, lastLaunchedAtMillis = 10),
                LocalLibraryApp("g", "Gamma", isFavourite = true, lastLaunchedAtMillis = 100),
            )

        assertEquals(listOf("Gamma", "Alpha", "Beta"), AppLibrarySorter.sort(apps).map { it.label })
    }

    @Test
    fun favouritesPersistenceModelIsLocalAndStable() {
        val favourite = LocalLibraryApp("com.example.game", "Example Game", isFavourite = true)

        assertTrue(favourite.isFavourite)
        assertEquals("com.example.game", favourite.packageName)
    }

    @Test
    fun recentAppOrderingUsesMostRecentFirst() {
        val sorted =
            AppLibrarySorter.sort(
                listOf(
                    LocalLibraryApp("old", "Old", lastLaunchedAtMillis = 1),
                    LocalLibraryApp("new", "New", lastLaunchedAtMillis = 2),
                ),
            )

        assertEquals("New", sorted.first().label)
    }

    @Test
    fun consoleModeStateTransitionsRequireTrustBeforeStreaming() {
        assertEquals(
            ConsoleModeState.Pairing,
            ConsoleModeReducer.start(receiverTrusted = false, permissionGranted = true),
        )
        assertEquals(
            ConsoleModeState.Streaming,
            ConsoleModeReducer.start(receiverTrusted = true, permissionGranted = true),
        )
        assertEquals(ConsoleModeState.Stopping, ConsoleModeReducer.stop(ConsoleModeState.Streaming))
    }

    @Test
    fun errorStateMapsToPlainEnglishMessage() {
        assertEquals(
            "NoctDock needs permission to mirror your handheld in Console Mode.",
            NoctError.MediaProjectionDenied.message,
        )
        assertTrue(NoctError.NetworkUnstable.diagnosticsUseful)
    }

    @Test
    fun streamProfilePersistenceDefaultsToStablePerformance() {
        val settings = PerformanceSettings()

        assertEquals(StreamProfiles.Performance, settings.selectedProfile)
        assertEquals(12, settings.effectiveBitrateMbps())
        assertEquals(SoundMode.RETROID, settings.soundMode)
    }

    @Test
    fun receiverTrustStateStoresIdentity() {
        val trusted =
            TrustedReceiver(
                identity = ReceiverIdentity("id", "key"),
                displayName = "NoctDock TV",
                lastHostAddress = "192.168.1.2",
                port = 45454,
                trustedAtMillis = 1,
            )

        assertEquals("id", trusted.identity.id)
        assertEquals(45454, trusted.port)
    }

    @Test
    fun diagnosticsFormattingRedactsInstalledAppsByDefault() {
        val text = DiagnosticsSnapshot(lastError = NoctError.NetworkUnstable).exportText()

        assertTrue(text.contains("NoctDock diagnostics"))
        assertTrue(text.contains("Sound mode: Retroid Sound"))
        assertTrue(text.contains("Background mode: Animated Nebula"))
        assertTrue(text.contains("Reduced motion: false"))
        assertTrue(text.contains("Battery Saver: false"))
        assertTrue(text.contains("Screen Cloak mode: Off"))
        assertTrue(text.contains("Screen Cloak method: NONE"))
        assertTrue(text.contains("Last error: ${NoctError.NetworkUnstable.message}"))
        assertTrue(text.contains("Installed app names included: false"))
        assertFalse(text.contains("com.example"))
    }
}
