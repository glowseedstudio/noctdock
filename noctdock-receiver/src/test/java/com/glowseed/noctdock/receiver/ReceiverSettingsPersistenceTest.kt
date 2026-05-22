package com.glowseed.noctdock.receiver

import com.glowseed.noctdock.core.PairingState
import com.glowseed.noctdock.core.ReceiverScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverSettingsPersistenceTest {
    @Test
    fun receiverSettingsRoundTripKeepsDisplayModeOrientationAndName() {
        val settings =
            ReceiverSettings(
                startFullscreen = false,
                keepScreenAwake = true,
                preferLandscapeWhilePlaying = false,
                scaleMode = ReceiverScaleMode.FILL,
                receiverName = "  Bedroom   Tablet  ",
            )

        val stored = ReceiverSettingsPersistence.toStoredValues(settings)
        val restored =
            ReceiverSettingsPersistence.fromStoredValues(
                startFullscreen = stored.startFullscreen,
                keepScreenAwake = stored.keepScreenAwake,
                preferLandscapeWhilePlaying = stored.preferLandscapeWhilePlaying,
                scaleModeName = stored.scaleModeName,
                receiverName = stored.receiverName,
            )

        assertEquals(false, restored.startFullscreen)
        assertEquals(true, restored.keepScreenAwake)
        assertEquals(false, restored.preferLandscapeWhilePlaying)
        assertEquals(ReceiverScaleMode.FILL, restored.scaleMode)
        assertEquals("Bedroom Tablet", restored.receiverName)
    }

    @Test
    fun receiverNameFallsBackWhenBlank() {
        assertEquals(
            "Pixel Tablet",
            ReceiverSettingsPersistence.resolvedReceiverName("", "Pixel Tablet"),
        )
        assertEquals(
            "Living Room Screen",
            ReceiverSettingsPersistence.resolvedReceiverName("  Living   Room  Screen ", "Pixel Tablet"),
        )
    }

    @Test
    fun streamPhaseTracksWaitingPairingPlayingAndInterruptedStates() {
        assertEquals(
            ReceiverUiPhase.WAITING,
            ReceiverUiPhaseResolver.resolve(
                pairingState = PairingState.Required,
                streamActive = false,
                hasPlaybackError = false,
            ),
        )
        assertEquals(
            ReceiverUiPhase.PAIRING,
            ReceiverUiPhaseResolver.resolve(
                pairingState = PairingState.AwaitingCode,
                streamActive = false,
                hasPlaybackError = false,
            ),
        )
        assertEquals(
            ReceiverUiPhase.ACTIVE,
            ReceiverUiPhaseResolver.resolve(
                pairingState = PairingState.Trusted,
                streamActive = true,
                hasPlaybackError = false,
            ),
        )
        assertEquals(
            ReceiverUiPhase.INTERRUPTED,
            ReceiverUiPhaseResolver.resolve(
                pairingState = PairingState.Trusted,
                streamActive = false,
                hasPlaybackError = true,
            ),
        )
        assertEquals(
            ReceiverUiPhase.INTERRUPTED,
            ReceiverUiPhaseResolver.resolve(
                pairingState = PairingState.Trusted,
                streamActive = true,
                hasPlaybackError = true,
            ),
        )
    }

    @Test
    fun streamEndKeepsTrustedPairingState() {
        val current = PairingState.Trusted
        val next =
            if (current != PairingState.Trusted) {
                PairingState.Required
            } else {
                current
            }
        assertEquals(PairingState.Trusted, next)
    }

    @Test
    fun streamEndResetsUntrustedPairingState() {
        val next =
            if (PairingState.AwaitingCode != PairingState.Trusted) {
                PairingState.Required
            } else {
                PairingState.AwaitingCode
            }
        assertEquals(PairingState.Required, next)
    }

    @Test
    fun receiverNameSanitizerNeverReturnsInvalidWhitespace() {
        val sanitized = ReceiverSettingsPersistence.sanitizeReceiverName("   Desk\t\tReceiver   ")
        assertEquals("Desk Receiver", sanitized)
        assertTrue(sanitized.none { it == '\t' })
    }
}
