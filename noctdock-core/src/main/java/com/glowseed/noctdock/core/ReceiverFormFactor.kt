package com.glowseed.noctdock.core

import kotlinx.serialization.Serializable

@Serializable
enum class ReceiverFormFactor {
    TV,
    PHONE,
    TABLET,
    UNKNOWN,
}

@Serializable
enum class ReceiverScaleMode {
    FIT,
    FILL,
}

data class ReceiverDeviceTraits(val hasLeanback: Boolean, val hasTouchscreen: Boolean, val smallestWidthDp: Int)

data class ReceiverViewportSize(val width: Int, val height: Int)

object ReceiverFormFactorDetector {
    fun detect(traits: ReceiverDeviceTraits): ReceiverFormFactor = when {
        traits.hasLeanback -> ReceiverFormFactor.TV
        traits.hasTouchscreen && traits.smallestWidthDp >= 600 -> ReceiverFormFactor.TABLET
        traits.hasTouchscreen -> ReceiverFormFactor.PHONE
        else -> ReceiverFormFactor.UNKNOWN
    }
}

object ReceiverAspectRatio {
    fun contentSize(containerWidth: Int, containerHeight: Int, videoWidth: Int, videoHeight: Int, mode: ReceiverScaleMode): ReceiverViewportSize {
        if (containerWidth <= 0 || containerHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return ReceiverViewportSize(0, 0)
        }
        val widthScale = containerWidth.toDouble() / videoWidth.toDouble()
        val heightScale = containerHeight.toDouble() / videoHeight.toDouble()
        val scale =
            when (mode) {
                ReceiverScaleMode.FIT -> minOf(widthScale, heightScale)
                ReceiverScaleMode.FILL -> maxOf(widthScale, heightScale)
            }
        return ReceiverViewportSize(
            width = (videoWidth * scale).toInt().coerceAtLeast(1),
            height = (videoHeight * scale).toInt().coerceAtLeast(1),
        )
    }
}

object ReceiverDisplayWording {
    fun genericName(formFactor: ReceiverFormFactor): String = when (formFactor) {
        ReceiverFormFactor.TV -> "NoctDock TV"

        ReceiverFormFactor.PHONE,
        ReceiverFormFactor.TABLET,
        ReceiverFormFactor.UNKNOWN,
        -> "NoctDock Receiver"
    }

    fun deviceLabel(formFactor: ReceiverFormFactor): String = when (formFactor) {
        ReceiverFormFactor.TV -> "TV"
        ReceiverFormFactor.PHONE -> "Phone"
        ReceiverFormFactor.TABLET -> "Tablet"
        ReceiverFormFactor.UNKNOWN -> "Screen"
    }

    fun receiverNoun(formFactor: ReceiverFormFactor): String = deviceLabel(formFactor).lowercase()

    fun readyLabel(formFactor: ReceiverFormFactor): String = "${deviceLabel(formFactor)} Ready"

    fun screenReady(formFactor: ReceiverFormFactor): String = readyLabel(formFactor)

    fun lookingForScreen(): String = "Looking for a screen..."
}
