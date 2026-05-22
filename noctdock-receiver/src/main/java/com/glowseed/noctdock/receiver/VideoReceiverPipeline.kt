package com.glowseed.noctdock.receiver

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.glowseed.noctdock.core.AudioConfig
import com.glowseed.noctdock.core.AudioJitterBuffer
import com.glowseed.noctdock.core.AudioJitterDecision
import com.glowseed.noctdock.core.FragmentRepairRequestPacket
import com.glowseed.noctdock.core.FrameAssembler
import com.glowseed.noctdock.core.LatencyPriority
import com.glowseed.noctdock.core.LatencyTuning
import com.glowseed.noctdock.core.NoctConstants
import com.glowseed.noctdock.core.NoctLog
import com.glowseed.noctdock.core.PacketCodec
import com.glowseed.noctdock.core.PacketType
import com.glowseed.noctdock.core.PcmAudioPacket
import com.glowseed.noctdock.core.ReceiverCapabilitiesPacket
import com.glowseed.noctdock.core.ReceiverFeedbackPacket
import com.glowseed.noctdock.core.ReceiverTransportKind
import com.glowseed.noctdock.core.ReceiverVideoCapabilities
import com.glowseed.noctdock.core.ReconnectPolicy
import com.glowseed.noctdock.core.ReconnectState
import com.glowseed.noctdock.core.StreamConfigPacket
import com.glowseed.noctdock.core.StreamMetrics
import com.glowseed.noctdock.core.StreamQualityConfig
import com.glowseed.noctdock.core.StreamSourceMetadata
import com.glowseed.noctdock.core.VideoFramePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

data class ReceiverPlaybackState(
    val active: Boolean = false,
    val senderAddress: String = "",
    val decoderName: String = "Not selected",
    val metrics: StreamMetrics = StreamMetrics(),
    val sourceMetadata: StreamSourceMetadata = StreamSourceMetadata.unknown(),
    val error: String? = null,
)

private const val STREAM_LOG_TAG = "Stream"

private data class ConnectionTestStageTracker(
    val testId: Int,
    val stageIndex: Int,
    val targetMbps: Int,
    val createdAtMillis: Long = System.currentTimeMillis(),
    var expectedPackets: Int = 0,
    var receivedPackets: Int = 0,
    var lastSequenceNumber: Long? = null,
    var lastArrivalUs: Long? = null,
    var lastSentAtUs: Long? = null,
    val jitterDeltasUs: MutableList<Long> = mutableListOf(),
) {
    fun accept(sequenceNumber: Long, sentAtUs: Long, stageComplete: Boolean, expectedPacketsFromSender: Int) {
        receivedPackets += 1
        if (stageComplete && expectedPacketsFromSender > 0) expectedPackets = expectedPacketsFromSender
        lastSequenceNumber?.let { previousSequence ->
            if (sequenceNumber > previousSequence + 1) {
                expectedPackets = maxOf(expectedPackets, sequenceNumber.toInt() + 1)
            }
        }
        val arrivalUs = System.nanoTime() / 1_000L
        if (lastArrivalUs != null && lastSentAtUs != null) {
            val senderGap = sentAtUs - lastSentAtUs!!
            val receiverGap = arrivalUs - lastArrivalUs!!
            jitterDeltasUs += kotlin.math.abs(receiverGap - senderGap)
        }
        lastSequenceNumber = sequenceNumber
        lastArrivalUs = arrivalUs
        lastSentAtUs = sentAtUs
        if (stageComplete && expectedPackets == 0) {
            expectedPackets = maxOf(expectedPacketsFromSender, sequenceNumber.toInt() + 1)
        }
    }
}

class FrameReassembler(private var assembler: FrameAssembler = FrameAssembler(timeoutMs = 45L, maxFrames = 8)) {
    private var currentPriority: LatencyPriority = LatencyPriority.Balanced
    fun accept(packet: ByteArray, length: Int): VideoFramePacket? {
        val fragment = PacketCodec.decode(packet, length)
        return assembler.accept(fragment)
    }

    fun prune(): Int = assembler.prune()

    val droppedFrames: Int get() = assembler.droppedFrames

    fun drainRepairRequests(): List<FragmentRepairRequestPacket> = assembler.drainRepairRequests()

    fun tuneFor(priority: LatencyPriority) {
        if (priority == currentPriority) return
        currentPriority = priority
        assembler =
            FrameAssembler(
                timeoutMs = LatencyTuning.reassemblyTimeoutMs(priority),
                maxFrames = LatencyTuning.reassemblyWindowFrames(priority),
            )
    }

