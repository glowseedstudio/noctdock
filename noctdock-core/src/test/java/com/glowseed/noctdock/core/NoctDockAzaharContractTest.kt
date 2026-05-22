package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NoctDockAzaharContractTest {
    @Test
    fun contractUsesCustomAzaharPackage() {
        assertEquals("com.glowseed.noctdock.azahar", NoctDockAzaharContract.PACKAGE_NAME)
        assertEquals("com.glowseed.noctdock.azahar.debug", NoctDockAzaharContract.DEBUG_PACKAGE_NAME)
        assertEquals(
            listOf("com.glowseed.noctdock.azahar", "com.glowseed.noctdock.azahar.debug"),
            NoctDockAzaharContract.PACKAGE_CANDIDATES,
        )
    }

    @Test
    fun launchDetailsExposeIntentValues() {
        val details =
            NoctDockAzaharLaunchDetails(
                receiverName = "Living Room",
                receiverAddress = "192.168.1.20",
                receiverPort = 45454,
                preferredCodec = VideoCodec.HEVC,
                soundMode = SoundMode.RETROID,
            )

        assertEquals("THREE_DS_TOP_SCREEN", details.mode)
        assertEquals("hevc", details.preferredCodecValue)
        assertEquals("retroid", details.soundModeValue)
        assertEquals(true, details.promptUser)
    }

    @Test
    fun launchRequiresAzaharPackage() {
        assertEquals(
            NoctDockAzaharPreflightResult.AZAHAR_MISSING,
            NoctDockAzaharContract.preflight(
                azaharInstalled = false,
                receiverSelected = true,
                receiverOnline = true,
                receiverTrusted = true,
            ),
        )
    }

    @Test
    fun launchIn3dsModeRequiresReceiver() {
        assertEquals(
            NoctDockAzaharPreflightResult.RECEIVER_REQUIRED,
            NoctDockAzaharContract.preflight(
                azaharInstalled = true,
                receiverSelected = false,
                receiverOnline = false,
                receiverTrusted = false,
            ),
        )
    }

    @Test
    fun launchIn3dsModeRequiresReadyTrustedReceiver() {
        assertEquals(
            NoctDockAzaharPreflightResult.RECEIVER_NOT_READY,
            NoctDockAzaharContract.preflight(
                azaharInstalled = true,
                receiverSelected = true,
                receiverOnline = false,
                receiverTrusted = true,
            ),
        )
        assertEquals(
            NoctDockAzaharPreflightResult.RECEIVER_NOT_TRUSTED,
            NoctDockAzaharContract.preflight(
                azaharInstalled = true,
                receiverSelected = true,
                receiverOnline = true,
                receiverTrusted = false,
            ),
        )
    }

    @Test
    fun resolvePreferredCodecMatchesNegotiationWhenHevcAllowed() {
        val codec =
            NoctDockAzaharContract.resolvePreferredCodec(
                profile = StreamProfiles.Cinema,
                sender =
                SenderVideoCapabilities(
                    supportsHevcEncode = true,
                    maxEncodeWidth = 1920,
                    maxEncodeHeight = 1080,
                ),
                receiver = ReceiverVideoCapabilities(supportsHevc = true, maxWidth = 1920, maxHeight = 1080),
                hevcAllowedByDevice = true,
            )
        assertEquals(VideoCodec.HEVC, codec)
    }

    @Test
    fun resolvePreferredCodecFallsBackToAvcWithoutSenderHevc() {
        val codec =
            NoctDockAzaharContract.resolvePreferredCodec(
                profile = StreamProfiles.Cinema,
                sender =
                SenderVideoCapabilities(
                    supportsHevcEncode = false,
                    maxEncodeWidth = 1920,
                    maxEncodeHeight = 1080,
                ),
                receiver = ReceiverVideoCapabilities(supportsHevc = true, maxWidth = 1920, maxHeight = 1080),
                hevcAllowedByDevice = true,
            )
        assertEquals(VideoCodec.AVC, codec)
    }

    @Test
    fun resolvePreferredCodecFallsBackToAvcWhenDevicePolicyBlocksHevc() {
        val codec =
            NoctDockAzaharContract.resolvePreferredCodec(
                profile = StreamProfiles.Sharp,
                sender =
                SenderVideoCapabilities(
                    supportsHevcEncode = true,
                    maxEncodeWidth = 1920,
                    maxEncodeHeight = 1080,
                ),
                receiver = ReceiverVideoCapabilities(supportsHevc = true, maxWidth = 1920, maxHeight = 1080),
                hevcAllowedByDevice = false,
            )
        assertEquals(VideoCodec.AVC, codec)
    }

    @Test
    fun resolvePreferredCodecUsesAvcForBalancedProfile() {
        val codec =
            NoctDockAzaharContract.resolvePreferredCodec(
                profile = StreamProfiles.Balanced,
                sender =
                SenderVideoCapabilities(
                    supportsHevcEncode = true,
                    maxEncodeWidth = 1920,
                    maxEncodeHeight = 1080,
                ),
                receiver = ReceiverVideoCapabilities(supportsHevc = true, maxWidth = 1920, maxHeight = 1080),
                hevcAllowedByDevice = true,
            )
        assertEquals(VideoCodec.AVC, codec)
    }

    @Test
    fun readyPreflightDoesNotRequireNormalConsoleMode() {
        assertEquals(
            NoctDockAzaharPreflightResult.READY,
            NoctDockAzaharContract.preflight(
                azaharInstalled = true,
                receiverSelected = true,
                receiverOnline = true,
                receiverTrusted = true,
            ),
        )
    }
}
