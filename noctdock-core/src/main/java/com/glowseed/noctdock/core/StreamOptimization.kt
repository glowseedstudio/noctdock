package com.glowseed.noctdock.core

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
data class StreamQualityConfig(
    val adaptiveBitrateEnabled: Boolean = true,
    val batterySaverMode: Boolean = false,
    val packetPacingEnabled: Boolean = true,
    val thermalProtectionEnabled: Boolean = true,
    val diagnosticsVerbose: Boolean = false,
    val reconnectBaseDelayMs: Long = 300,
    val reconnectMaxDelayMs: Long = 5_000,
    val heartbeatTimeoutMs: Long = 1_500,
)

/** Bounded reassembly, audio buffer, and reconnect timings keyed by [LatencyPriority]. */
object LatencyTuning {
    fun reassemblyTimeoutMs(priority: LatencyPriority): Long = when (priority) {
        LatencyPriority.Lowest -> 60L
        LatencyPriority.Balanced -> 140L
        LatencyPriority.Quality -> 200L
    }

    fun reassemblyWindowFrames(priority: LatencyPriority): Int = when (priority) {
        LatencyPriority.Lowest -> 6
        LatencyPriority.Balanced -> 10
        LatencyPriority.Quality -> 14
    }

    fun audioTargetBufferMs(priority: LatencyPriority): Int = when (priority) {
        LatencyPriority.Lowest -> 24
        LatencyPriority.Balanced -> 32
        LatencyPriority.Quality -> 42
    }

    fun audioMaxBufferMs(priority: LatencyPriority): Int = when (priority) {
        LatencyPriority.Lowest -> 48
        LatencyPriority.Balanced -> 64
        LatencyPriority.Quality -> 84
    }

    fun audioMaxLateMs(priority: LatencyPriority): Int = when (priority) {
        LatencyPriority.Lowest -> 24
        LatencyPriority.Balanced -> 36
        LatencyPriority.Quality -> 55
    }
}

@Serializable
data class StreamHealth(
    val packetLossPercent: Int,
    val jitterMs: Int,
    val queueDepth: Int,
    val droppedFramesPerMinute: Int,
    val receiverDecodeErrors: Int,
    val thermalThrottling: Boolean,
    val encoderOverloaded: Boolean,
) {
    val unstable: Boolean =
        packetLossPercent >= 4 ||
            jitterMs >= 35 ||
            queueDepth >= 4 ||
            droppedFramesPerMinute >= 90 ||
            receiverDecodeErrors > 0 ||
            thermalThrottling ||
            encoderOverloaded

    val excellent: Boolean =
        packetLossPercent == 0 &&
            jitterMs <= 10 &&
            queueDepth <= 1 &&
            droppedFramesPerMinute <= 5 &&
            receiverDecodeErrors == 0 &&
            !thermalThrottling &&
            !encoderOverloaded
}

@Serializable
enum class StreamHealthGrade {
    Excellent,
    Stable,
    Unstable,
    Critical,
}

object StreamHealthCalculator {
    fun grade(health: StreamHealth): StreamHealthGrade = when {
        health.packetLossPercent >= 10 || health.receiverDecodeErrors >= 3 || health.thermalThrottling -> StreamHealthGrade.Critical
        health.unstable -> StreamHealthGrade.Unstable
        health.excellent -> StreamHealthGrade.Excellent
        else -> StreamHealthGrade.Stable
    }
}

@Serializable
enum class ConnectionRecommendation {
    PERFORMANCE,
    BALANCED,
    QUALITY,
    SHARP,
    CINEMA,
}

@Serializable
enum class ConnectionStabilityRating {
    Excellent,
    Good,
    Fair,
    Poor,
}

@Serializable
enum class ReceiverTransportKind {
    UNKNOWN,
    WIFI,
    ETHERNET,
}

