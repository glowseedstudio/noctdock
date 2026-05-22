package com.glowseed.noctdock.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

enum class PacketType(val id: Int) {
    CONFIG(1),
    VIDEO_FRAGMENT(2),
    HEARTBEAT(3),
    STOP(4),
    RECEIVER_FEEDBACK(5),
    AUDIO_CONFIG(6),
    AUDIO_PCM(7),
    FRAGMENT_REPAIR_REQUEST(8),
    RECEIVER_CAPABILITIES(9),
    CONNECTION_TEST(10),
    ;

    companion object {
        fun fromId(id: Int): PacketType = entries.firstOrNull { it.id == id } ?: error("Unknown packet type $id")
    }
}

data class PacketHeader(val type: PacketType, val streamId: Int, val frameId: Long, val fragmentIndex: Int, val fragmentCount: Int, val timestampUs: Long, val flags: Int, val payloadSize: Int) {
    val isKeyFrame: Boolean get() = flags and FLAG_KEY_FRAME != 0

    companion object {
        const val MAGIC = 0x4E44564F
        const val VERSION = 1
        const val BYTE_SIZE = 42
        const val FLAG_KEY_FRAME = 1
        const val FLAG_CODEC_CONFIG = 1 shl 1
    }
}

data class StreamConfigPacket(
    val streamId: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val codecConfigSps: ByteArray,
    val codecConfigPps: ByteArray,
    val mime: String = "video/avc",
    val latencyPriority: LatencyPriority = LatencyPriority.Balanced,
    val sourceMetadata: StreamSourceMetadata = StreamSourceMetadata.noctDockSender(),
) {
    override fun equals(other: Any?): Boolean = other is StreamConfigPacket &&
        streamId == other.streamId &&
        width == other.width &&
        height == other.height &&
        fps == other.fps &&
        bitrate == other.bitrate &&
        mime == other.mime &&
        latencyPriority == other.latencyPriority &&
        sourceMetadata == other.sourceMetadata &&
        codecConfigSps.contentEquals(other.codecConfigSps) &&
        codecConfigPps.contentEquals(other.codecConfigPps)

    override fun hashCode(): Int {
        var result = streamId
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + fps
        result = 31 * result + bitrate
        result = 31 * result + codecConfigSps.contentHashCode()
        result = 31 * result + codecConfigPps.contentHashCode()
        result = 31 * result + mime.hashCode()
        result = 31 * result + latencyPriority.hashCode()
        result = 31 * result + sourceMetadata.hashCode()
        return result
    }
}

enum class StreamSourceType {
    NOCTDOCK_SENDER,
    NOCTDOCK_AZAHAR,
    UNKNOWN,
}

data class StreamSourceMetadata(
    val sourceType: StreamSourceType = StreamSourceType.UNKNOWN,
    val sourceAppName: String = "",
    val sourceMode: String = "",
    val friendlyTitle: String = "",
    val friendlySubtitle: String = "",
) {
    val displayTitle: String
        get() =
            friendlyTitle.ifBlank {
                when (sourceType) {
                    StreamSourceType.NOCTDOCK_AZAHAR -> "3DS Top Screen"
                    StreamSourceType.NOCTDOCK_SENDER -> "Console Mode"
                    StreamSourceType.UNKNOWN -> "Console Mode"
                }
            }

    val displaySubtitle: String
        get() =
            friendlySubtitle.ifBlank {
                when (sourceType) {
                    StreamSourceType.NOCTDOCK_AZAHAR -> "Touch stays on handheld"
                    StreamSourceType.NOCTDOCK_SENDER -> "Playing from handheld"
                    StreamSourceType.UNKNOWN -> "Playing from handheld"
                }
            }

    companion object {
        fun noctDockSender(): StreamSourceMetadata = StreamSourceMetadata(
            sourceType = StreamSourceType.NOCTDOCK_SENDER,
            sourceAppName = "NoctDock",
            sourceMode = "CONSOLE_MODE",
            friendlyTitle = "Console Mode",
            friendlySubtitle = "Playing from handheld",
        )

        fun noctDockAzahar(): StreamSourceMetadata = StreamSourceMetadata(
            sourceType = StreamSourceType.NOCTDOCK_AZAHAR,
            sourceAppName = "NoctDock Azahar",
            sourceMode = "THREE_DS_TOP_SCREEN",
            friendlyTitle = "3DS Top Screen",
            friendlySubtitle = "Touch stays on handheld",
        )

        fun unknown(): StreamSourceMetadata = StreamSourceMetadata()
    }
}

