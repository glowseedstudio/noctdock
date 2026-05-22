package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevcFallbackPolicyTest {
    @Test
    fun streakResetsWhenNoDecoderErrors() {
        assertEquals(0, HevcFallbackPolicy.nextStreak(2, 0))
        assertEquals(1, HevcFallbackPolicy.nextStreak(0, 1))
        assertEquals(3, HevcFallbackPolicy.nextStreak(2, 1))
    }

    @Test
    fun fallbackRequiresHevcAndStreakThreshold() {
        assertFalse(
            HevcFallbackPolicy.shouldFallback(
                activeCodecMime = VideoCodec.AVC.mime,
                decoderErrorStreak = 3,
                fallbackAlreadyPerformed = false,
            ),
        )
        assertFalse(
            HevcFallbackPolicy.shouldFallback(
                activeCodecMime = VideoCodec.HEVC.mime,
                decoderErrorStreak = 2,
                fallbackAlreadyPerformed = false,
            ),
        )
        assertTrue(
            HevcFallbackPolicy.shouldFallback(
                activeCodecMime = VideoCodec.HEVC.mime,
                decoderErrorStreak = 3,
                fallbackAlreadyPerformed = false,
            ),
        )
        assertFalse(
            HevcFallbackPolicy.shouldFallback(
                activeCodecMime = VideoCodec.HEVC.mime,
                decoderErrorStreak = 5,
                fallbackAlreadyPerformed = true,
            ),
        )
    }
}