@Serializable
data class ConnectionTestResult(
    val throughputMbps: Int,
    val packetLossPercent: Int,
    val jitterMs: Int,
    val roundTripMs: Int,
    val receiverResponsive: Boolean,
    val receiverTransport: ReceiverTransportKind = ReceiverTransportKind.UNKNOWN,
    val testedAtMillis: Long = System.currentTimeMillis(),
    val overrideRecommendation: ConnectionRecommendation? = null,
) {
    val stability: ConnectionStabilityRating =
        when {
            !receiverResponsive || packetLossPercent >= 5 || jitterMs >= 20 || throughputMbps < 14 -> ConnectionStabilityRating.Poor
            packetLossPercent >= 2 || jitterMs >= 12 || throughputMbps < 20 -> ConnectionStabilityRating.Fair
            packetLossPercent <= 1 && jitterMs <= 10 && throughputMbps >= 22 -> ConnectionStabilityRating.Good
            packetLossPercent == 0 && jitterMs <= 6 && roundTripMs <= 12 && throughputMbps >= 35 -> ConnectionStabilityRating.Excellent
            else -> ConnectionStabilityRating.Fair
        }

    val recommendation: ConnectionRecommendation =
        overrideRecommendation ?: when {
            stability == ConnectionStabilityRating.Poor -> ConnectionRecommendation.PERFORMANCE
            stability == ConnectionStabilityRating.Fair -> ConnectionRecommendation.BALANCED
            throughputMbps >= 48 && packetLossPercent == 0 && jitterMs <= 8 && roundTripMs <= 12 -> ConnectionRecommendation.CINEMA
            stability == ConnectionStabilityRating.Good -> ConnectionRecommendation.QUALITY
            else -> ConnectionRecommendation.SHARP
        }

    val friendlyLabel: String =
        when (recommendation) {
            ConnectionRecommendation.PERFORMANCE -> "Performance Mode recommended"
            ConnectionRecommendation.BALANCED -> "Balanced Mode recommended"
            ConnectionRecommendation.QUALITY -> "Quality Mode recommended"
            ConnectionRecommendation.SHARP -> "Sharp Mode recommended"
            ConnectionRecommendation.CINEMA -> "Cinema Mode recommended"
        }

    val explanation: String =
        when (recommendation) {
            ConnectionRecommendation.PERFORMANCE -> "Your connection is struggling. Move closer to Wi-Fi or use Ethernet."
            ConnectionRecommendation.BALANCED -> "Your connection is stable, but Performance Mode may feel smoother."
            ConnectionRecommendation.QUALITY -> "Your connection is strong enough for a cleaner picture."
            ConnectionRecommendation.SHARP -> "Your connection is strong enough for a sharper image."
            ConnectionRecommendation.CINEMA -> "Your connection looks excellent for Full HD play."
        }

    fun isStale(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis - testedAtMillis > 24L * 60L * 60L * 1000L
}

data class ConnectionTestStage(val stageIndex: Int, val targetMbps: Int, val payloadSizeBytes: Int = 1200, val durationMs: Long = 650L)

data class ConnectionTestStageSummary(
    val stage: ConnectionTestStage,
    val expectedPackets: Int,
    val receivedPackets: Int,
    val missingPackets: Int,
    val jitterMs: Int,
    val roundTripMs: Int,
    val receiverTransport: ReceiverTransportKind,
) {
    val lossPercent: Int = if (expectedPackets <= 0) 100 else ((missingPackets * 100) / expectedPackets).coerceIn(0, 100)
    val effectiveMbps: Int =
        if (stage.durationMs <= 0L) {
            0
        } else {
            val bitsPerSecond =
                (receivedPackets.toLong() * stage.payloadSizeBytes.toLong() * 8L * 1000L) /
                    stage.durationMs
            (bitsPerSecond / 1_000_000L).toInt().coerceAtLeast(0)
        }
}

object ConnectionTestEvaluator {
    fun rates(includeSharpTier: Boolean, includeFullHdTier: Boolean): List<ConnectionTestStage> {
        val stages = mutableListOf(12, 18, 22, 28)
        if (includeSharpTier) stages += 35
        if (includeFullHdTier) stages += 50
        return stages.distinct().mapIndexed { index, mbps -> ConnectionTestStage(index, mbps) }
    }

    fun summarize(stages: List<ConnectionTestStageSummary>): ConnectionTestResult {
        if (stages.isEmpty()) {
            return ConnectionTestResult(0, 100, 999, 999, receiverResponsive = false)
        }
        val stableStages =
            stages.filter {
                it.lossPercent <= 2 &&
                    it.jitterMs <= 14 &&
                    it.receivedPackets > it.expectedPackets / 2
            }
        val best = stableStages.maxByOrNull { it.effectiveMbps } ?: stages.maxByOrNull { it.receivedPackets }!!
        val avgLoss = stages.map { it.lossPercent }.average().toInt()
        val avgJitter = stages.map { it.jitterMs }.average().toInt()
        val avgRtt = stages.map { it.roundTripMs }.average().toInt()
        return ConnectionTestResult(
            throughputMbps = best.effectiveMbps,
            packetLossPercent = avgLoss.coerceIn(0, 100),
            jitterMs = avgJitter.coerceAtLeast(0),
            roundTripMs = avgRtt.coerceAtLeast(0),
            receiverResponsive = stages.any { it.receivedPackets > 0 },
            receiverTransport = best.receiverTransport,
        )
    }

    fun allowAutoRecommendExperimental(result: ConnectionTestResult, experimentalEnabled: Boolean): Boolean = experimentalEnabled &&
        result.recommendation == ConnectionRecommendation.CINEMA &&
        result.packetLossPercent == 0 &&
        result.jitterMs <= 4
}

class AdaptiveBitrateController(
    private val minBitrateMbps: Int = 4,
    private val maxBitrateMbps: Int = 35,
    private val stepDownMbps: Int = 2,
    private val stepUpMbps: Int = 1,
    private val stableSamplesBeforeIncrease: Int = 2,
) {
    private var stableSamples = 0

    fun nextBitrate(currentBitrateMbps: Int, health: StreamHealth, config: StreamQualityConfig): Int {
        if (!config.adaptiveBitrateEnabled) return currentBitrateMbps
        val cappedMax = if (config.batterySaverMode) min(maxBitrateMbps, 12) else maxBitrateMbps
        if (health.unstable) {
            stableSamples = 0
            return max(minBitrateMbps, currentBitrateMbps - stepDownMbps)
        }
        stableSamples += if (health.excellent) 1 else 0
        return if (stableSamples >= stableSamplesBeforeIncrease) {
            stableSamples = 0
            min(cappedMax, currentBitrateMbps + stepUpMbps)
        } else {
            currentBitrateMbps.coerceIn(minBitrateMbps, cappedMax)
        }
    }
}

class ProfileAdaptiveBitrateController(
    private val floorMbps: Int,
    private val ceilingMbps: Int,
    private val severeFloorMbps: Int = 8,
    private val stepDownMbps: Int = 2,
    private val stepUpMbps: Int = 1,
    private val stableSamplesBeforeIncrease: Int = 8,
) {
    private var stableSamples = 0

    fun nextBitrate(currentBitrateMbps: Int, health: StreamHealth, config: StreamQualityConfig): Int {
        if (!config.adaptiveBitrateEnabled) return currentBitrateMbps
        val cappedCeiling = if (config.batterySaverMode) min(ceilingMbps, 12) else ceilingMbps
        val severe = health.packetLossPercent >= 10 || health.receiverDecodeErrors >= 3 || health.thermalThrottling
        if (health.unstable) {
            stableSamples = 0
            val floor = if (severe) min(floorMbps, severeFloorMbps) else floorMbps
            return max(floor, currentBitrateMbps - stepDownMbps)
        }
        stableSamples += if (health.excellent) 1 else 0
        return if (stableSamples >= stableSamplesBeforeIncrease) {
            stableSamples = 0
            min(cappedCeiling, currentBitrateMbps + stepUpMbps)
        } else {
            currentBitrateMbps.coerceIn(min(floorMbps, cappedCeiling), cappedCeiling)
        }
    }
}

data class ReceiverFeedbackDelta(val reassemblyDrops: Int, val decoderErrors: Int, val audioUnderruns: Int, val audioDrops: Int)

object HevcFallbackPolicy {
    const val DECODER_ERROR_STREAK_THRESHOLD = 3

    fun nextStreak(currentStreak: Int, decoderErrorDelta: Int): Int = if (decoderErrorDelta > 0) currentStreak + 1 else 0

    fun shouldFallback(activeCodecMime: String, decoderErrorStreak: Int, fallbackAlreadyPerformed: Boolean): Boolean = !fallbackAlreadyPerformed &&
        activeCodecMime.equals(VideoCodec.HEVC.mime, ignoreCase = true) &&
        decoderErrorStreak >= DECODER_ERROR_STREAK_THRESHOLD
}

class ReceiverFeedbackDeltaTracker {
    private var lastReassemblyDrops = 0
    private var lastDecoderErrors = 0
    private var lastAudioUnderruns = 0
    private var lastAudioDrops = 0

    fun update(feedback: ReceiverFeedbackPacket): ReceiverFeedbackDelta {
        val delta =
            ReceiverFeedbackDelta(
                reassemblyDrops = (feedback.reassemblyDrops - lastReassemblyDrops).coerceAtLeast(0),
                decoderErrors = (feedback.decoderErrors - lastDecoderErrors).coerceAtLeast(0),
                audioUnderruns = (feedback.audioUnderruns - lastAudioUnderruns).coerceAtLeast(0),
                audioDrops = (feedback.audioDrops - lastAudioDrops).coerceAtLeast(0),
            )
        lastReassemblyDrops = feedback.reassemblyDrops
        lastDecoderErrors = feedback.decoderErrors
        lastAudioUnderruns = feedback.audioUnderruns
        lastAudioDrops = feedback.audioDrops
        return delta
    }
}

object PacketPacingPolicy {
    private const val NANOS_PER_SECOND = 1_000_000_000L

    fun packetIntervalNs(packetBytes: Int, bitrateMbps: Int): Long {
        if (packetBytes <= 0 || bitrateMbps <= 0) return 0L
        val bitsPerSecond = bitrateMbps.toLong() * 1_000_000L
        return ((packetBytes.toLong() * 8L * NANOS_PER_SECOND) / bitsPerSecond).coerceAtLeast(0L)
    }

    fun frameBudgetNs(priority: LatencyPriority, keyFrame: Boolean): Long {
        val baseMs =
            when (priority) {
                LatencyPriority.Lowest -> 1L
                LatencyPriority.Balanced -> 2L
                LatencyPriority.Quality -> 4L
            }
        val keyFrameBoostMs =
            when (priority) {
                LatencyPriority.Lowest -> 1L
                LatencyPriority.Balanced -> 2L
                LatencyPriority.Quality -> 3L
            }
        return (baseMs + if (keyFrame) keyFrameBoostMs else 0L) * 1_000_000L
    }
}

class PacketPacer(private val minimumDelayNs: Long = 250_000L) {
    private var accumulatedDelayNs = 0L

    fun delayForPacketNs(packetBytes: Int, bitrateMbps: Int, remainingFrameBudgetNs: Long): Long {
        if (remainingFrameBudgetNs <= 0L) return 0L
        accumulatedDelayNs =
            (accumulatedDelayNs + PacketPacingPolicy.packetIntervalNs(packetBytes, bitrateMbps))
                .coerceAtMost(remainingFrameBudgetNs)
        if (accumulatedDelayNs < minimumDelayNs) return 0L
        val delayNs = min(accumulatedDelayNs, remainingFrameBudgetNs)
        accumulatedDelayNs -= delayNs
        return delayNs
    }

    fun reset() {
        accumulatedDelayNs = 0L
    }
}

@Serializable
data class EncodedFrame(val frameId: Long, val presentationTimeUs: Long, val keyFrame: Boolean, val bytes: Int)

class LatestFrameQueue(private val capacity: Int) {
    private val frames = ArrayDeque<EncodedFrame>(capacity)
    var droppedFrames: Int = 0
        private set

    fun offer(frame: EncodedFrame) {
        while (frames.size >= capacity) {
            frames.removeFirst()
            droppedFrames += 1
        }
        frames.addLast(frame)
    }

    fun poll(): EncodedFrame? = frames.removeFirstOrNull()

    fun snapshot(): List<EncodedFrame> = frames.toList()
}

@Serializable
data class UdpPacketHeader(val streamId: Int, val frameId: Long, val packetIndex: Int, val packetCount: Int, val keyFrame: Boolean, val presentationTimeUs: Long)

object UdpPacketizer {
    const val MAX_PAYLOAD_BYTES = 1_180

    fun packetCount(frameBytes: Int): Int = max(1, (frameBytes + MAX_PAYLOAD_BYTES - 1) / MAX_PAYLOAD_BYTES)

    fun headersFor(frame: EncodedFrame, streamId: Int): List<UdpPacketHeader> {
        val count = packetCount(frame.bytes)
        return List(count) { index ->
            UdpPacketHeader(
                streamId = streamId,
                frameId = frame.frameId,
                packetIndex = index,
                packetCount = count,
                keyFrame = frame.keyFrame,
                presentationTimeUs = frame.presentationTimeUs,
            )
        }
    }
}

class ReassemblyWindow(private val maxFrames: Int) {
    private val frames = linkedMapOf<Long, MutableSet<Int>>()

    fun accept(header: UdpPacketHeader): Boolean {
        while (frames.size >= maxFrames && !frames.containsKey(header.frameId)) {
            frames.remove(frames.keys.first())
        }
        val packets = frames.getOrPut(header.frameId) { mutableSetOf() }
        packets += header.packetIndex
        val complete = packets.size == header.packetCount
        if (complete) frames.remove(header.frameId)
        return complete
    }
}

@Serializable
enum class ReconnectState {
    Connected,
    WaitingForHeartbeat,
    Reconnecting,
    Failed,
}

object ReconnectPolicy {
    fun state(nowMillis: Long, lastHeartbeatMillis: Long, attempts: Int, config: StreamQualityConfig): ReconnectState {
        val elapsed = nowMillis - lastHeartbeatMillis
        return when {
            elapsed <= config.heartbeatTimeoutMs -> ReconnectState.Connected
            attempts <= 0 -> ReconnectState.WaitingForHeartbeat
            attempts < 8 -> ReconnectState.Reconnecting
            else -> ReconnectState.Failed
        }
    }

    fun nextDelayMs(attempts: Int, config: StreamQualityConfig): Long {
        val multiplier = 1L shl attempts.coerceIn(0, 6)
        return min(config.reconnectMaxDelayMs, config.reconnectBaseDelayMs * multiplier)
    }
}

object ThermalResponsePolicy {
    fun recommendedProfile(current: StreamProfile, health: StreamHealth): StreamProfile = when {
        !health.thermalThrottling && !health.encoderOverloaded -> current

        current.id == StreamProfiles.Extreme.id ||
            current.id == StreamProfiles.Boost1080.id ||
            current.id == StreamProfiles.Cinema.id ||
            current.id == StreamProfiles.Sharp.id -> StreamProfiles.Quality

        current.id == StreamProfiles.Quality.id -> StreamProfiles.Balanced

        current.id == StreamProfiles.Balanced.id -> StreamProfiles.Performance

        else -> current
    }
}

@Serializable
data class AudioConfig(val sampleRateHz: Int = 48_000, val channelCount: Int = 2, val codec: String = "opus")

@Serializable
data class AudioPacket(val streamId: Int, val sequenceNumber: Long, val presentationTimeUs: Long, val payloadBytes: Int)

@Serializable
data class SyncClock(val senderBootTimeMs: Long, val mediaTimeUs: Long)

@Serializable
data class EmulatorContext(val foregroundPackage: String? = null, val profileId: String? = null)