data class VideoFramePacket(val streamId: Int, val frameId: Long, val presentationTimeUs: Long, val keyFrame: Boolean, val data: ByteArray) {
    override fun equals(other: Any?): Boolean = other is VideoFramePacket &&
        streamId == other.streamId &&
        frameId == other.frameId &&
        presentationTimeUs == other.presentationTimeUs &&
        keyFrame == other.keyFrame &&
        data.contentEquals(other.data)

    override fun hashCode(): Int {
        var result = streamId
        result = 31 * result + frameId.hashCode()
        result = 31 * result + presentationTimeUs.hashCode()
        result = 31 * result + keyFrame.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class ReceiverFeedbackPacket(
    val streamId: Int,
    val receivedFps: Int,
    val reassemblyDrops: Int,
    val decoderErrors: Int,
    val queueDepth: Int,
    val audioPacketsReceived: Int = 0,
    val audioUnderruns: Int = 0,
    val audioDrops: Int = 0,
    val audioBufferMs: Int = 0,
    val avOffsetMs: Int = 0,
)

data class PcmAudioPacket(val streamId: Int, val sequenceNumber: Long, val presentationTimeUs: Long, val data: ByteArray) {
    override fun equals(other: Any?): Boolean = other is PcmAudioPacket &&
        streamId == other.streamId &&
        sequenceNumber == other.sequenceNumber &&
        presentationTimeUs == other.presentationTimeUs &&
        data.contentEquals(other.data)

    override fun hashCode(): Int {
        var result = streamId
        result = 31 * result + sequenceNumber.hashCode()
        result = 31 * result + presentationTimeUs.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

data class FragmentPacket(val header: PacketHeader, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean = other is FragmentPacket && header == other.header && payload.contentEquals(other.payload)

    override fun hashCode(): Int = 31 * header.hashCode() + payload.contentHashCode()
}

data class FragmentRepairRequestPacket(val streamId: Int, val frameId: Long, val fragmentIndexes: IntArray) {
    override fun equals(other: Any?): Boolean = other is FragmentRepairRequestPacket &&
        streamId == other.streamId &&
        frameId == other.frameId &&
        fragmentIndexes.contentEquals(other.fragmentIndexes)

    override fun hashCode(): Int {
        var result = streamId
        result = 31 * result + frameId.hashCode()
        result = 31 * result + fragmentIndexes.contentHashCode()
        return result
    }
}

data class ReceiverCapabilitiesPacket(val streamId: Int, val capabilities: ReceiverVideoCapabilities)

data class ConnectionTestPacket(
    val streamId: Int,
    val testId: Int,
    val stageIndex: Int,
    val sequenceNumber: Long,
    val sentAtUs: Long,
    val echo: Boolean,
    val stageComplete: Boolean,
    val targetMbps: Int,
    val payloadSize: Int,
    val expectedPackets: Int = 0,
    val receivedPackets: Int = 0,
    val missingPackets: Int = 0,
    val jitterUs: Int = 0,
    val receiverTransport: Int = 0,
)

/**
 * Binary encode/decode for the NoctDock LAN protocol (magic `NDVO`, version 1).
 *
 * Video frames are split into MTU-sized [PacketType.VIDEO_FRAGMENT] payloads; receivers reassemble by
 * `frameId` and `fragmentIndex`. Config, heartbeat, feedback, audio, and connection-test types share
 * the same [PacketHeader] layout—do not change field sizes without bumping [ProtocolVersion].
 */
object PacketCodec {
    const val MAX_DATAGRAM_SIZE = 1400
    const val MAX_REPAIR_FRAGMENT_INDEXES = 32
    private const val MAX_FRAGMENT_PAYLOAD = MAX_DATAGRAM_SIZE - PacketHeader.BYTE_SIZE
    private const val MAX_SOURCE_METADATA_STRING_BYTES = 160

    fun encodeConfig(packet: StreamConfigPacket): ByteArray {
        val mimeBytes = packet.mime.encodeToByteArray()
        val sourceAppBytes =
            packet.sourceMetadata.sourceAppName
                .encodeToByteArray()
                .takeBoundedSourceBytes()
        val sourceModeBytes =
            packet.sourceMetadata.sourceMode
                .encodeToByteArray()
                .takeBoundedSourceBytes()
        val friendlyTitleBytes =
            packet.sourceMetadata.friendlyTitle
                .encodeToByteArray()
                .takeBoundedSourceBytes()
        val friendlySubtitleBytes =
            packet.sourceMetadata.friendlySubtitle
                .encodeToByteArray()
                .takeBoundedSourceBytes()
        val metadataSize =
            4 + 4 + sourceAppBytes.size + 4 + sourceModeBytes.size + 4 + friendlyTitleBytes.size + 4 +
                friendlySubtitleBytes.size
        val payloadSize =
            4 * 8 + mimeBytes.size + packet.codecConfigSps.size + packet.codecConfigPps.size + metadataSize
        val payload =
            ByteBuffer
                .allocate(payloadSize)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(packet.width)
                .putInt(packet.height)
                .putInt(packet.fps)
                .putInt(packet.bitrate)
                .putInt(mimeBytes.size)
                .put(mimeBytes)
                .putInt(packet.codecConfigSps.size)
                .put(packet.codecConfigSps)
                .putInt(packet.codecConfigPps.size)
                .put(packet.codecConfigPps)
                .putInt(packet.latencyPriority.ordinal)
                .putInt(packet.sourceMetadata.sourceType.ordinal)
                .putBoundedString(sourceAppBytes)
                .putBoundedString(sourceModeBytes)
                .putBoundedString(friendlyTitleBytes)
                .putBoundedString(friendlySubtitleBytes)
                .array()
        val header =
            PacketHeader(
                type = PacketType.CONFIG,
                streamId = packet.streamId,
                frameId = 0,
                fragmentIndex = 0,
                fragmentCount = 1,
                timestampUs = 0,
                flags = PacketHeader.FLAG_CODEC_CONFIG,
                payloadSize = payload.size,
            )
        return encodeFragment(FragmentPacket(header, payload))
    }

    fun decodeConfig(fragment: FragmentPacket): StreamConfigPacket {
        require(fragment.header.type == PacketType.CONFIG) { "Packet is not CONFIG" }
        val buffer = ByteBuffer.wrap(fragment.payload).order(ByteOrder.BIG_ENDIAN)
        require(buffer.remaining() >= 20) { "Invalid config payload" }
        val width = buffer.int
        val height = buffer.int
        val fps = buffer.int
        val bitrate = buffer.int
        val mimeSize = buffer.int
        require(mimeSize in 1..64 && buffer.remaining() >= mimeSize + 4) { "Invalid config MIME size" }
        val mimeBytes = ByteArray(mimeSize)
        buffer.get(mimeBytes)
        val spsSize = buffer.int
        require(spsSize in 0..256_000 && buffer.remaining() >= spsSize + 4) { "Invalid codec config size" }
        val sps = ByteArray(spsSize)
        buffer.get(sps)
        val ppsSize = buffer.int
        require(ppsSize in 0..256_000 && buffer.remaining() >= ppsSize) { "Invalid codec config size" }
        val pps = ByteArray(ppsSize)
        buffer.get(pps)
        val latencyPriority =
            if (buffer.remaining() >= 4) {
                LatencyPriority.entries.getOrElse(buffer.int) { LatencyPriority.Balanced }
            } else {
                LatencyPriority.Balanced
            }
        val sourceMetadata = decodeSourceMetadata(buffer)
        val mime = mimeBytes.decodeToString()
        require(mime == VideoCodec.AVC.mime || mime == VideoCodec.HEVC.mime) { "Unsupported video codec" }
        if (mime == VideoCodec.HEVC.mime) require(sps.isNotEmpty()) { "Invalid HEVC config" }
        return StreamConfigPacket(
            fragment.header.streamId,
            width,
            height,
            fps,
            bitrate,
            sps,
            pps,
            mime,
            latencyPriority,
            sourceMetadata,
        )
    }

    private fun decodeSourceMetadata(buffer: ByteBuffer): StreamSourceMetadata {
        if (buffer.remaining() < 4) return StreamSourceMetadata.unknown()
        val sourceType = StreamSourceType.entries.getOrElse(buffer.int) { StreamSourceType.UNKNOWN }
        val sourceAppName = buffer.readBoundedStringOrNull() ?: return StreamSourceMetadata.unknown()
        val sourceMode = buffer.readBoundedStringOrNull() ?: return StreamSourceMetadata.unknown()
        val friendlyTitle = buffer.readBoundedStringOrNull() ?: return StreamSourceMetadata.unknown()
        val friendlySubtitle = buffer.readBoundedStringOrNull() ?: return StreamSourceMetadata.unknown()
        if (sourceType == StreamSourceType.UNKNOWN) return StreamSourceMetadata.unknown()
        return StreamSourceMetadata(
            sourceType = sourceType,
            sourceAppName = sourceAppName,
            sourceMode = sourceMode,
            friendlyTitle = friendlyTitle,
            friendlySubtitle = friendlySubtitle,
        )
    }

    private fun ByteArray.takeBoundedSourceBytes(): ByteArray = if (size <= MAX_SOURCE_METADATA_STRING_BYTES) this else copyOf(MAX_SOURCE_METADATA_STRING_BYTES)

    private fun ByteBuffer.putBoundedString(bytes: ByteArray): ByteBuffer = putInt(bytes.size).put(bytes)

    private fun ByteBuffer.readBoundedStringOrNull(): String? {
        if (remaining() < 4) return null
        val size = int
        if (size !in 0..MAX_SOURCE_METADATA_STRING_BYTES || remaining() < size) return null
        val bytes = ByteArray(size)
        get(bytes)
        return bytes.decodeToString()
    }

    fun fragmentFrame(frame: VideoFramePacket): List<ByteArray> {
        val fragments = mutableListOf<ByteArray>()
        forEachFrameFragment(frame) { fragments += it }
        return fragments
    }

    fun forEachFrameFragment(frame: VideoFramePacket, consume: (ByteArray) -> Unit) {
        val fragmentCount = ceil(frame.data.size / MAX_FRAGMENT_PAYLOAD.toDouble()).toInt().coerceAtLeast(1)
        for (index in 0 until fragmentCount) {
            val start = index * MAX_FRAGMENT_PAYLOAD
            val end = minOf(start + MAX_FRAGMENT_PAYLOAD, frame.data.size)
            val payload = frame.data.copyOfRange(start, end)
            val header =
                PacketHeader(
                    type = PacketType.VIDEO_FRAGMENT,
                    streamId = frame.streamId,
                    frameId = frame.frameId,
                    fragmentIndex = index,
                    fragmentCount = fragmentCount,
                    timestampUs = frame.presentationTimeUs,
                    flags = if (frame.keyFrame) PacketHeader.FLAG_KEY_FRAME else 0,
                    payloadSize = payload.size,
                )
            consume(encodeFragment(FragmentPacket(header, payload)))
        }
    }

    fun encodeHeartbeat(streamId: Int): ByteArray = encodeFragment(
        FragmentPacket(
            PacketHeader(PacketType.HEARTBEAT, streamId, 0, 0, 1, System.nanoTime() / 1000L, 0, 0),
            ByteArray(0),
        ),
    )

    fun encodeStop(streamId: Int): ByteArray = encodeFragment(FragmentPacket(PacketHeader(PacketType.STOP, streamId, 0, 0, 1, 0, 0, 0), ByteArray(0)))

    fun encodeFragmentRepairRequest(packet: FragmentRepairRequestPacket): ByteArray {
        val limitedIndexes = packet.fragmentIndexes.take(MAX_REPAIR_FRAGMENT_INDEXES)
        val payload =
            ByteBuffer
                .allocate(4 + limitedIndexes.size * 4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(limitedIndexes.size)
                .also { buffer -> limitedIndexes.forEach { buffer.putInt(it) } }
                .array()
        return encodeFragment(
            FragmentPacket(
                PacketHeader(
                    type = PacketType.FRAGMENT_REPAIR_REQUEST,
                    streamId = packet.streamId,
                    frameId = packet.frameId,
                    fragmentIndex = 0,
                    fragmentCount = 1,
                    timestampUs = System.nanoTime() / 1_000L,
                    flags = 0,
                    payloadSize = payload.size,
                ),
                payload,
            ),
        )
    }

    fun decodeFragmentRepairRequest(fragment: FragmentPacket): FragmentRepairRequestPacket {
        require(fragment.header.type == PacketType.FRAGMENT_REPAIR_REQUEST) { "Packet is not FRAGMENT_REPAIR_REQUEST" }
        val buffer = ByteBuffer.wrap(fragment.payload).order(ByteOrder.BIG_ENDIAN)
        val count = buffer.int.coerceIn(0, MAX_REPAIR_FRAGMENT_INDEXES)
        require(buffer.remaining() >= count * 4) { "Invalid repair request payload" }
        val indexes = IntArray(count) { buffer.int }
        return FragmentRepairRequestPacket(fragment.header.streamId, fragment.header.frameId, indexes)
    }

    fun encodeReceiverCapabilities(packet: ReceiverCapabilitiesPacket): ByteArray {
        val modelBytes = packet.capabilities.receiverModel.encodeToByteArray()
        val decoderBytes = packet.capabilities.decoderName.encodeToByteArray()
        val payload =
            ByteBuffer
                .allocate(4 * 10 + modelBytes.size + decoderBytes.size)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(if (packet.capabilities.supportsAvc) 1 else 0)
                .putInt(if (packet.capabilities.supportsHevc) 1 else 0)
                .putInt(packet.capabilities.maxWidth)
                .putInt(packet.capabilities.maxHeight)
                .putInt(packet.capabilities.maxFps)
                .putInt(packet.capabilities.preferredCodec.ordinal)
                .putInt(modelBytes.size)
                .put(modelBytes)
                .putInt(decoderBytes.size)
                .put(decoderBytes)
                .putInt(if (packet.capabilities.lowLatencyDecodeSupported) 1 else 0)
                .putInt(if (packet.capabilities.shieldOptimized) 1 else 0)
                .array()
        return encodeFragment(
            FragmentPacket(
                PacketHeader(
                    PacketType.RECEIVER_CAPABILITIES,
                    packet.streamId,
                    0,
                    0,
                    1,
                    System.nanoTime() / 1000L,
                    0,
                    payload.size,
                ),
                payload,
            ),
        )
    }

    fun decodeReceiverCapabilities(fragment: FragmentPacket): ReceiverCapabilitiesPacket {
        require(fragment.header.type == PacketType.RECEIVER_CAPABILITIES) { "Packet is not RECEIVER_CAPABILITIES" }
        val buffer = ByteBuffer.wrap(fragment.payload).order(ByteOrder.BIG_ENDIAN)
        require(buffer.remaining() >= 28) { "Invalid receiver capabilities payload" }
        val supportsAvc = buffer.int == 1
        val supportsHevc = buffer.int == 1
        val maxWidth = buffer.int
        val maxHeight = buffer.int
        val maxFps = buffer.int
        val preferredCodec = VideoCodec.entries.getOrElse(buffer.int) { VideoCodec.AVC }
        val modelSize = buffer.int
        require(modelSize in 0..96 && buffer.remaining() >= modelSize + 4) { "Invalid receiver model size" }
        val modelBytes = ByteArray(modelSize)
        buffer.get(modelBytes)
        val decoderSize = buffer.int
        require(decoderSize in 0..96 && buffer.remaining() >= decoderSize + 8) { "Invalid decoder name size" }
        val decoderBytes = ByteArray(decoderSize)
        buffer.get(decoderBytes)
        return ReceiverCapabilitiesPacket(
            streamId = fragment.header.streamId,
            capabilities =
            ReceiverVideoCapabilities(
                supportsAvc = supportsAvc,
                supportsHevc = supportsHevc,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                maxFps = maxFps,
                preferredCodec = preferredCodec,
                receiverModel = modelBytes.decodeToString(),
                decoderName = decoderBytes.decodeToString(),
                lowLatencyDecodeSupported = buffer.int == 1,
                shieldOptimized = buffer.int == 1,
            ),
        )
    }

    fun encodeConnectionTest(packet: ConnectionTestPacket): ByteArray {
        val payloadBytes = packet.payloadSize.coerceIn(52, MAX_DATAGRAM_SIZE - PacketHeader.BYTE_SIZE)
        val payload =
            ByteBuffer
                .allocate(payloadBytes)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(packet.testId)
                .putInt(packet.stageIndex)
                .putLong(packet.sequenceNumber)
                .putLong(packet.sentAtUs)
                .putInt(if (packet.echo) 1 else 0)
                .putInt(if (packet.stageComplete) 1 else 0)
                .putInt(packet.targetMbps)
                .putInt(payloadBytes)
                .putInt(packet.expectedPackets)
                .putInt(packet.receivedPackets)
                .putInt(packet.missingPackets)
                .putInt(packet.jitterUs)
                .putInt(packet.receiverTransport)
                .array()
        return encodeFragment(
            FragmentPacket(
                PacketHeader(
                    PacketType.CONNECTION_TEST,
                    packet.streamId,
                    packet.sequenceNumber,
                    0,
                    1,
                    packet.sentAtUs,
                    0,
                    payload.size,
                ),
                payload,
            ),
        )
    }

    fun decodeConnectionTest(fragment: FragmentPacket): ConnectionTestPacket {
        require(fragment.header.type == PacketType.CONNECTION_TEST) { "Packet is not CONNECTION_TEST" }
        require(fragment.payload.size >= 52) { "Invalid connection test payload" }
        val buffer = ByteBuffer.wrap(fragment.payload).order(ByteOrder.BIG_ENDIAN)
        return ConnectionTestPacket(
            streamId = fragment.header.streamId,
            testId = buffer.int,
            stageIndex = buffer.int,
            sequenceNumber = buffer.long,
            sentAtUs = buffer.long,
            echo = buffer.int == 1,
            stageComplete = buffer.int == 1,
            targetMbps = buffer.int,
            payloadSize = buffer.int,
            expectedPackets = buffer.int,
            receivedPackets = buffer.int,
            missingPackets = buffer.int,
            jitterUs = buffer.int,
            receiverTransport = buffer.int,
        )
    }

    fun encodeReceiverFeedback(packet: ReceiverFeedbackPacket): ByteArray {
        val payload =
            ByteBuffer
                .allocate(4 * 9)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(packet.receivedFps)
                .putInt(packet.reassemblyDrops)
                .putInt(packet.decoderErrors)
                .putInt(packet.queueDepth)
                .putInt(packet.audioPacketsReceived)
                .putInt(packet.audioUnderruns)
                .putInt(packet.audioDrops)
                .putInt(packet.audioBufferMs)
                .putInt(packet.avOffsetMs)
                .array()
        return encodeFragment(
            FragmentPacket(
                PacketHeader(
                    type = PacketType.RECEIVER_FEEDBACK,
                    streamId = packet.streamId,
                    frameId = 0,
                    fragmentIndex = 0,
                    fragmentCount = 1,
                    timestampUs = System.nanoTime() / 1000L,
                    flags = 0,
                    payloadSize = payload.size,
                ),
                payload,
            ),
        )
    }

    fun decodeReceiverFeedback(fragment: FragmentPacket): ReceiverFeedbackPacket {
        require(fragment.header.type == PacketType.RECEIVER_FEEDBACK) { "Packet is not RECEIVER_FEEDBACK" }
        require(fragment.payload.size == 4 * 9) { "Invalid receiver feedback payload" }
        val buffer = ByteBuffer.wrap(fragment.payload).order(ByteOrder.BIG_ENDIAN)
        return ReceiverFeedbackPacket(
            streamId = fragment.header.streamId,
            receivedFps = buffer.int,
            reassemblyDrops = buffer.int,
            decoderErrors = buffer.int,
            queueDepth = buffer.int,
            audioPacketsReceived = buffer.int,
            audioUnderruns = buffer.int,
            audioDrops = buffer.int,
            audioBufferMs = buffer.int,
            avOffsetMs = buffer.int,
        )
    }

    fun encodeAudioConfig(streamId: Int, config: AudioConfig): ByteArray {
        val payload =
            ByteBuffer
                .allocate(4 * 2)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(config.sampleRateHz)
                .putInt(config.channelCount)
                .array()
        return encodeFragment(
            FragmentPacket(
                PacketHeader(PacketType.AUDIO_CONFIG, streamId, 0, 0, 1, 0, 0, payload.size),
                payload,
            ),
        )
    }

    fun decodeAudioConfig(fragment: FragmentPacket): AudioConfig {
        require(fragment.header.type == PacketType.AUDIO_CONFIG) { "Packet is not AUDIO_CONFIG" }
        require(fragment.payload.size == 4 * 2) { "Invalid audio config payload" }
        val buffer = ByteBuffer.wrap(fragment.payload).order(ByteOrder.BIG_ENDIAN)
        return AudioConfig(
            sampleRateHz = buffer.int,
            channelCount = buffer.int,
            codec = "pcm16",
        )
    }

    fun encodeAudioPcm(packet: PcmAudioPacket): ByteArray = encodeFragment(
        FragmentPacket(
            PacketHeader(
                type = PacketType.AUDIO_PCM,
                streamId = packet.streamId,
                frameId = packet.sequenceNumber,
                fragmentIndex = 0,
                fragmentCount = 1,
                timestampUs = packet.presentationTimeUs,
                flags = 0,
                payloadSize = packet.data.size,
            ),
            packet.data,
        ),
    )

    fun decodeAudioPcm(fragment: FragmentPacket): PcmAudioPacket {
        require(fragment.header.type == PacketType.AUDIO_PCM) { "Packet is not AUDIO_PCM" }
        require(fragment.payload.isNotEmpty()) { "Invalid empty audio packet" }
        return PcmAudioPacket(
            streamId = fragment.header.streamId,
            sequenceNumber = fragment.header.frameId,
            presentationTimeUs = fragment.header.timestampUs,
            data = fragment.payload,
        )
    }

    fun decode(datagram: ByteArray, length: Int = datagram.size): FragmentPacket {
        require(length >= PacketHeader.BYTE_SIZE) { "Datagram too small" }
        val buffer = ByteBuffer.wrap(datagram, 0, length).order(ByteOrder.BIG_ENDIAN)
        require(buffer.int == PacketHeader.MAGIC) { "Bad packet magic" }
        require(buffer.get().toInt() == PacketHeader.VERSION) { "Unsupported packet version" }
        val type = PacketType.fromId(buffer.get().toInt())
        val streamId = buffer.int
        val frameId = buffer.long
        val fragmentIndex = buffer.int
        val fragmentCount = buffer.int
        val timestampUs = buffer.long
        val flags = buffer.int
        val payloadSize = buffer.int
        require(payloadSize >= 0 && payloadSize <= length - PacketHeader.BYTE_SIZE) { "Invalid payload size" }
        val payload = ByteArray(payloadSize)
        buffer.get(payload)
        return FragmentPacket(
            PacketHeader(type, streamId, frameId, fragmentIndex, fragmentCount, timestampUs, flags, payloadSize),
            payload,
        )
    }

    fun isNoctDockPacket(data: ByteArray, length: Int): Boolean {
        if (length < 4) return false
        val magic = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        return magic == PacketHeader.MAGIC
    }

    private fun encodeFragment(fragment: FragmentPacket): ByteArray {
        val header = fragment.header
        require(header.payloadSize == fragment.payload.size) { "Payload size mismatch" }
        return ByteBuffer
            .allocate(PacketHeader.BYTE_SIZE + fragment.payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(PacketHeader.MAGIC)
            .put(PacketHeader.VERSION.toByte())
            .put(header.type.id.toByte())
            .putInt(header.streamId)
            .putLong(header.frameId)
            .putInt(header.fragmentIndex)
            .putInt(header.fragmentCount)
            .putLong(header.timestampUs)
            .putInt(header.flags)
            .putInt(header.payloadSize)
            .put(fragment.payload)
            .array()
    }
}

class FrameAssembler(private val timeoutMs: Long = 80L, private val maxFrames: Int = 24) {
    private companion object {
        const val REPAIR_REQUEST_START_DELAY_MS = 12L
        const val REPAIR_RETRY_INTERVAL_MS = 32L
        const val REPAIR_SWEEP_INTERVAL_MS = 16L
        const val MAX_REPAIR_REQUESTS_PER_SWEEP = 2
    }

    private data class PendingFrame(
        val streamId: Int,
        val frameId: Long,
        val timestampUs: Long,
        val keyFrame: Boolean,
        val fragmentCount: Int,
        val createdAtMs: Long,
        val fragments: Array<ByteArray?>,
    )

    private val pending = linkedMapOf<Long, PendingFrame>()
    private val repairRequests = ArrayDeque<FragmentRepairRequestPacket>()
    private val repairRequestedAtMs = mutableMapOf<Long, Long>()
    private var lastRepairSweepAtMs = 0L
    var droppedFrames: Int = 0
        private set

    fun accept(fragment: FragmentPacket, nowMs: Long = System.currentTimeMillis()): VideoFramePacket? {
        if (fragment.header.type != PacketType.VIDEO_FRAGMENT) return null
        prune(nowMs)
        val header = fragment.header
        if (header.fragmentIndex !in 0 until header.fragmentCount) return null
        val frame =
            pending.getOrPut(header.frameId) {
                if (pending.size >= maxFrames) {
                    val oldest = pending.keys.first()
                    pending.remove(oldest)
                    droppedFrames++
                }
                PendingFrame(
                    streamId = header.streamId,
                    frameId = header.frameId,
                    timestampUs = header.timestampUs,
                    keyFrame = header.isKeyFrame,
                    fragmentCount = header.fragmentCount,
                    createdAtMs = nowMs,
                    fragments = arrayOfNulls(header.fragmentCount),
                )
            }
        if (frame.fragmentCount != header.fragmentCount) return null
        frame.fragments[header.fragmentIndex] = fragment.payload
        if (frame.fragments.any { it == null }) return null
        pending.remove(header.frameId)
        repairRequestedAtMs.remove(header.frameId)
        val size = frame.fragments.sumOf { it?.size ?: 0 }
        val data = ByteArray(size)
        var offset = 0
        frame.fragments.forEach { part ->
            val bytes = requireNotNull(part)
            bytes.copyInto(data, offset)
            offset += bytes.size
        }
        return VideoFramePacket(frame.streamId, frame.frameId, frame.timestampUs, frame.keyFrame, data)
    }

    fun drainRepairRequests(nowMs: Long = System.currentTimeMillis()): List<FragmentRepairRequestPacket> {
        if (nowMs - lastRepairSweepAtMs >= REPAIR_SWEEP_INTERVAL_MS) {
            lastRepairSweepAtMs = nowMs
            queueRepairRequestsForPending(nowMs)
        }
        return buildList {
            while (repairRequests.isNotEmpty()) add(repairRequests.removeFirst())
        }
    }

    fun prune(nowMs: Long = System.currentTimeMillis()): Int {
        val expired = pending.filterValues { nowMs - it.createdAtMs > timeoutMs }.keys
        expired.forEach { pending.remove(it) }
        expired.forEach { repairRequestedAtMs.remove(it) }
        droppedFrames += expired.size
        return expired.size
    }

    private fun maybeQueueRepairRequest(frame: PendingFrame, nowMs: Long): Boolean {
        if (nowMs - frame.createdAtMs < REPAIR_REQUEST_START_DELAY_MS) return false
        val lastRequestAtMs = repairRequestedAtMs[frame.frameId]
        if (lastRequestAtMs != null && nowMs - lastRequestAtMs < REPAIR_RETRY_INTERVAL_MS) return false
        val missing =
            frame.fragments.mapIndexedNotNull { index, bytes ->
                if (bytes == null) index else null
            }
        if (missing.isEmpty() || missing.size > PacketCodec.MAX_REPAIR_FRAGMENT_INDEXES) return false
        repairRequestedAtMs[frame.frameId] = nowMs
        repairRequests.addLast(FragmentRepairRequestPacket(frame.streamId, frame.frameId, missing.toIntArray()))
        return true
    }

    private fun queueRepairRequestsForPending(nowMs: Long) {
        var queued = 0
        pending.values.forEach { frame ->
            if (queued >= MAX_REPAIR_REQUESTS_PER_SWEEP) return
            if (maybeQueueRepairRequest(frame, nowMs)) {
                queued += 1
            }
        }
    }
}