    fun reset() {
        assembler =
            FrameAssembler(
                timeoutMs = LatencyTuning.reassemblyTimeoutMs(currentPriority),
                maxFrames = LatencyTuning.reassemblyWindowFrames(currentPriority),
            )
    }
}

class H264VideoDecoder(private val onFrameRendered: () -> Unit, private val onError: (Throwable) -> Unit) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val frameQueue = Channel<VideoFramePacket>(capacity = 4)
    private val codecLock = Any()
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var config: StreamConfigPacket? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private val running = AtomicBoolean(false)
    private var senderToReceiverVideoOffsetUs: Long? = null

    @Volatile
    private var queuedFrames = 0
    private var waitingForFreshKeyFrame = false
    val queueDepth: Int get() = queuedFrames
    var decoderName: String = "Not selected"
        private set

    fun attachSurface(nextSurface: Surface) {
        synchronized(codecLock) {
            surface = nextSurface
            config?.let { configureLocked(it) }
        }
    }

    fun detachSurface() {
        synchronized(codecLock) {
            releaseCodecLocked()
            surface = null
        }
    }

    fun configure(nextConfig: StreamConfigPacket) {
        synchronized(codecLock) {
            configureLocked(nextConfig)
        }
    }

    private fun configureLocked(nextConfig: StreamConfigPacket) {
        if (codec != null && config?.sameDecoderConfig(nextConfig) == true) {
            config = nextConfig
            return
        }
        config = nextConfig
        senderToReceiverVideoOffsetUs = null
        queuedFrames = 0
        waitingForFreshKeyFrame = false
        val outputSurface = surface ?: return
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                outputSurface.setFrameRate(nextConfig.fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
            }
        }
        releaseCodecLocked()
        runCatching {
            val mediaCodec = MediaCodec.createDecoderByType(nextConfig.mime)
            decoderName = mediaCodec.name
            val format =
                MediaFormat.createVideoFormat(nextConfig.mime, nextConfig.width, nextConfig.height).apply {
                    if (nextConfig.codecConfigSps.isNotEmpty()) setByteBuffer("csd-0", ByteBuffer.wrap(nextConfig.codecConfigSps))
                    if (nextConfig.codecConfigPps.isNotEmpty()) setByteBuffer("csd-1", ByteBuffer.wrap(nextConfig.codecConfigPps))
                    if (Build.VERSION.SDK_INT >= 30) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                    if (Build.VERSION.SDK_INT >= 31) setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 1)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, nextConfig.width * nextConfig.height)
                }
            mediaCodec.configure(format, outputSurface, null, 0)
            mediaCodec.start()
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    mediaCodec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_LOW_LATENCY, 1) })
                }
            }
            codec = mediaCodec
            if (running.compareAndSet(false, true)) {
                scope.launch { decodeLoop() }
            }
        }.onFailure(onError)
    }

    fun enqueue(frame: VideoFramePacket) {
        if (queuedFrames >= 2) {
            drainQueuedFrames()
            waitingForFreshKeyFrame = true
        }
        if (waitingForFreshKeyFrame && !frame.keyFrame) return
        if (frame.keyFrame) waitingForFreshKeyFrame = false
        queuedFrames = (queuedFrames + 1).coerceAtMost(8)
        if (!frameQueue.trySend(frame).isSuccess) {
            frameQueue.tryReceive()
            frameQueue.trySend(frame)
        }
    }

    fun requireFreshKeyFrame() {
        drainQueuedFrames()
        waitingForFreshKeyFrame = true
    }

    fun stop() {
        running.set(false)
        senderToReceiverVideoOffsetUs = null
        frameQueue.trySend(VideoFramePacket(0, 0, 0, false, ByteArray(0)))
        synchronized(codecLock) {
            releaseCodecLocked()
        }
    }

    private suspend fun decodeLoop() {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_VIDEO) }
        val info = MediaCodec.BufferInfo()
        while (running.get()) {
            val frame = frameQueue.receiveCatching().getOrNull() ?: break
            queuedFrames = (queuedFrames - 1).coerceAtLeast(0)
            if (frame.data.isEmpty()) continue
            synchronized(codecLock) {
                val currentCodec = codec ?: return@synchronized
                runCatching {
                    drainOutput(currentCodec, info, firstTimeoutUs = 0)
                    val inputIndex = currentCodec.dequeueInputBuffer(1_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = currentCodec.getInputBuffer(inputIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(frame.data)
                        val receiverPresentationUs = receiverPresentationTimeUs(frame.presentationTimeUs)
                        currentCodec.queueInputBuffer(inputIndex, 0, frame.data.size, receiverPresentationUs, 0)
                    }
                    drainOutput(currentCodec, info, firstTimeoutUs = 1_000)
                }.onFailure(onError)
            }
        }
    }

    private fun drainQueuedFrames() {
        while (frameQueue.tryReceive().isSuccess) {
            queuedFrames = (queuedFrames - 1).coerceAtLeast(0)
        }
    }

    private fun receiverPresentationTimeUs(senderPresentationUs: Long): Long {
        val nowUs = System.nanoTime() / 1_000L
        val offset = senderToReceiverVideoOffsetUs ?: (nowUs - senderPresentationUs).also {
            senderToReceiverVideoOffsetUs = it
        }
        return (senderPresentationUs + offset).coerceAtLeast(0L)
    }

    private fun drainOutput(mediaCodec: MediaCodec, info: MediaCodec.BufferInfo, firstTimeoutUs: Long) {
        var timeoutUs = firstTimeoutUs
        while (true) {
            when (val outputIndex = mediaCodec.dequeueOutputBuffer(info, timeoutUs)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return

                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit

                else -> if (outputIndex >= 0) {
                    mediaCodec.releaseOutputBuffer(outputIndex, true)
                    onFrameRendered()
                }
            }
            timeoutUs = 0
        }
    }

    private fun releaseCodecLocked() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
    }

    private fun StreamConfigPacket.sameDecoderConfig(other: StreamConfigPacket): Boolean = width == other.width &&
        height == other.height &&
        fps == other.fps &&
        mime == other.mime &&
        codecConfigSps.contentEquals(other.codecConfigSps) &&
        codecConfigPps.contentEquals(other.codecConfigPps)

    fun updateSurfaceSize(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    fun metricsConfig(): StreamMetrics = StreamMetrics(
        receiverDecoderMime = config?.mime.orEmpty(),
        receiverSurfaceWidth = surfaceWidth,
        receiverSurfaceHeight = surfaceHeight,
    )
}

class PcmAudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var jitterBuffer = AudioJitterBuffer(targetBufferMs = 32, maxBufferMs = 64, maxLateMs = 36)
    private val lock = Any()
    private var audioTrack: AudioTrack? = null
    private var currentConfig: AudioConfig? = null
    private var currentPriority: LatencyPriority = LatencyPriority.Balanced
    private val running = AtomicBoolean(false)

    fun configure(config: AudioConfig): Boolean {
        synchronized(lock) {
            val track = audioTrack
            if (currentConfig == config && track?.state == AudioTrack.STATE_INITIALIZED) {
                runCatching {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                }.onFailure {
                    releaseTrack()
                    return false
                }
                return true
            }
        }
        releaseTrack()
        synchronized(lock) { jitterBuffer.configure() }
        val channelMask = if (config.channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuffer = AudioTrack.getMinBufferSize(config.sampleRateHz, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val targetBuffer = (config.sampleRateHz * config.channelCount.coerceAtLeast(1) * 2 * 45) / 1_000
        val track =
            runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(config.sampleRateHz)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(channelMask)
                            .build(),
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer, targetBuffer, 4096))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build()
            }.getOrNull() ?: return false
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            return false
        }
        val started =
            runCatching {
                track.play()
            }.isSuccess
        if (!started) {
            runCatching { track.release() }
            return false
        }
        val bytesPerFrame = config.channelCount.coerceAtLeast(1) * 2
        val targetFrames = (config.sampleRateHz * LatencyTuning.audioTargetBufferMs(currentPriority)) / 1_000
        val minFrames = (minBuffer / bytesPerFrame).coerceAtLeast(1)
        runCatching { track.setBufferSizeInFrames(maxOf(targetFrames, minFrames)) }
        synchronized(lock) {
            audioTrack = track
            currentConfig = config
        }
        if (running.compareAndSet(false, true)) {
            scope.launch { playLoop() }
        }
        return true
    }

    fun tuneFor(priority: LatencyPriority) {
        synchronized(lock) {
            if (priority == currentPriority) return
            currentPriority = priority
            jitterBuffer =
                AudioJitterBuffer(
                    targetBufferMs = LatencyTuning.audioTargetBufferMs(priority),
                    maxBufferMs = LatencyTuning.audioMaxBufferMs(priority),
                    maxLateMs = LatencyTuning.audioMaxLateMs(priority),
                )
            jitterBuffer.configure()
        }
    }

    fun enqueue(packet: PcmAudioPacket) {
        synchronized(lock) {
            if (audioTrack == null) return
            jitterBuffer.offer(packet, nowUs())
        }
    }

    fun stop() {
        running.set(false)
        synchronized(lock) { jitterBuffer.stop() }
        releaseTrack()
    }

    fun snapshot(): com.glowseed.noctdock.core.AudioSyncSnapshot = synchronized(lock) { jitterBuffer.snapshot(nowUs()) }

    private suspend fun playLoop() {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        while (running.get()) {
            when (val decision = synchronized(lock) { jitterBuffer.next(nowUs()) }) {
                is AudioJitterDecision.Play -> {
                    val track = synchronized(lock) { audioTrack }
                    if (track == null) {
                        delay(5)
                    } else {
                        val written =
                            runCatching {
                                track.write(decision.packet.data, 0, decision.packet.data.size)
                            }.getOrDefault(AudioTrack.ERROR_INVALID_OPERATION)
                        if (written < 0) {
                            running.set(false)
                            releaseTrack()
                        }
                    }
                }

                is AudioJitterDecision.Wait -> delay(decision.delayMs)

                AudioJitterDecision.Underrun -> delay(5)
            }
        }
    }

    private fun releaseTrack() {
        val track =
            synchronized(lock) {
                val current = audioTrack
                audioTrack = null
                currentConfig = null
                current
            }
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
    }

    private fun nowUs(): Long = System.nanoTime() / 1_000L
}

