package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamOptimizationTest {
    @Test
    fun adaptiveBitrateReducesGraduallyWhenUnstable() {
        val controller = AdaptiveBitrateController()
        val bitrate =
            controller.nextBitrate(
                currentBitrateMbps = 14,
                health = health(packetLoss = 5, jitter = 20),
                config = StreamQualityConfig(),
            )

        assertEquals(12, bitrate)
    }

    @Test
    fun adaptiveBitrateRestoresOnlyAfterStableWindow() {
        val controller = AdaptiveBitrateController(stableSamplesBeforeIncrease = 2)
        val config = StreamQualityConfig()
        val excellent = health(packetLoss = 0, jitter = 4)

        assertEquals(10, controller.nextBitrate(10, excellent, config))
        assertEquals(11, controller.nextBitrate(10, excellent, config))
    }

    @Test
    fun reconnectStateMachineHandlesHeartbeatTimeouts() {
        val config = StreamQualityConfig(heartbeatTimeoutMs = 1000)

        assertEquals(ReconnectState.Connected, ReconnectPolicy.state(1500, 800, 0, config))
        assertEquals(ReconnectState.WaitingForHeartbeat, ReconnectPolicy.state(2500, 800, 0, config))
        assertEquals(ReconnectState.Reconnecting, ReconnectPolicy.state(2500, 800, 2, config))
        assertEquals(ReconnectState.Failed, ReconnectPolicy.state(2500, 800, 8, config))
    }

    @Test
    fun packetTimeoutDelayUsesBoundedBackoff() {
        val config = StreamQualityConfig(reconnectBaseDelayMs = 250, reconnectMaxDelayMs = 1000)

        assertEquals(250L, ReconnectPolicy.nextDelayMs(0, config))
        assertEquals(1000L, ReconnectPolicy.nextDelayMs(8, config))
    }

    @Test
    fun queueOverflowDropsOldFramesAndKeepsLatest() {
        val queue = LatestFrameQueue(capacity = 2)
        queue.offer(frame(1))
        queue.offer(frame(2))
        queue.offer(frame(3))

        assertEquals(1, queue.droppedFrames)
        assertEquals(listOf(2L, 3L), queue.snapshot().map { it.frameId })
    }

    @Test
    fun frameDropPrioritizationPreservesLatestFrameOrder() {
        val queue = LatestFrameQueue(capacity = 1)
        queue.offer(frame(10))
        queue.offer(frame(11))

        assertEquals(11L, queue.poll()?.frameId)
    }

    @Test
    fun bitrateScalingCapsBatterySaverMode() {
        val controller = AdaptiveBitrateController(stableSamplesBeforeIncrease = 1)
        val next =
            controller.nextBitrate(
                currentBitrateMbps = 20,
                health = health(packetLoss = 0, jitter = 1),
                config = StreamQualityConfig(batterySaverMode = true),
            )

        assertEquals(12, next)
    }

    @Test
    fun adaptiveBitrateRespectsConfiguredQualityFloor() {
        val controller = AdaptiveBitrateController(minBitrateMbps = 12)

        val next =
            controller.nextBitrate(
                currentBitrateMbps = 12,
                health = health(packetLoss = 20, jitter = 60),
                config = StreamQualityConfig(),
            )

        assertEquals(12, next)
    }

    @Test
    fun profileAdaptiveBitrateDoesNotDropBelowFloorForNormalInstability() {
        val controller = ProfileAdaptiveBitrateController(floorMbps = 24, ceilingMbps = 38)

        val next =
            controller.nextBitrate(
                currentBitrateMbps = 24,
                health = health(packetLoss = 5, jitter = 25),
                config = StreamQualityConfig(),
            )

        assertEquals(24, next)
    }

    @Test
    fun connectionTestClassifiesCinemaOnlyForStrongLocalResults() {
        assertEquals(
            ConnectionRecommendation.CINEMA,
            ConnectionTestResult(
                throughputMbps = 60,
                packetLossPercent = 0,
                jitterMs = 4,
                roundTripMs = 6,
                receiverResponsive = true,
            ).recommendation,
        )
        assertEquals(
            ConnectionRecommendation.QUALITY,
            ConnectionTestResult(
                throughputMbps = 36,
                packetLossPercent = 1,
                jitterMs = 10,
                roundTripMs = 10,
                receiverResponsive = true,
            ).recommendation,
        )
        assertEquals(
            ConnectionRecommendation.PERFORMANCE,
            ConnectionTestResult(
                throughputMbps = 20,
                packetLossPercent = 3,
                jitterMs = 25,
                roundTripMs = 30,
                receiverResponsive = true,
            ).recommendation,
        )
    }

    @Test
    fun connectionTestCanRecommendBalancedForFairLocalResults() {
        assertEquals(
            ConnectionRecommendation.BALANCED,
            ConnectionTestResult(
                throughputMbps = 18,
                packetLossPercent = 2,
                jitterMs = 13,
                roundTripMs = 18,
                receiverResponsive = true,
            ).recommendation,
        )
    }

    @Test
    fun staleConnectionResultsAreFlaggedAfterTwentyFourHours() {
        val result =
            ConnectionTestResult(
                throughputMbps = 22,
                packetLossPercent = 0,
                jitterMs = 5,
                roundTripMs = 8,
                receiverResponsive = true,
                testedAtMillis = 1_000L,
            )

        assertTrue(result.isStale(1_000L + 24L * 60L * 60L * 1000L + 1L))
        assertFalse(result.isStale(1_000L + 1000L))
    }

    @Test
    fun extremeIsNotAutoRecommendedWithoutExperimentalFlag() {
        val result =
            ConnectionTestResult(
                throughputMbps = 60,
                packetLossPercent = 0,
                jitterMs = 3,
                roundTripMs = 6,
                receiverResponsive = true,
            )

        assertFalse(ConnectionTestEvaluator.allowAutoRecommendExperimental(result, experimentalEnabled = false))
        assertTrue(ConnectionTestEvaluator.allowAutoRecommendExperimental(result, experimentalEnabled = true))
    }

    @Test
    fun connectionTestSummaryUsesMeasuredStageResults() {
        val result =
            ConnectionTestEvaluator.summarize(
                listOf(
                    ConnectionTestStageSummary(
                        stage = ConnectionTestStage(0, 12, durationMs = 650),
                        expectedPackets = 800,
                        receivedPackets = 800,
                        missingPackets = 0,
                        jitterMs = 3,
                        roundTripMs = 6,
                        receiverTransport = ReceiverTransportKind.WIFI,
                    ),
                    ConnectionTestStageSummary(
                        stage = ConnectionTestStage(1, 18, durationMs = 650),
                        expectedPackets = 1200,
                        receivedPackets = 1170,
                        missingPackets = 30,
                        jitterMs = 8,
                        roundTripMs = 9,
                        receiverTransport = ReceiverTransportKind.WIFI,
                    ),
                ),
            )

        assertEquals(17, result.throughputMbps)
        assertEquals(ConnectionRecommendation.BALANCED, result.recommendation)
    }

    @Test
    fun connectionTestStageLossPercentUsesMissingPacketsAgainstExpectedPackets() {
        val summary =
            ConnectionTestStageSummary(
                stage = ConnectionTestStage(0, 22, durationMs = 650),
                expectedPackets = 1000,
                receivedPackets = 970,
                missingPackets = 30,
                jitterMs = 7,
                roundTripMs = 8,
                receiverTransport = ReceiverTransportKind.WIFI,
            )

        assertEquals(3, summary.lossPercent)
    }

    @Test
    fun emptyConnectionTestSummaryFallsBackToPoorUnresponsiveResult() {
        val result = ConnectionTestEvaluator.summarize(emptyList())

        assertEquals(0, result.throughputMbps)
        assertEquals(ConnectionStabilityRating.Poor, result.stability)
        assertEquals(ConnectionRecommendation.PERFORMANCE, result.recommendation)
        assertFalse(result.receiverResponsive)
    }

    @Test
    fun receiverFeedbackDeltaUsesOnlyNewDropsAndErrors() {
        val tracker = ReceiverFeedbackDeltaTracker()

        val first =
            tracker.update(
                ReceiverFeedbackPacket(
                    streamId = 1,
                    receivedFps = 60,
                    reassemblyDrops = 4,
                    decoderErrors = 1,
                    queueDepth = 0,
                    audioUnderruns = 2,
                    audioDrops = 3,
                ),
            )
        val second =
            tracker.update(
                ReceiverFeedbackPacket(
                    streamId = 1,
                    receivedFps = 60,
                    reassemblyDrops = 5,
                    decoderErrors = 1,
                    queueDepth = 0,
                    audioUnderruns = 4,
                    audioDrops = 3,
                ),
            )

        assertEquals(4, first.reassemblyDrops)
        assertEquals(1, first.decoderErrors)
        assertEquals(1, second.reassemblyDrops)
        assertEquals(0, second.decoderErrors)
        assertEquals(2, second.audioUnderruns)
        assertEquals(0, second.audioDrops)
    }

    @Test
    fun packetPacingIntervalTracksConfiguredBitrate() {
        val intervalNs = PacketPacingPolicy.packetIntervalNs(packetBytes = 1_200, bitrateMbps = 12)

        assertEquals(800_000L, intervalNs)
    }

    @Test
    fun packetPacerCapsDelayToFrameBudget() {
        val pacer = PacketPacer(minimumDelayNs = 1L)
        val budget = PacketPacingPolicy.frameBudgetNs(LatencyPriority.Lowest, keyFrame = false)
        var remaining = budget
        var totalDelay = 0L

        repeat(20) {
            val delay = pacer.delayForPacketNs(packetBytes = 1_200, bitrateMbps = 4, remainingFrameBudgetNs = remaining)
            totalDelay += delay
            remaining = (remaining - delay).coerceAtLeast(0L)
        }

        assertEquals(budget, totalDelay)
    }

    @Test
    fun packetPacerDoesNotDelayWhenDisabledByZeroBudget() {
        val pacer = PacketPacer(minimumDelayNs = 1L)

        assertEquals(0L, pacer.delayForPacketNs(packetBytes = 1_200, bitrateMbps = 12, remainingFrameBudgetNs = 0L))
    }

    @Test
    fun streamHealthCalculatesQualityGrades() {
        assertEquals(StreamHealthGrade.Excellent, StreamHealthCalculator.grade(health(packetLoss = 0, jitter = 2)))
        assertEquals(StreamHealthGrade.Unstable, StreamHealthCalculator.grade(health(packetLoss = 5, jitter = 30)))
        assertEquals(StreamHealthGrade.Critical, StreamHealthCalculator.grade(health(packetLoss = 12, jitter = 30)))
    }

    @Test
    fun thermalResponseRecommendsLowerProfile() {
        val recommendation =
            ThermalResponsePolicy.recommendedProfile(
                current = StreamProfiles.Quality,
                health = health(packetLoss = 0, jitter = 2, thermal = true),
            )

        assertEquals(StreamProfiles.Balanced, recommendation)
    }

    @Test
    fun profileMetadataIsValid() {
        StreamProfiles.all.forEach { profile ->
            assertTrue(profile.expectedLatencyMs > 0)
            assertTrue(profile.expectedBandwidthMbps >= profile.bitrateMbps)
            assertFalse(profile.title.isBlank())
        }
    }

    @Test
    fun packetizerCreatesExpectedFragmentHeaders() {
        val headers = UdpPacketizer.headersFor(frame(1, bytes = UdpPacketizer.MAX_PAYLOAD_BYTES + 1), streamId = 7)

        assertEquals(2, headers.size)
        assertEquals(0, headers.first().packetIndex)
        assertEquals(1, headers.last().packetIndex)
        assertTrue(headers.first().keyFrame)
    }

    @Test
    fun reassemblyWindowReportsCompletion() {
        val window = ReassemblyWindow(maxFrames = 4)
        val headers = UdpPacketizer.headersFor(frame(2, bytes = UdpPacketizer.MAX_PAYLOAD_BYTES + 1), streamId = 1)

        assertFalse(window.accept(headers.first()))
        assertTrue(window.accept(headers.last()))
    }

    private fun frame(id: Long, bytes: Int = 200): EncodedFrame = EncodedFrame(
        frameId = id,
        presentationTimeUs = id * 16_666,
        keyFrame = true,
        bytes = bytes,
    )

    private fun health(packetLoss: Int, jitter: Int, thermal: Boolean = false): StreamHealth = StreamHealth(
        packetLossPercent = packetLoss,
        jitterMs = jitter,
        queueDepth = 1,
        droppedFramesPerMinute = 0,
        receiverDecodeErrors = 0,
        thermalThrottling = thermal,
        encoderOverloaded = false,
    )
}
