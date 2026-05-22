package com.glowseed.noctdock.core

import kotlinx.serialization.Serializable

@Serializable
enum class VideoCodec(val mime: String, val friendlyName: String) {
    AVC("video/avc", "AVC"),
    HEVC("video/hevc", "HEVC"),
    ;

    companion object {
        fun fromMime(mime: String): VideoCodec = entries.firstOrNull { it.mime.equals(mime, ignoreCase = true) } ?: AVC
    }
}

@Serializable
enum class CodecPreference {
    AVC_ONLY,
    HEVC_PREFERRED,
}

@Serializable
data class VideoProfileLevel(val profile: Int = 0, val level: Int = 0)

@Serializable
data class ReceiverVideoCapabilities(
    val supportsAvc: Boolean = true,
    val supportsHevc: Boolean = false,
    val maxWidth: Int = 1280,
    val maxHeight: Int = 720,
    val maxFps: Int = 60,
    val preferredCodec: VideoCodec = VideoCodec.AVC,
    val decoderName: String = "Unknown",
    val lowLatencyDecodeSupported: Boolean = false,
    val receiverModel: String = "Android TV",
    val shieldOptimized: Boolean = false,
) {
    fun supports(profile: StreamProfile, codec: VideoCodec): Boolean = profile.width <= maxWidth &&
        profile.height <= maxHeight &&
        profile.fps <= maxFps &&
        when (codec) {
            VideoCodec.AVC -> supportsAvc
            VideoCodec.HEVC -> supportsHevc
        }
}

@Serializable
data class SenderVideoCapabilities(
    val supportsAvcEncode: Boolean = true,
    val supportsHevcEncode: Boolean = false,
    val avcEncoderName: String = "Unknown",
    val hevcEncoderName: String = "Unknown",
    val maxEncodeWidth: Int = 1280,
    val maxEncodeHeight: Int = 720,
    val minBitrateMbps: Int = 4,
    val maxBitrateMbps: Int = 22,
    val lowLatencyEncodeSupported: Boolean = false,
) {
    fun supports(profile: StreamProfile, codec: VideoCodec): Boolean = profile.width <= maxEncodeWidth &&
        profile.height <= maxEncodeHeight &&
        when (codec) {
            VideoCodec.AVC -> supportsAvcEncode
            VideoCodec.HEVC -> supportsHevcEncode
        }
}

@Serializable
data class NegotiatedStreamConfig(
    val profileId: String,
    val codec: VideoCodec,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateMbps: Int,
    val adaptiveFloorMbps: Int,
    val adaptiveCeilingMbps: Int,
    val warning: String? = null,
) {
    val bitrate: Int get() = bitrateMbps * 1_000_000
}

object CodecCompatibilityMessaging {
    fun hevcToAvcFallbackMessage(profileTitle: String? = null, height: Int = 720): String {
        val scope =
            profileTitle?.takeIf { it.isNotBlank() }
                ?: when {
                    height >= 1080 -> "Full HD"
                    height >= 900 -> "900p"
                    else -> "720p"
                }
        return "$scope will use compatibility video (AVC) on this connection."
    }
}

object StreamNegotiator {
    fun negotiate(profile: StreamProfile, sender: SenderVideoCapabilities, receiver: ReceiverVideoCapabilities): NegotiatedStreamConfig {
        val preferredCodec =
            if (
                profile.codecPreference == CodecPreference.HEVC_PREFERRED &&
                sender.supports(profile, VideoCodec.HEVC) &&
                receiver.supports(profile, VideoCodec.HEVC)
            ) {
                VideoCodec.HEVC
            } else {
                VideoCodec.AVC
            }
        val fallbackWarning =
            if (profile.codecPreference == CodecPreference.HEVC_PREFERRED && preferredCodec == VideoCodec.AVC) {
                CodecCompatibilityMessaging.hevcToAvcFallbackMessage(
                    profileTitle = profile.title,
                    height = profile.height,
                )
            } else {
                null
            }
        val (width, height, fps) = clampDimensions(profile, sender, receiver, preferredCodec)
        val clampWarning =
            if (width != profile.width || height != profile.height || fps != profile.fps) {
                "Adjusted picture to ${width}x$height at ${fps}fps for this screen."
            } else {
                null
            }
        val warning =
            listOfNotNull(fallbackWarning, clampWarning)
                .joinToString(" ")
                .ifBlank { null }
        val bitrate = profile.bitrateFor(preferredCodec)
        val floor = profile.adaptiveFloorFor(preferredCodec)
        val ceiling = profile.adaptiveCeilingFor(preferredCodec)
        return NegotiatedStreamConfig(
            profileId = profile.id,
            codec = preferredCodec,
            width = width,
            height = height,
            fps = fps,
            bitrateMbps = bitrate,
            adaptiveFloorMbps = floor,
            adaptiveCeilingMbps = ceiling,
            warning = warning,
        )
    }

