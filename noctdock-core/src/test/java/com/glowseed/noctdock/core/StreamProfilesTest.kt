package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamProfilesTest {
    @Test
    fun presetsExist() {
        assertEquals(
            listOf("performance", "balanced", "quality", "sharp", "cinema"),
            StreamProfiles.visible.map { it.id },
        )
        assertEquals(
            listOf(
                "performance",
                "balanced",
                "quality",
                "sharp",
                "cinema",
                "1080_boost",
                "extreme",
            ),
            StreamProfiles.all.map {
                it.id
            },
        )
    }

    @Test
    fun defaultProfileIsStable720p() {
        assertEquals(StreamProfiles.Performance, StreamProfiles.default)
    }

    @Test
    fun profileValuesAreValid() {
        StreamProfiles.all.forEach { profile ->
            assertTrue(profile.width > 0)
            assertTrue(profile.height > 0)
            assertTrue(profile.fps in 30..120)
            assertTrue(profile.bitrateMbps > 0)
            assertTrue(profile.expectedBandwidthMbps >= profile.bitrateMbps)
        }
    }

    @Test
    fun latencyTuningKeepsPerformanceMostResponsive() {
        assertTrue(
            LatencyTuning.reassemblyTimeoutMs(LatencyPriority.Lowest) <
                LatencyTuning.reassemblyTimeoutMs(LatencyPriority.Balanced),
        )
        assertTrue(
            LatencyTuning.reassemblyTimeoutMs(LatencyPriority.Balanced) <
                LatencyTuning.reassemblyTimeoutMs(LatencyPriority.Quality),
        )
        assertTrue(
            LatencyTuning.audioTargetBufferMs(LatencyPriority.Lowest) <
                LatencyTuning.audioTargetBufferMs(LatencyPriority.Balanced),
        )
        assertTrue(
            LatencyTuning.audioTargetBufferMs(LatencyPriority.Balanced) <
                LatencyTuning.audioTargetBufferMs(LatencyPriority.Quality),
        )
    }

    @Test
    fun highQualityProfilesUseRequestedBitrateFloors() {
        assertEquals(32, StreamProfiles.Cinema.bitrateFor(VideoCodec.HEVC))
        assertEquals(42, StreamProfiles.Cinema.bitrateFor(VideoCodec.AVC))
        assertEquals(24, StreamProfiles.Cinema.adaptiveFloorFor(VideoCodec.HEVC))
        assertEquals(32, StreamProfiles.Cinema.adaptiveFloorFor(VideoCodec.AVC))
        assertEquals(38, StreamProfiles.Cinema.adaptiveCeilingFor(VideoCodec.HEVC))
        assertEquals(50, StreamProfiles.Cinema.adaptiveCeilingFor(VideoCodec.AVC))
    }

    @Test
    fun negotiationPrefersHevcWhenBothSidesSupportIt() {
        val config =
            StreamNegotiator.negotiate(
                profile = StreamProfiles.Cinema,
                sender =
                SenderVideoCapabilities(
                    supportsHevcEncode = true,
                    maxEncodeWidth = 1920,
                    maxEncodeHeight = 1080,
                ),
                receiver = ReceiverVideoCapabilities(supportsHevc = true, maxWidth = 1920, maxHeight = 1080),
            )

        assertEquals(VideoCodec.HEVC, config.codec)
        assertEquals(1920, config.width)
        assertEquals(1080, config.height)
        assertEquals(32, config.bitrateMbps)
    }

    @Test
    fun negotiationClampsResolutionToReceiverAndSenderCaps() {
        val config =
            StreamNegotiator.negotiate(
                profile = StreamProfiles.Cinema,
                sender =
                SenderVideoCapabilities(
                    supportsHevcEncode = true,
                    maxEncodeWidth = 1920,
                    maxEncodeHeight = 1080,
                ),
                receiver =
                ReceiverVideoCapabilities(
                    supportsHevc = true,
                    maxWidth = 1280,
                    maxHeight = 720,
                    maxFps = 60,
                ),
            )

        assertEquals(1280, config.width)
        assertEquals(720, config.height)
        assertEquals(60, config.fps)
        assertTrue(config.warning?.contains("Adjusted picture") == true)
    }

    @Test
    fun negotiationFallsBackToAvcWhenHevcUnavailable() {
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

        assertEquals(VideoCodec.AVC, config.codec)
        assertEquals(30, config.bitrateMbps)
        assertTrue(config.warning != null)
    }
}
