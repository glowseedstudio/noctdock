package com.glowseed.noctdock.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoPacketsTest {
    @Test
    fun connectionTestPacketRoundTripsWithReceiverSummaryFields() {
        val packet =
            ConnectionTestPacket(
                streamId = 9,
                testId = 44,
                stageIndex = 2,
                sequenceNumber = 88,
                sentAtUs = 123_456,
                echo = true,
                stageComplete = true,
                targetMbps = 22,
                payloadSize = 1200,
                expectedPackets = 500,
                receivedPackets = 492,
                missingPackets = 8,
                jitterUs = 5_000,
                receiverTransport = ReceiverTransportKind.ETHERNET.ordinal,
            )

        val decoded = PacketCodec.decodeConnectionTest(PacketCodec.decode(PacketCodec.encodeConnectionTest(packet)))

        assertEquals(packet.streamId, decoded.streamId)
        assertEquals(packet.testId, decoded.testId)
        assertEquals(packet.stageIndex, decoded.stageIndex)
        assertEquals(packet.sequenceNumber, decoded.sequenceNumber)
        assertEquals(packet.echo, decoded.echo)
        assertEquals(packet.stageComplete, decoded.stageComplete)
        assertEquals(packet.targetMbps, decoded.targetMbps)
        assertEquals(packet.expectedPackets, decoded.expectedPackets)
        assertEquals(packet.receivedPackets, decoded.receivedPackets)
        assertEquals(packet.missingPackets, decoded.missingPackets)
        assertEquals(packet.jitterUs, decoded.jitterUs)
        assertEquals(packet.receiverTransport, decoded.receiverTransport)
    }

    @Test
    fun configPacketRoundTrips() {
        val config =
            StreamConfigPacket(
                streamId = 9,
                width = 1280,
                height = 720,
                fps = 60,
                bitrate = 8_000_000,
                codecConfigSps = byteArrayOf(0, 0, 0, 1, 103),
                codecConfigPps = byteArrayOf(0, 0, 0, 1, 104),
                latencyPriority = LatencyPriority.Lowest,
            )

        val decoded = PacketCodec.decodeConfig(PacketCodec.decode(PacketCodec.encodeConfig(config)))

        assertEquals(config.streamId, decoded.streamId)
        assertEquals(config.width, decoded.width)
        assertEquals(config.height, decoded.height)
        assertEquals(config.fps, decoded.fps)
        assertEquals(config.bitrate, decoded.bitrate)
        assertEquals(config.latencyPriority, decoded.latencyPriority)
        assertArrayEquals(config.codecConfigSps, decoded.codecConfigSps)
        assertArrayEquals(config.codecConfigPps, decoded.codecConfigPps)
        assertEquals(StreamSourceType.NOCTDOCK_SENDER, decoded.sourceMetadata.sourceType)
        assertEquals("Console Mode", decoded.sourceMetadata.friendlyTitle)
    }

    @Test
    fun configPacketRoundTripsWithAzaharSourceMetadata() {
        val config =
            StreamConfigPacket(
                streamId = 9,
                width = 800,
                height = 480,
                fps = 30,
                bitrate = 3_000_000,
                codecConfigSps = byteArrayOf(0, 0, 0, 1, 103),
                codecConfigPps = byteArrayOf(0, 0, 0, 1, 104),
                sourceMetadata = StreamSourceMetadata.noctDockAzahar(),
            )

        val decoded = PacketCodec.decodeConfig(PacketCodec.decode(PacketCodec.encodeConfig(config)))

        assertEquals(StreamSourceType.NOCTDOCK_AZAHAR, decoded.sourceMetadata.sourceType)
        assertEquals("NoctDock Azahar", decoded.sourceMetadata.sourceAppName)
        assertEquals("THREE_DS_TOP_SCREEN", decoded.sourceMetadata.sourceMode)
        assertEquals("3DS Top Screen", decoded.sourceMetadata.displayTitle)
        assertEquals("Touch stays on handheld", decoded.sourceMetadata.displaySubtitle)
    }

    @Test
    fun configPacketWithoutSourceMetadataRemainsCompatible() {
        val payload =
            ByteBuffer
                .allocate(
                    4 * 8 +
                        VideoCodec.AVC.mime
                            .encodeToByteArray()
                            .size + 5 + 5,
                ).order(ByteOrder.BIG_ENDIAN)
                .putInt(1280)
                .putInt(720)
                .putInt(60)
                .putInt(8_000_000)
                .putInt(
                    VideoCodec.AVC.mime
                        .encodeToByteArray()
                        .size,
                ).put(VideoCodec.AVC.mime.encodeToByteArray())
                .putInt(5)
                .put(byteArrayOf(0, 0, 0, 1, 103))
                .putInt(5)
                .put(byteArrayOf(0, 0, 0, 1, 104))
                .putInt(LatencyPriority.Lowest.ordinal)
                .array()

        val decoded =
            PacketCodec.decodeConfig(
                FragmentPacket(
                    PacketHeader(PacketType.CONFIG, 9, 0, 0, 1, 0, PacketHeader.FLAG_CODEC_CONFIG, payload.size),
                    payload,
                ),
            )

        assertEquals(StreamSourceType.UNKNOWN, decoded.sourceMetadata.sourceType)
        assertEquals("Console Mode", decoded.sourceMetadata.displayTitle)
        assertEquals("Playing from handheld", decoded.sourceMetadata.displaySubtitle)
    }

    @Test
    fun unknownSourceTypeFallsBackToDefaultLabels() {
        val mimeBytes = VideoCodec.AVC.mime.encodeToByteArray()

        fun String.bytes() = encodeToByteArray()

        fun ByteBuffer.putString(value: String): ByteBuffer {
            val bytes = value.bytes()
            return putInt(bytes.size).put(bytes)
        }
        val payload =
            ByteBuffer
                .allocate(4 * 8 + mimeBytes.size + 5 + 5 + 4 + 4 + 7 + 4 + 4 + 4 + 10 + 4 + 10)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(1280)
                .putInt(720)
                .putInt(60)
                .putInt(8_000_000)
                .putInt(mimeBytes.size)
                .put(mimeBytes)
                .putInt(5)
                .put(byteArrayOf(0, 0, 0, 1, 103))
                .putInt(5)
                .put(byteArrayOf(0, 0, 0, 1, 104))
                .putInt(LatencyPriority.Balanced.ordinal)
                .putInt(999)
                .putString("Mystery")
                .putString("MODE")
                .putString("Bad title")
                .putString("Bad label")
                .array()

        val decoded =
            PacketCodec.decodeConfig(
                FragmentPacket(
                    PacketHeader(PacketType.CONFIG, 9, 0, 0, 1, 0, PacketHeader.FLAG_CODEC_CONFIG, payload.size),
                    payload,
                ),
            )

        assertEquals(StreamSourceType.UNKNOWN, decoded.sourceMetadata.sourceType)
        assertEquals("Console Mode", decoded.sourceMetadata.displayTitle)
        assertEquals("Playing from handheld", decoded.sourceMetadata.displaySubtitle)
    }

    @Test
    fun hevcConfigPacketRoundTrips() {
        val config =
            StreamConfigPacket(
                streamId = 10,
                width = 1920,
                height = 1080,
                fps = 60,
                bitrate = 32_000_000,
                codecConfigSps = byteArrayOf(0, 0, 0, 1, 64),
                codecConfigPps = ByteArray(0),
                mime = VideoCodec.HEVC.mime,
                latencyPriority = LatencyPriority.Quality,
            )

        val decoded = PacketCodec.decodeConfig(PacketCodec.decode(PacketCodec.encodeConfig(config)))

        assertEquals(VideoCodec.HEVC.mime, decoded.mime)
        assertEquals(1920, decoded.width)
        assertEquals(1080, decoded.height)
        assertEquals(32_000_000, decoded.bitrate)
        assertArrayEquals(config.codecConfigSps, decoded.codecConfigSps)
    }

    @Test
    fun malformedHevcConfigIsRejected() {
        val config =
            StreamConfigPacket(
                streamId = 10,
                width = 1920,
                height = 1080,
                fps = 60,
                bitrate = 32_000_000,
                codecConfigSps = ByteArray(0),
                codecConfigPps = ByteArray(0),
                mime = VideoCodec.HEVC.mime,
            )

        runCatching { PacketCodec.decodeConfig(PacketCodec.decode(PacketCodec.encodeConfig(config))) }
            .onSuccess { throw AssertionError("Expected malformed HEVC config to be rejected") }
    }

    @Test
    fun videoFrameFragmentsAndReassembles() {
        val bytes = ByteArray(PacketCodec.MAX_DATAGRAM_SIZE * 3) { (it % 251).toByte() }
        val frame =
            VideoFramePacket(streamId = 4, frameId = 22, presentationTimeUs = 33_333, keyFrame = true, data = bytes)
        val assembler = FrameAssembler(timeoutMs = 500)

        val rebuilt =
            PacketCodec
                .fragmentFrame(frame)
                .map { PacketCodec.decode(it) }
                .mapNotNull { assembler.accept(it, nowMs = 1000) }
                .single()

        assertEquals(frame.frameId, rebuilt.frameId)
        assertTrue(rebuilt.keyFrame)
        assertArrayEquals(bytes, rebuilt.data)
    }

    @Test
    fun incompleteFrameTimesOutAndDrops() {
        val frame =
            VideoFramePacket(
                streamId = 1,
                frameId = 77,
                presentationTimeUs = 1,
                keyFrame = false,
                data = ByteArray(3000),
            )
        val assembler = FrameAssembler(timeoutMs = 10)
        val first = PacketCodec.decode(PacketCodec.fragmentFrame(frame).first())

        assertEquals(null, assembler.accept(first, nowMs = 100))
        assertEquals(1, assembler.prune(nowMs = 200))
        assertEquals(1, assembler.droppedFrames)
    }

    @Test
    fun packetHeaderCarriesFrameMetadata() {
        val frame =
            VideoFramePacket(
                streamId = 5,
                frameId = 101,
                presentationTimeUs = 99_000,
                keyFrame = true,
                data = ByteArray(128),
            )

        val decoded = PacketCodec.decode(PacketCodec.fragmentFrame(frame).single())

        assertEquals(PacketType.VIDEO_FRAGMENT, decoded.header.type)
        assertEquals(frame.streamId, decoded.header.streamId)
        assertEquals(frame.frameId, decoded.header.frameId)
        assertEquals(frame.presentationTimeUs, decoded.header.timestampUs)
        assertTrue(decoded.header.isKeyFrame)
    }

    @Test
    fun metricsCountersCarryRealTransportValues() {
        val metrics =
            StreamMetrics(
                fps = 60,
                bitrateMbps = 8,
                droppedFrames = 2,
                receivedFps = 59,
                reassemblyDrops = 1,
                decoderErrors = 0,
            )

        assertEquals(60, metrics.fps)
        assertEquals(59, metrics.receivedFps)
        assertEquals(1, metrics.reassemblyDrops)
    }

    @Test
    fun receiverFeedbackRoundTrips() {
        val feedback =
            ReceiverFeedbackPacket(
                streamId = 7,
                receivedFps = 58,
                reassemblyDrops = 3,
                decoderErrors = 1,
                queueDepth = 2,
                audioPacketsReceived = 42,
                audioUnderruns = 2,
                audioDrops = 4,
                audioBufferMs = 38,
                avOffsetMs = 12,
            )

        val decoded =
            PacketCodec.decodeReceiverFeedback(
                PacketCodec.decode(PacketCodec.encodeReceiverFeedback(feedback)),
            )

        assertEquals(feedback.streamId, decoded.streamId)
        assertEquals(feedback.receivedFps, decoded.receivedFps)
        assertEquals(feedback.reassemblyDrops, decoded.reassemblyDrops)
        assertEquals(feedback.decoderErrors, decoded.decoderErrors)
        assertEquals(feedback.queueDepth, decoded.queueDepth)
        assertEquals(feedback.audioPacketsReceived, decoded.audioPacketsReceived)
        assertEquals(feedback.audioUnderruns, decoded.audioUnderruns)
        assertEquals(feedback.audioDrops, decoded.audioDrops)
        assertEquals(feedback.audioBufferMs, decoded.audioBufferMs)
        assertEquals(feedback.avOffsetMs, decoded.avOffsetMs)
    }

    @Test
    fun fragmentRepairRequestRoundTrips() {
        val request = FragmentRepairRequestPacket(streamId = 3, frameId = 99, fragmentIndexes = intArrayOf(1, 4, 7))

        val decoded =
            PacketCodec.decodeFragmentRepairRequest(
                PacketCodec.decode(PacketCodec.encodeFragmentRepairRequest(request)),
            )

        assertEquals(request.streamId, decoded.streamId)
        assertEquals(request.frameId, decoded.frameId)
        assertArrayEquals(request.fragmentIndexes, decoded.fragmentIndexes)
    }

    @Test
    fun receiverCapabilitiesPacketRoundTrips() {
        val capabilities =
            ReceiverVideoCapabilities(
                supportsAvc = true,
                supportsHevc = true,
                maxWidth = 1920,
                maxHeight = 1080,
                preferredCodec = VideoCodec.HEVC,
                decoderName = "c2.nvidia.hevc.decoder",
                receiverModel = "NVIDIA Shield",
                shieldOptimized = true,
            )

        val decoded =
            PacketCodec.decodeReceiverCapabilities(
                PacketCodec.decode(PacketCodec.encodeReceiverCapabilities(ReceiverCapabilitiesPacket(2, capabilities))),
            )

        assertEquals(2, decoded.streamId)
        assertTrue(decoded.capabilities.supportsHevc)
        assertEquals(VideoCodec.HEVC, decoded.capabilities.preferredCodec)
        assertEquals("NVIDIA Shield", decoded.capabilities.receiverModel)
        assertTrue(decoded.capabilities.shieldOptimized)
    }

    @Test
    fun connectionTestPacketRoundTripsWithRealPayloadSize() {
        val packet =
            ConnectionTestPacket(
                streamId = 3,
                testId = 1,
                stageIndex = 0,
                sequenceNumber = 7,
                sentAtUs = 1000,
                echo = false,
                stageComplete = false,
                targetMbps = 12,
                payloadSize = 512,
                expectedPackets = 24,
            )

        val encoded = PacketCodec.encodeConnectionTest(packet)
        val decoded = PacketCodec.decodeConnectionTest(PacketCodec.decode(encoded))

        assertEquals(3, decoded.streamId)
        assertEquals(1, decoded.testId)
        assertEquals(0, decoded.stageIndex)
        assertEquals(7, decoded.sequenceNumber)
        assertEquals(12, decoded.targetMbps)
        assertEquals(512, decoded.payloadSize)
        assertTrue(encoded.size > 512)
    }

    @Test
    fun largeFragmentRepairRequestRoundTripsUpToProtocolLimit() {
        val indexes = IntArray(PacketCodec.MAX_REPAIR_FRAGMENT_INDEXES) { it }
        val request = FragmentRepairRequestPacket(streamId = 3, frameId = 100, fragmentIndexes = indexes)

        val decoded =
            PacketCodec.decodeFragmentRepairRequest(
                PacketCodec.decode(PacketCodec.encodeFragmentRepairRequest(request)),
            )

        assertEquals(request.streamId, decoded.streamId)
        assertEquals(request.frameId, decoded.frameId)
        assertArrayEquals(indexes, decoded.fragmentIndexes)
    }

    @Test
    fun assemblerQueuesRepairRequestForMissingFragments() {
        val frame =
            VideoFramePacket(
                streamId = 2,
                frameId = 42,
                presentationTimeUs = 1,
                keyFrame = false,
                data =
                ByteArray(
                    3000,
                ) {
                    it.toByte()
                },
            )
        val fragments = PacketCodec.fragmentFrame(frame).map { PacketCodec.decode(it) }
        val assembler = FrameAssembler(timeoutMs = 100)

        assembler.accept(fragments[0], nowMs = 100)
        assembler.accept(fragments[2], nowMs = 112)
        val requests = assembler.drainRepairRequests(nowMs = 112)

        assertEquals(1, requests.size)
        assertEquals(frame.frameId, requests.single().frameId)
        assertArrayEquals(intArrayOf(1), requests.single().fragmentIndexes)
    }

    @Test
    fun assemblerQueuesRepairRequestForPendingFrameWithoutNewFragments() {
        val frame =
            VideoFramePacket(
                streamId = 2,
                frameId = 43,
                presentationTimeUs = 1,
                keyFrame = false,
                data =
                ByteArray(
                    3000,
                ) {
                    it.toByte()
                },
            )
        val fragments = PacketCodec.fragmentFrame(frame).map { PacketCodec.decode(it) }
        val assembler = FrameAssembler(timeoutMs = 100)

        assembler.accept(fragments[0], nowMs = 100)
        val requests = assembler.drainRepairRequests(nowMs = 112)

        assertEquals(1, requests.size)
        assertEquals(frame.frameId, requests.single().frameId)
        assertTrue(1 in requests.single().fragmentIndexes)
    }

    @Test
    fun assemblerRetriesRepairRequestWhileFrameIsAlive() {
        val frame =
            VideoFramePacket(
                streamId = 2,
                frameId = 44,
                presentationTimeUs = 1,
                keyFrame = false,
                data =
                ByteArray(
                    3000,
                ) {
                    it.toByte()
                },
            )
        val fragments = PacketCodec.fragmentFrame(frame).map { PacketCodec.decode(it) }
        val assembler = FrameAssembler(timeoutMs = 100)

        assembler.accept(fragments[0], nowMs = 100)
        assertEquals(1, assembler.drainRepairRequests(nowMs = 112).size)
        assertEquals(0, assembler.drainRepairRequests(nowMs = 127).size)
        assertEquals(0, assembler.drainRepairRequests(nowMs = 143).size)
        assertEquals(1, assembler.drainRepairRequests(nowMs = 159).size)
    }

    @Test
    fun audioPacketsRoundTrip() {
        val config = AudioConfig(sampleRateHz = 48_000, channelCount = 2, codec = "pcm16")
        val pcm =
            PcmAudioPacket(
                streamId = 11,
                sequenceNumber = 4,
                presentationTimeUs = 12_000,
                data = ByteArray(960) { it.toByte() },
            )

        val decodedConfig = PacketCodec.decodeAudioConfig(PacketCodec.decode(PacketCodec.encodeAudioConfig(11, config)))
        val decodedPcm = PacketCodec.decodeAudioPcm(PacketCodec.decode(PacketCodec.encodeAudioPcm(pcm)))

        assertEquals(config.sampleRateHz, decodedConfig.sampleRateHz)
        assertEquals(config.channelCount, decodedConfig.channelCount)
        assertEquals(pcm.streamId, decodedPcm.streamId)
        assertEquals(pcm.sequenceNumber, decodedPcm.sequenceNumber)
        assertArrayEquals(pcm.data, decodedPcm.data)
    }

    @Test
    fun invalidAudioPacketIsRejected() {
        val fragment =
            FragmentPacket(
                PacketHeader(PacketType.AUDIO_PCM, 1, 1, 0, 1, 1, 0, 0),
                ByteArray(0),
            )

        runCatching { PacketCodec.decodeAudioPcm(fragment) }
            .onSuccess { throw AssertionError("Expected invalid audio packet to be rejected") }
    }

    @Test
    fun invalidFeedbackPacketIsRejected() {
        val fragment =
            FragmentPacket(
                PacketHeader(PacketType.RECEIVER_FEEDBACK, 1, 0, 0, 1, 0, 0, 4),
                ByteArray(4),
            )

        runCatching { PacketCodec.decodeReceiverFeedback(fragment) }
            .onSuccess { throw AssertionError("Expected invalid feedback packet to be rejected") }
    }
}