    internal fun clampDimensions(profile: StreamProfile, sender: SenderVideoCapabilities, receiver: ReceiverVideoCapabilities, codec: VideoCodec): Triple<Int, Int, Int> {
        val maxWidth = minOf(sender.maxEncodeWidth, receiver.maxWidth)
        val maxHeight = minOf(sender.maxEncodeHeight, receiver.maxHeight)
        val maxFps = minOf(profile.fps, receiver.maxFps).coerceAtLeast(30)
        var width = profile.width.coerceAtMost(maxWidth)
        var height = profile.height.coerceAtMost(maxHeight)
        if (width < profile.width || height < profile.height) {
            val scale = minOf(maxWidth.toDouble() / profile.width, maxHeight.toDouble() / profile.height)
            width = evenDimension((profile.width * scale).toInt().coerceIn(320, maxWidth))
            height = evenDimension((profile.height * scale).toInt().coerceIn(240, maxHeight))
        }
        width = evenDimension(width)
        height = evenDimension(height)
        while (!sender.supportsDimensions(width, height, maxFps, codec) ||
            !receiver.supportsDimensions(width, height, maxFps, codec)
        ) {
            width = evenDimension((width * 9) / 10)
            height = evenDimension((height * 9) / 10)
            if (width < 640 || height < 360) break
        }
        return Triple(width, height, maxFps)
    }

    private fun evenDimension(value: Int): Int = (value.coerceAtLeast(2)) and -2
}

private fun SenderVideoCapabilities.supportsDimensions(width: Int, height: Int, fps: Int, codec: VideoCodec): Boolean = width <= maxEncodeWidth &&
    height <= maxEncodeHeight &&
    when (codec) {
        VideoCodec.AVC -> supportsAvcEncode
        VideoCodec.HEVC -> supportsHevcEncode
    }

private fun ReceiverVideoCapabilities.supportsDimensions(width: Int, height: Int, fps: Int, codec: VideoCodec): Boolean = width <= maxWidth &&
    height <= maxHeight &&
    fps <= maxFps &&
    when (codec) {
        VideoCodec.AVC -> supportsAvc
        VideoCodec.HEVC -> supportsHevc
    }

@Serializable
data class StreamProfile(
    val id: String,
    val title: String,
    val summary: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateMbps: Int,
    val latencyPriority: LatencyPriority,
    val expectedLatencyMs: Int,
    val expectedBandwidthMbps: Int,
    val thermalImpact: ImpactEstimate,
    val batteryImpact: ImpactEstimate,
    val frameDropStrategy: FrameDropStrategy,
    val codecPreference: CodecPreference = CodecPreference.AVC_ONLY,
    val avcBitrateMbps: Int = bitrateMbps,
    val hevcBitrateMbps: Int? = null,
    val adaptiveFloorMbps: Int = (bitrateMbps * 3 / 4).coerceAtLeast(4),
    val adaptiveCeilingMbps: Int = bitrateMbps,
    val avcAdaptiveFloorMbps: Int = adaptiveFloorMbps,
    val avcAdaptiveCeilingMbps: Int = adaptiveCeilingMbps,
    val hevcAdaptiveFloorMbps: Int = adaptiveFloorMbps,
    val hevcAdaptiveCeilingMbps: Int = adaptiveCeilingMbps,
    val hidden: Boolean = false,
) {
    val resolutionLabel: String = "${width}p".takeIf { width < height } ?: "${height}p"

    fun bitrateFor(codec: VideoCodec): Int = when (codec) {
        VideoCodec.AVC -> avcBitrateMbps
        VideoCodec.HEVC -> hevcBitrateMbps ?: avcBitrateMbps
    }

    fun adaptiveFloorFor(codec: VideoCodec): Int = when (codec) {
        VideoCodec.AVC -> avcAdaptiveFloorMbps
        VideoCodec.HEVC -> hevcBitrateMbps?.let { hevcAdaptiveFloorMbps } ?: avcAdaptiveFloorMbps
    }

    fun adaptiveCeilingFor(codec: VideoCodec): Int = when (codec) {
        VideoCodec.AVC -> avcAdaptiveCeilingMbps
        VideoCodec.HEVC -> hevcBitrateMbps?.let { hevcAdaptiveCeilingMbps } ?: avcAdaptiveCeilingMbps
    }
}

@Serializable
enum class LatencyPriority {
    Lowest,
    Balanced,
    Quality,
}

@Serializable
enum class ImpactEstimate {
    Low,
    Medium,
    High,
    Extreme,
}

@Serializable
enum class FrameDropStrategy {
    AggressiveLatest,
    Balanced,
    QualityBiased,
}

/** Built-in Console Mode quality presets used by sender UI, negotiation, and adaptive bitrate floors. */
object StreamProfiles {
    val Performance =
        StreamProfile(
            id = "performance",
            title = "Performance",
            summary = "Smooth and reliable.",
            width = 1280,
            height = 720,
            fps = 60,
            bitrateMbps = 12,
            latencyPriority = LatencyPriority.Lowest,
            expectedLatencyMs = 45,
            expectedBandwidthMbps = 14,
            thermalImpact = ImpactEstimate.Medium,
            batteryImpact = ImpactEstimate.Medium,
            frameDropStrategy = FrameDropStrategy.AggressiveLatest,
            adaptiveFloorMbps = 8,
            adaptiveCeilingMbps = 14,
        )