class ReceiverSessionController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ReceiverPlaybackState())
    val state: StateFlow<ReceiverPlaybackState> = _state.asStateFlow()
    private var receiver: UdpVideoReceiver? = null
    private var metricsJob: Job? = null
    private var framesReceived = 0
    private var framesRendered = 0
    private var decoderErrors = 0
    private var lastVideoPacketAtMillis = System.currentTimeMillis()
    private var activeStreamId = 0
    private var lastReassemblyDrops = 0
    private var reportedVideoDrops = 0
    private var lastAcceptedFrameId = 0L
    private var waitingForVideoKeyFrame = true
    private var reconnectAttempts = 0
    private var wifiLock: WifiManager.WifiLock? = null
    private val streamQualityConfig = StreamQualityConfig()
    private val audioPlayer = PcmAudioPlayer()
    private val decoder =
        H264VideoDecoder(
            onFrameRendered = { framesRendered++ },
            onError = { error ->
                decoderErrors++
                _state.value = _state.value.copy(error = error.message, metrics = _state.value.metrics.copy(decoderErrors = decoderErrors))
            },
        )

    fun attachSurface(surface: Surface) {
        decoder.attachSurface(surface)
    }

    fun updateSurfaceSize(width: Int, height: Int) {
        decoder.updateSurfaceSize(width, height)
    }

    fun detachSurface() {
        decoder.detachSurface()
    }

    fun start(
        onPairingPayload: (DatagramSocket, InetAddress, Int, String) -> Unit,
        onStreamEnded: () -> Unit = {},
        receiverVideoCapabilities: () -> ReceiverVideoCapabilities = { ReceiverVideoCapabilities() },
    ) {
        if (receiver != null) return
        lateinit var udpReceiver: UdpVideoReceiver
        udpReceiver =
            UdpVideoReceiver(
                onPairingPayload = onPairingPayload,
                onConfig = { config ->
                    markVideoPacketSeen()
                    if (activeStreamId == 0 || config.streamId == activeStreamId) {
                        activeStreamId = config.streamId
                        decoderErrors = 0
                        udpReceiver.resetReassembly()
                        lastReassemblyDrops = 0
                        reportedVideoDrops = 0
                        lastAcceptedFrameId = 0L
                        waitingForVideoKeyFrame = true
                        acquireLowLatencyWifiLock()
                        audioPlayer.tuneFor(config.latencyPriority)
                        decoder.configure(config)
                        _state.value =
                            _state.value.copy(
                                active = true,
                                decoderName = decoder.decoderName,
                                sourceMetadata = config.sourceMetadata,
                                error = null,
                                metrics =
                                _state.value.metrics.copy(
                                    requestedWidth = config.width,
                                    requestedHeight = config.height,
                                    codecMime = config.mime,
                                ),
                            )
                    }
                },
                onFrame = frameHandler@{ frame, address, drops ->
                    markVideoPacketSeen()
                    if (activeStreamId != 0 && frame.streamId != activeStreamId) return@frameHandler
                    activeStreamId = frame.streamId
                    var recoveryNeeded = false
                    if (drops > lastReassemblyDrops) {
                        reportedVideoDrops += drops - lastReassemblyDrops
                        lastReassemblyDrops = drops
                        recoveryNeeded = true
                    }
                    if (lastAcceptedFrameId > 0L && frame.frameId > lastAcceptedFrameId + 1L) {
                        reportedVideoDrops += ((frame.frameId - lastAcceptedFrameId - 1L).coerceAtMost(Int.MAX_VALUE.toLong())).toInt()
                        recoveryNeeded = true
                    }
                    if (recoveryNeeded) {
                        waitingForVideoKeyFrame = true
                        decoder.requireFreshKeyFrame()
                        udpReceiver.sendFeedback(buildFeedback(reassemblyDrops = reportedVideoDrops))
                    }
                    if (waitingForVideoKeyFrame && !frame.keyFrame) {
                        reportedVideoDrops += 1
                        lastAcceptedFrameId = frame.frameId
                        _state.value =
                            _state.value.copy(
                                active = true,
                                senderAddress = address.hostAddress.orEmpty(),
                                decoderName = decoder.decoderName,
                                metrics = _state.value.metrics.copy(reassemblyDrops = reportedVideoDrops),
                            )
                        return@frameHandler
                    }
                    waitingForVideoKeyFrame = false
                    lastAcceptedFrameId = frame.frameId
                    framesReceived++
                    decoder.enqueue(frame)
                    _state.value =
                        _state.value.copy(
                            active = true,
                            senderAddress = address.hostAddress.orEmpty(),
                            decoderName = decoder.decoderName,
                            metrics = _state.value.metrics.copy(reassemblyDrops = reportedVideoDrops),
                        )
                },
                onHeartbeat = { address ->
                    markVideoPacketSeen()
                    if (_state.value.active) {
                        _state.value = _state.value.copy(senderAddress = address.hostAddress.orEmpty())
                    }
                },
                onAudioConfig = { config ->
                    if (!audioPlayer.configure(config)) {
                        _state.value = _state.value.copy(error = "TV sound could not start on this device. Try Retroid Sound.")
                    }
                },
                onAudioPcm = { packet ->
                    audioPlayer.enqueue(packet)
                },
                onRepairRequests = { socket, address, port, requests ->
                    requests.forEach { request ->
                        runCatching {
                            val bytes = PacketCodec.encodeFragmentRepairRequest(request)
                            socket.send(DatagramPacket(bytes, bytes.size, address, port))
                        }
                    }
                },
                transportProvider = { currentTransportKind() },
                onStop = {
                    resetPlaybackSession()
                    onStreamEnded()
                },
                onCapabilitiesQuery = receiverVideoCapabilities,
            )
        receiver = udpReceiver
        udpReceiver.start()
        metricsJob?.cancel()
        metricsJob = scope.launch {
            var lastMetricsAtNs = System.nanoTime()
            while (true) {
                delay(1000)
                val nowNs = System.nanoTime()
                val elapsedSeconds = ((nowNs - lastMetricsAtNs).coerceAtLeast(1L)).toDouble() / 1_000_000_000.0
                lastMetricsAtNs = nowNs
                val incomingFps = (framesReceived / elapsedSeconds).toInt().coerceAtLeast(0)
                val renderFps = (framesRendered / elapsedSeconds).toInt().coerceAtLeast(0)
                val current = _state.value
                val nowMillis = System.currentTimeMillis()
                val reconnectState =
                    ReconnectPolicy.state(
                        nowMillis = nowMillis,
                        lastHeartbeatMillis = lastVideoPacketAtMillis,
                        attempts = reconnectAttempts,
                        config = streamQualityConfig,
                    )
                if (reconnectState == ReconnectState.Connected) {
                    if (reconnectAttempts > 0) {
                        reconnectAttempts = 0
                        if (_state.value.error == "Reconnecting to handheld...") {
                            _state.value = _state.value.copy(error = null)
                        }
                    }
                } else if (current.active) {
                    reconnectAttempts = (reconnectAttempts + 1).coerceAtMost(8)
                    val updatedState =
                        ReconnectPolicy.state(
                            nowMillis = nowMillis,
                            lastHeartbeatMillis = lastVideoPacketAtMillis,
                            attempts = reconnectAttempts,
                            config = streamQualityConfig,
                        )
                    if (updatedState == ReconnectState.Failed) {
                        resetPlaybackSession()
                        onStreamEnded()
                        continue
                    }
                    if (_state.value.error != "Reconnecting to handheld...") {
                        _state.value = _state.value.copy(error = "Reconnecting to handheld...")
                    }
                }
                val audioSnapshot = audioPlayer.snapshot()
                val decoderMetrics = decoder.metricsConfig()
                NoctLog.debug(
                    STREAM_LOG_TAG,
                    "receiver incomingFps=$incomingFps renderFps=$renderFps reassemblyDrops=${current.metrics.reassemblyDrops} decoderErrors=$decoderErrors audioPackets=${audioSnapshot.packetsReceived} audioUnderruns=${audioSnapshot.underruns} audioDrops=${audioSnapshot.drops} audioBuffer=${audioSnapshot.bufferMs}ms",
                )
                _state.value =
                    current.copy(
                        metrics =
                        current.metrics.copy(
                            receivedFps = incomingFps,
                            fps = renderFps,
                            decoderErrors = decoderErrors,
                            audioPacketsReceived = audioSnapshot.packetsReceived,
                            audioUnderruns = audioSnapshot.underruns,
                            audioDrops = audioSnapshot.drops,
                            audioBufferMs = audioSnapshot.bufferMs,
                            avOffsetMs = audioSnapshot.estimatedAvOffsetMs,
                            receiverDecoderMime = decoderMetrics.receiverDecoderMime,
                            receiverSurfaceWidth = decoderMetrics.receiverSurfaceWidth,
                            receiverSurfaceHeight = decoderMetrics.receiverSurfaceHeight,
                        ),
                    )
                udpReceiver.sendFeedback(buildFeedback(renderFps = renderFps, audioSnapshot = audioSnapshot))
                framesReceived = 0
                framesRendered = 0
            }
        }
    }

    private fun buildFeedback(
        renderFps: Int = _state.value.metrics.fps,
        reassemblyDrops: Int = _state.value.metrics.reassemblyDrops,
        audioSnapshot: com.glowseed.noctdock.core.AudioSyncSnapshot = audioPlayer.snapshot(),
    ): ReceiverFeedbackPacket = ReceiverFeedbackPacket(
        streamId = activeStreamId,
        receivedFps = renderFps,
        reassemblyDrops = reassemblyDrops,
        decoderErrors = decoderErrors,
        queueDepth = decoder.queueDepth,
        audioPacketsReceived = audioSnapshot.packetsReceived,
        audioUnderruns = audioSnapshot.underruns,
        audioDrops = audioSnapshot.drops,
        audioBufferMs = audioSnapshot.bufferMs,
        avOffsetMs = audioSnapshot.estimatedAvOffsetMs,
    )

    fun clearStreamSenderBinding() {
        receiver?.clearStreamSenderBinding()
    }

    private fun resetPlaybackSession() {
        receiver?.clearStreamSenderBinding()
        audioPlayer.stop()
        decoder.stop()
        releaseLowLatencyWifiLock()
        framesReceived = 0
        framesRendered = 0
        waitingForVideoKeyFrame = true
        activeStreamId = 0
        reconnectAttempts = 0
        _state.value = _state.value.copy(active = false, error = null)
    }

    fun stop() {
        receiver?.stop()
        receiver = null
        metricsJob?.cancel()
        metricsJob = null
        audioPlayer.stop()
        decoder.stop()
        releaseLowLatencyWifiLock()
        framesReceived = 0
        framesRendered = 0
        _state.value = ReceiverPlaybackState()
    }

    private fun markVideoPacketSeen() {
        lastVideoPacketAtMillis = System.currentTimeMillis()
        if (reconnectAttempts > 0) {
            reconnectAttempts = 0
            if (_state.value.error == "Reconnecting to handheld...") {
                _state.value = _state.value.copy(error = null)
            }
        }
    }

    private fun acquireLowLatencyWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java) ?: return
        wifiLock =
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "NoctDockReceiverLowLatency").apply {
                setReferenceCounted(false)
                runCatching { acquire() }
            }
    }

    private fun releaseLowLatencyWifiLock() {
        runCatching {
            wifiLock?.takeIf { it.isHeld }?.release()
        }
        wifiLock = null
    }

    private fun currentTransportKind(): ReceiverTransportKind {
        val connectivityManager =
            context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return ReceiverTransportKind.UNKNOWN
        val network = connectivityManager.activeNetwork ?: return ReceiverTransportKind.UNKNOWN
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ReceiverTransportKind.UNKNOWN
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ReceiverTransportKind.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ReceiverTransportKind.WIFI
            else -> ReceiverTransportKind.UNKNOWN
        }
    }
}

