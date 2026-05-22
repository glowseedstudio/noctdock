package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecCompatibilityMessagingTest {
    @Test
    fun usesProfileTitleWhenProvided() {
        assertEquals(
            "Sharp will use compatibility video (AVC) on this connection.",
            CodecCompatibilityMessaging.hevcToAvcFallbackMessage(profileTitle = "Sharp", height = 900),
        )
    }

    @Test
    fun derivesScopeFromHeightWhenTitleMissing() {
        assertTrue(
            CodecCompatibilityMessaging.hevcToAvcFallbackMessage(height = 1080).contains("Full HD"),
        )
        assertTrue(
            CodecCompatibilityMessaging.hevcToAvcFallbackMessage(height = 900).contains("900p"),
        )
    }

    @Test
    fun negotiationWarningUsesProfileTitle() {
        val config =
            StreamNegotiator.negotiate(
                profile = StreamProfiles.Sharp,
                sender =
                SenderVideoCapabilities(
                    supportsHevcEncode = false,
                    maxEncodeWidth = 1920,
                    maxEncodeHeight = 1080,
                ),
                receiver = ReceiverVideoCapabilities(supportsHevc = true, maxWidth = 1920, maxHeight = 1080),
            )

        assertTrue(config.warning?.contains("Sharp") == true)
        assertTrue(config.warning?.contains("AVC") == true)
    }
}