    val Balanced =
        StreamProfile(
            id = "balanced",
            title = "Balanced",
            summary = "Recommended for most sessions.",
            width = 1280,
            height = 720,
            fps = 60,
            bitrateMbps = 18,
            latencyPriority = LatencyPriority.Balanced,
            expectedLatencyMs = 55,
            expectedBandwidthMbps = 20,
            thermalImpact = ImpactEstimate.Medium,
            batteryImpact = ImpactEstimate.Medium,
            frameDropStrategy = FrameDropStrategy.Balanced,
            adaptiveFloorMbps = 12,
            adaptiveCeilingMbps = 18,
        )

    val Quality =
        StreamProfile(
            id = "quality",
            title = "Quality",
            summary = "Cleaner picture at 720p.",
            width = 1280,
            height = 720,
            fps = 60,
            bitrateMbps = 22,
            latencyPriority = LatencyPriority.Balanced,
            expectedLatencyMs = 75,
            expectedBandwidthMbps = 25,
            thermalImpact = ImpactEstimate.High,
            batteryImpact = ImpactEstimate.High,
            frameDropStrategy = FrameDropStrategy.QualityBiased,
            adaptiveFloorMbps = 16,
            adaptiveCeilingMbps = 22,
        )

    val Sharp =
        StreamProfile(
            id = "sharp",
            title = "Sharp",
            summary = "Sharper image for strong handhelds.",
            width = 1600,
            height = 900,
            fps = 60,
            bitrateMbps = 24,
            latencyPriority = LatencyPriority.Balanced,
            expectedLatencyMs = 80,
            expectedBandwidthMbps = 32,
            thermalImpact = ImpactEstimate.High,
            batteryImpact = ImpactEstimate.High,
            frameDropStrategy = FrameDropStrategy.QualityBiased,
            codecPreference = CodecPreference.HEVC_PREFERRED,
            avcBitrateMbps = 30,
            hevcBitrateMbps = 24,
            avcAdaptiveFloorMbps = 22,
            avcAdaptiveCeilingMbps = 34,
            hevcAdaptiveFloorMbps = 18,
            hevcAdaptiveCeilingMbps = 30,
        )

    val Cinema =
        StreamProfile(
            id = "cinema",
            title = "Cinema",
            summary = "Full HD for excellent Wi-Fi.",
            width = 1920,
            height = 1080,
            fps = 60,
            bitrateMbps = 32,
            latencyPriority = LatencyPriority.Quality,
            expectedLatencyMs = 90,
            expectedBandwidthMbps = 45,
            thermalImpact = ImpactEstimate.High,
            batteryImpact = ImpactEstimate.High,
            frameDropStrategy = FrameDropStrategy.QualityBiased,
            codecPreference = CodecPreference.HEVC_PREFERRED,
            avcBitrateMbps = 42,
            hevcBitrateMbps = 32,
            avcAdaptiveFloorMbps = 32,
            avcAdaptiveCeilingMbps = 50,
            hevcAdaptiveFloorMbps = 24,
            hevcAdaptiveCeilingMbps = 38,
        )

    val Boost1080 =
        StreamProfile(
            id = "1080_boost",
            title = "1080 Boost",
            summary = "Compatibility test mode.",
            width = 1920,
            height = 1080,
            fps = 60,
            bitrateMbps = 50,
            latencyPriority = LatencyPriority.Quality,
            expectedLatencyMs = 95,
            expectedBandwidthMbps = 55,
            thermalImpact = ImpactEstimate.Extreme,
            batteryImpact = ImpactEstimate.Extreme,
            frameDropStrategy = FrameDropStrategy.QualityBiased,
            avcBitrateMbps = 50,
            avcAdaptiveFloorMbps = 38,
            avcAdaptiveCeilingMbps = 55,
            hidden = true,
        )

    val Extreme =
        StreamProfile(
            id = "extreme",
            title = "Extreme",
            summary = "Experimental.",
            width = 1920,
            height = 1080,
            fps = 60,
            bitrateMbps = 40,
            latencyPriority = LatencyPriority.Quality,
            expectedLatencyMs = 110,
            expectedBandwidthMbps = 60,
            thermalImpact = ImpactEstimate.Extreme,
            batteryImpact = ImpactEstimate.Extreme,
            frameDropStrategy = FrameDropStrategy.QualityBiased,
            codecPreference = CodecPreference.HEVC_PREFERRED,
            avcBitrateMbps = 55,
            hevcBitrateMbps = 40,
            avcAdaptiveFloorMbps = 40,
            avcAdaptiveCeilingMbps = 55,
            hevcAdaptiveFloorMbps = 32,
            hevcAdaptiveCeilingMbps = 45,
            hidden = true,
        )

    val visible = listOf(Performance, Balanced, Quality, Sharp, Cinema)
    val all = visible + Boost1080 + Extreme
    val default = Performance
}
