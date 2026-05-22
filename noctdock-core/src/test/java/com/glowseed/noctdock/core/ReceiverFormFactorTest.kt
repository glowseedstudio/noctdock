package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiverFormFactorTest {
    @Test
    fun leanbackDevicesRemainTvReceivers() {
        val result =
            ReceiverFormFactorDetector.detect(
                ReceiverDeviceTraits(
                    hasLeanback = true,
                    hasTouchscreen = false,
                    smallestWidthDp = 540,
                ),
            )

        assertEquals(ReceiverFormFactor.TV, result)
    }

    @Test
    fun touchscreenSmallAndLargeDevicesMapToPhoneAndTablet() {
        assertEquals(
            ReceiverFormFactor.PHONE,
            ReceiverFormFactorDetector.detect(
                ReceiverDeviceTraits(hasLeanback = false, hasTouchscreen = true, smallestWidthDp = 411),
            ),
        )
        assertEquals(
            ReceiverFormFactor.TABLET,
            ReceiverFormFactorDetector.detect(
                ReceiverDeviceTraits(hasLeanback = false, hasTouchscreen = true, smallestWidthDp = 700),
            ),
        )
    }

    @Test
    fun fitAndFillPreserveAspectRatio() {
        assertEquals(
            ReceiverViewportSize(width = 1920, height = 1080),
            ReceiverAspectRatio.contentSize(
                containerWidth = 1920,
                containerHeight = 1200,
                videoWidth = 16,
                videoHeight = 9,
                mode = ReceiverScaleMode.FIT,
            ),
        )
        assertEquals(
            ReceiverViewportSize(width = 2133, height = 1200),
            ReceiverAspectRatio.contentSize(
                containerWidth = 1920,
                containerHeight = 1200,
                videoWidth = 16,
                videoHeight = 9,
                mode = ReceiverScaleMode.FILL,
            ),
        )
    }

    @Test
    fun senderWordingCanUseTvOrGenericScreenLabels() {
        assertEquals("TV Ready", ReceiverDisplayWording.screenReady(ReceiverFormFactor.TV))
        assertEquals("Phone Ready", ReceiverDisplayWording.screenReady(ReceiverFormFactor.PHONE))
        assertEquals("Tablet Ready", ReceiverDisplayWording.screenReady(ReceiverFormFactor.TABLET))
        assertEquals("Screen Ready", ReceiverDisplayWording.screenReady(ReceiverFormFactor.UNKNOWN))
        assertEquals("tv", ReceiverDisplayWording.receiverNoun(ReceiverFormFactor.TV))
        assertEquals("phone", ReceiverDisplayWording.receiverNoun(ReceiverFormFactor.PHONE))
        assertEquals("tablet", ReceiverDisplayWording.receiverNoun(ReceiverFormFactor.TABLET))
        assertEquals("screen", ReceiverDisplayWording.receiverNoun(ReceiverFormFactor.UNKNOWN))
        assertEquals("Looking for a screen...", ReceiverDisplayWording.lookingForScreen())
    }
}