class UdpVideoReceiver(
    private val onPairingPayload: (DatagramSocket, InetAddress, Int, String) -> Unit,
    private val onConfig: (StreamConfigPacket) -> Unit,
    private val onFrame: (VideoFramePacket, InetAddress, Int) -> Unit,
    private val onHeartbeat: (InetAddress) -> Unit,
    private val onAudioConfig: (AudioConfig) -> Unit,
    private val onAudioPcm: (PcmAudioPacket) -> Unit,
    private val onRepairRequests: (DatagramSocket, InetAddress, Int, List<FragmentRepairRequestPacket>) -> Unit,
    private val transportProvider: () -> ReceiverTransportKind,
    private val onStop: () -> Unit,
    private val onCapabilitiesQuery: () -> ReceiverVideoCapabilities,
) {
    private companion object {
        const val MAX_CONNECTION_TEST_TRACKERS = 32
        const val CONNECTION_TEST_TRACKER_TTL_MS = 30_000L
    }
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private val reassembler = FrameReassembler()
    private var senderAddress: InetAddress? = null
    private var senderPort: Int = 0
    private var boundStreamSenderHost: String? = null
    private var boundStreamSenderPort: Int = 0
    private val connectionTests = linkedMapOf<String, ConnectionTestStageTracker>()

    fun resetReassembly() {
        reassembler.reset()
    }

    fun clearStreamSenderBinding() {
        boundStreamSenderHost = null
        boundStreamSenderPort = 0
        senderAddress = null
        senderPort = 0
        resetReassembly()
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_VIDEO) }
            runCatching {
                DatagramSocket(null).use { boundSocket ->
                    boundSocket.reuseAddress = true
                    runCatching { boundSocket.receiveBufferSize = 1_048_576 }
                    runCatching { boundSocket.trafficClass = 0x10 }
                    boundSocket.bind(InetSocketAddress(NoctConstants.DEFAULT_DISCOVERY_PORT))
                    socket = boundSocket
                    val buffer = ByteArray(4096)
                    while (running.get()) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        boundSocket.receive(packet)
                        if (PacketCodec.isNoctDockPacket(packet.data, packet.length)) {
                            handleVideoPacket(packet.data, packet.length, packet.address, packet.port)
                        } else {
                            onPairingPayload(boundSocket, packet.address, packet.port, String(packet.data, packet.offset, packet.length))
                        }
                    }
                }
            }.onFailure { error ->
                if (running.get()) {
                    NoctLog.warn(STREAM_LOG_TAG, "Receiver socket stopped unexpectedly", error)
                }
            }.also {
                socket = null
                running.set(false)
            }
        }
    }

    fun sendFeedback(feedback: ReceiverFeedbackPacket) {
        val address = senderAddress ?: return
        val port = senderPort.takeIf { it > 0 } ?: return
        val feedbackSocket = socket ?: return
        runCatching {
            val bytes = PacketCodec.encodeReceiverFeedback(feedback)
            feedbackSocket.send(DatagramPacket(bytes, bytes.size, address, port))
        }
    }

    fun stop() {
        running.set(false)
        socket?.close()
        job.cancel()
    }

    private fun handleVideoPacket(bytes: ByteArray, length: Int, address: InetAddress, port: Int) {
        runCatching {
            val fragment = PacketCodec.decode(bytes, length)
            if (!acceptsStreamPacket(fragment.header.type, address, port)) return
            senderAddress = address
            senderPort = port
            when (fragment.header.type) {
                PacketType.CONFIG -> {
                    val config = PacketCodec.decodeConfig(fragment)
                    reassembler.tuneFor(config.latencyPriority)
                    onConfig(config)
                }

                PacketType.VIDEO_FRAGMENT -> {
                    reassembler.prune()
                    reassembler.accept(bytes, length)?.let { frame -> onFrame(frame, address, reassembler.droppedFrames) }
                    val requests = reassembler.drainRepairRequests()
                    if (requests.isNotEmpty()) {
                        socket?.let { activeSocket -> onRepairRequests(activeSocket, address, port, requests) }
                    }
                }

                PacketType.HEARTBEAT -> onHeartbeat(address)

                PacketType.AUDIO_CONFIG -> onAudioConfig(PacketCodec.decodeAudioConfig(fragment))

                PacketType.AUDIO_PCM -> onAudioPcm(PacketCodec.decodeAudioPcm(fragment))

                PacketType.RECEIVER_FEEDBACK -> Unit

                PacketType.RECEIVER_CAPABILITIES -> respondCapabilities(fragment, address, port)

                PacketType.CONNECTION_TEST -> echoConnectionTest(fragment, address, port)

                PacketType.FRAGMENT_REPAIR_REQUEST -> Unit

                PacketType.STOP -> onStop()
            }
        }
    }

    private fun acceptsStreamPacket(type: PacketType, address: InetAddress, port: Int): Boolean {
        if (type == PacketType.CONNECTION_TEST) return true
        val host = address.hostAddress.orEmpty()
        if (host.isBlank()) return false
        val boundHost = boundStreamSenderHost
        if (boundHost == null) {
            boundStreamSenderHost = host
            boundStreamSenderPort = port
            return true
        }
        return boundHost == host && (boundStreamSenderPort == 0 || port == boundStreamSenderPort)
    }

    private fun respondCapabilities(fragment: com.glowseed.noctdock.core.FragmentPacket, address: InetAddress, port: Int) {
        val request = PacketCodec.decodeReceiverCapabilities(fragment)
        val response =
            ReceiverCapabilitiesPacket(
                streamId = request.streamId,
                capabilities = onCapabilitiesQuery(),
            )
        val responseBytes = PacketCodec.encodeReceiverCapabilities(response)
        socket?.send(DatagramPacket(responseBytes, responseBytes.size, address, port))
    }

    private fun pruneConnectionTests(nowMillis: Long = System.currentTimeMillis()) {
        connectionTests.entries.removeIf { (_, tracker) -> nowMillis - tracker.createdAtMillis > CONNECTION_TEST_TRACKER_TTL_MS }
        while (connectionTests.size > MAX_CONNECTION_TEST_TRACKERS) {
            connectionTests.remove(connectionTests.keys.first())
        }
    }

    private fun echoConnectionTest(fragment: com.glowseed.noctdock.core.FragmentPacket, address: InetAddress, port: Int) {
        pruneConnectionTests()
        val request = PacketCodec.decodeConnectionTest(fragment)
        val key = "${request.testId}:${request.stageIndex}"
        val tracker =
            connectionTests.getOrPut(key) {
                ConnectionTestStageTracker(
                    testId = request.testId,
                    stageIndex = request.stageIndex,
                    targetMbps = request.targetMbps,
                )
            }
        tracker.accept(
            sequenceNumber = request.sequenceNumber,
            sentAtUs = request.sentAtUs,
            stageComplete = request.stageComplete,
            expectedPacketsFromSender = request.expectedPackets,
        )
        if (!request.stageComplete) return
        val expectedPackets = maxOf(tracker.expectedPackets, request.expectedPackets, tracker.receivedPackets)
        val missingPackets = (expectedPackets - tracker.receivedPackets).coerceAtLeast(0)
        val jitterUs =
            if (tracker.jitterDeltasUs.isEmpty()) {
                0
            } else {
                (tracker.jitterDeltasUs.average()).toInt().coerceAtLeast(0)
            }
        val response =
            request.copy(
                echo = true,
                expectedPackets = expectedPackets,
                receivedPackets = tracker.receivedPackets,
                missingPackets = missingPackets,
                jitterUs = jitterUs,
                receiverTransport = transportProvider().ordinal,
            )
        connectionTests.remove(key)
        val responseBytes = PacketCodec.encodeConnectionTest(response)
        socket?.send(DatagramPacket(responseBytes, responseBytes.size, address, port))
    }
}

@Composable
fun TvPlaybackSurface(controller: ReceiverSessionController, modifier: Modifier = Modifier, keepScreenAwake: Boolean = true) {
    val holderCallback =
        remember(controller) {
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    controller.attachSurface(holder.surface)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    controller.updateSurfaceSize(width, height)
                    controller.attachSurface(holder.surface)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    controller.detachSurface()
                }
            }
        }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(holderCallback)
                keepScreenOn = keepScreenAwake
            }
        },
        update = { view -> view.keepScreenOn = keepScreenAwake },
    )
    DisposableEffect(holderCallback) {
        onDispose { controller.detachSurface() }
    }
}
