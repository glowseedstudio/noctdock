package com.glowseed.noctdock.sender

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glowseed.noctdock.core.AudioConfig
import com.glowseed.noctdock.core.CodecCompatibilityMessaging
import com.glowseed.noctdock.core.FragmentRepairRequestPacket
import com.glowseed.noctdock.core.HevcFallbackPolicy
import com.glowseed.noctdock.core.LatencyPriority
import com.glowseed.noctdock.core.NoctConstants
import com.glowseed.noctdock.core.NoctLog
import com.glowseed.noctdock.core.PacketCodec
import com.glowseed.noctdock.core.PacketPacer
import com.glowseed.noctdock.core.PacketPacingPolicy
import com.glowseed.noctdock.core.PacketType
import com.glowseed.noctdock.core.PcmAudioPacket
import com.glowseed.noctdock.core.ProfileAdaptiveBitrateController
import com.glowseed.noctdock.core.ReceiverFeedbackDeltaTracker
import com.glowseed.noctdock.core.ReceiverFeedbackPacket
import com.glowseed.noctdock.core.ScreenCloakMode
import com.glowseed.noctdock.core.ScreenCloakStatus
import com.glowseed.noctdock.core.SoundMode
import com.glowseed.noctdock.core.StreamConfigPacket
import com.glowseed.noctdock.core.StreamHealth
import com.glowseed.noctdock.core.StreamMetrics
import com.glowseed.noctdock.core.StreamQualityConfig
import com.glowseed.noctdock.core.StreamSessionState
import com.glowseed.noctdock.core.VideoCodec
import com.glowseed.noctdock.core.VideoFramePacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

data class EncodedFrame(val frameId: Long, val presentationTimeUs: Long, val keyFrame: Boolean, val bytes: ByteArray)

data class SenderStreamState(
    val state: StreamSessionState = StreamSessionState.Idle,
    val receiverName: String = "",
    val encoderName: String = "Not selected",
    val metrics: StreamMetrics = StreamMetrics(),
    val error: String? = null,
    val screenCloakStatus: ScreenCloakStatus = ScreenCloakStatus(),
)

private const val STREAM_LOG_TAG = "Stream"

object StreamSessionController {
    private val _state = MutableStateFlow(SenderStreamState())
    val state: StateFlow<SenderStreamState> = _state.asStateFlow()

    fun update(next: SenderStreamState) {
        _state.value = next
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP),
        )
    }
}

class MediaProjectionController(private val context: Context, private val resultCode: Int, private val resultData: Intent, private val onProjectionStopped: () -> Unit = {}) {
    private val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private val callback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                display?.release()
                display = null
                onProjectionStopped()
            }
        }

    fun start(): MediaProjection {
        val mediaProjection =
            manager.getMediaProjection(resultCode, resultData)
                ?: error("MediaProjection permission result was invalid")
        mediaProjection.registerCallback(callback, Handler(Looper.getMainLooper()))
        projection = mediaProjection
        return mediaProjection
    }

    fun renderTo(surface: Surface, width: Int, height: Int, densityDpi: Int) {
        val mediaProjection = projection ?: error("MediaProjection was not started")
        display?.release()
        display =
            mediaProjection.createVirtualDisplay(
                "NoctDockCapture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null,
            )
    }

    fun release() {
        display?.release()
        display = null
        runCatching { projection?.unregisterCallback(callback) }
        projection?.stop()
        projection = null
    }
}

class H264ScreenEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val bitrate: Int,
    private val codecMime: String,
    private val keyframeIntervalSeconds: Int,
    private val lowLatency: Boolean,
    private val latencyPriority: LatencyPriority,
    private val onConfig: (StreamConfigPacket) -> Unit,
    private val onFrame: (EncodedFrame) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var callbackThread: HandlerThread? = null
    private val frameCounter = AtomicLong(1L)
    var encoderName: String = "Not selected"
        private set

    fun start(streamId: Int): Surface {
        val codecInfo =
            chooseEncoder(codecMime) ?: error("No ${VideoCodec.fromMime(codecMime).friendlyName} encoder available")
        encoderName = codecInfo.name
        val mediaCodec = MediaCodec.createByCodecName(codecInfo.name)
        codec = mediaCodec
        val format =
            MediaFormat.createVideoFormat(codecMime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_BITRATE_MODE, codecInfo.preferredBitrateMode(codecMime, latencyPriority))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeIntervalSeconds)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                setFloat(MediaFormat.KEY_OPERATING_RATE, fps * 1.25f)
                setInteger(MediaFormat.KEY_LATENCY, 1)
                setInteger(MediaFormat.KEY_MAX_FPS_TO_ENCODER, fps)
                codecInfo.configureProfileLevel(this, codecMime, width, height, fps)
                if (lowLatency && Build.VERSION.SDK_INT >= 30) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                if (lowLatency) setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
            }
        val thread = HandlerThread("NoctDockEncoder", Process.THREAD_PRIORITY_VIDEO)
        thread.start()
        callbackThread = thread
        mediaCodec.setCallback(
            object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    runCatching {
                        if (info.size <= 0) {
                            codec.releaseOutputBuffer(index, false)
                            return
                        }
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer == null) {
                            codec.releaseOutputBuffer(index, false)
                            return
                        }
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        buffer.get(bytes)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        val isKeyFrame = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        if (!isConfig) {
                            onFrame(
                                EncodedFrame(
                                    frameId = frameCounter.getAndIncrement(),
                                    presentationTimeUs = info.presentationTimeUs,
                                    keyFrame = isKeyFrame,
                                    bytes = bytes,
                                ),
                            )
                        }
                        codec.releaseOutputBuffer(index, false)
                    }.onFailure(onError)
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    onError(e)
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    val csd0 = format.getByteBuffer("csd-0")?.toByteArray() ?: ByteArray(0)
                    val csd1 = format.getByteBuffer("csd-1")?.toByteArray() ?: ByteArray(0)
                    onConfig(
                        StreamConfigPacket(
                            streamId,
                            width,
                            height,
                            fps,
                            bitrate,
                            csd0,
                            csd1,
                            mime = codecMime,
                            latencyPriority = latencyPriority,
                        ),
                    )
                }
            },
            Handler(thread.looper),
        )
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = mediaCodec.createInputSurface()
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                inputSurface?.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
        }
        mediaCodec.start()
        return requireNotNull(inputSurface)
    }

    fun requestKeyFrame() {
        val params = Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }
        runCatching { codec?.setParameters(params) }
    }

    fun updateBitrate(bitrate: Int) {
        val params = Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate) }
        runCatching { codec?.setParameters(params) }
    }

    fun release() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        inputSurface?.release()
        inputSurface = null
        callbackThread?.quitSafely()
        callbackThread = null
    }

    private fun chooseEncoder(mime: String): MediaCodecInfo? {
        val codecs =
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filter { it.isEncoder && it.supportedTypes.any { type -> type.equals(mime, true) } }
        return codecs.firstOrNull { it.isHardwareAccelerated }
            ?: codecs.firstOrNull { !it.name.contains("google", ignoreCase = true) }
            ?: codecs.firstOrNull()
    }

    private fun MediaCodecInfo.preferredBitrateMode(mime: String, priority: LatencyPriority): Int {
        val capabilities = runCatching { getCapabilitiesForType(mime).encoderCapabilities }.getOrNull()
        val cbrFrameDrop =
            if (Build.VERSION.SDK_INT >=
                31
            ) {
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD
            } else {
                null
            }
        return when {
            capabilities?.isBitrateModeSupported(
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
            ) == true -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR

            priority == LatencyPriority.Lowest && cbrFrameDrop != null &&
                capabilities?.isBitrateModeSupported(cbrFrameDrop) == true -> cbrFrameDrop

            else -> MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
        }
    }

    private fun MediaCodecInfo.configureProfileLevel(format: MediaFormat, mime: String, width: Int, height: Int, fps: Int) {
        val levels = runCatching { getCapabilitiesForType(mime).profileLevels.toList() }.getOrDefault(emptyList())
        if (mime == MediaFormat.MIMETYPE_VIDEO_AVC) {
            val high = levels.firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
            if (high != null) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                val level =
                    when {
                        width >= 1920 && height >= 1080 && fps >= 60 -> MediaCodecInfo.CodecProfileLevel.AVCLevel42
                        else -> MediaCodecInfo.CodecProfileLevel.AVCLevel31
                    }
                if (levels.any { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh && it.level >= level }) {
                    format.setInteger(MediaFormat.KEY_LEVEL, level)
                }
            }
        } else if (mime == MediaFormat.MIMETYPE_VIDEO_HEVC) {
            val main = levels.firstOrNull { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain }
            if (main !=
                null
            ) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
            }
        }
    }

    private fun java.nio.ByteBuffer.toByteArray(): ByteArray {
        val duplicate = duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }
}

class UdpVideoSender(
    private val host: String,
    private val port: Int,
    private val streamId: Int,
    private val maxQueueSize: Int,
    private val dropOldestFrames: Boolean,
    private val packetPacingEnabled: Boolean,
    private val onKeyFrameNeeded: () -> Unit,
    private val onMetrics: (StreamMetrics) -> Unit,
) {
    private companion object {
        const val KEYFRAME_REQUEST_COOLDOWN_NS = 1_200_000_000L
        const val FEEDBACK_DROP_KEYFRAME_THRESHOLD = 4
        const val FEEDBACK_QUEUE_KEYFRAME_THRESHOLD = 4
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val frameChannel =
        Channel<EncodedFrame>(
            capacity = maxQueueSize.coerceIn(1, 8),
        )
    private val configChannel = Channel<StreamConfigPacket>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null

    @Volatile
    private var latestConfig: StreamConfigPacket? = null
    private var configSent = false
    private var bytesSentThisSecond = 0L
    private var framesSentThisSecond = 0
    private var droppedFrames = 0
    private var queuedFrames = 0
    private var pacingDelayNsThisSecond = 0L
    private var pacedPacketsThisSecond = 0
    private var fragmentsSentThisSecond = 0
    private var maxFragmentsPerFrameThisSecond = 0
    private var repairRequestsThisSecond = 0
    private var repairFragmentsResentThisSecond = 0
    private var latestFeedback = ReceiverFeedbackPacket(streamId, 0, 0, 0, 0)
    private val feedbackDeltaTracker = ReceiverFeedbackDeltaTracker()
    private val packetPacer = PacketPacer()
    private val recentFragments = linkedMapOf<Long, List<ByteArray>>()

    @Volatile
    private var waitingForFreshKeyFrame = false
    private var lastKeyFrameRequestAtNs = 0L

    fun start() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_VIDEO) }
            DatagramSocket().use { udpSocket ->
                runCatching { udpSocket.sendBufferSize = 1_048_576 }
                runCatching { udpSocket.trafficClass = 0x10 }
                socket = udpSocket
                val address = InetAddress.getByName(host)
                launch {
                    while (running.get()) {
                        delay(500)
                        sendBytes(udpSocket, address, PacketCodec.encodeHeartbeat(streamId))
                    }
                }
                launch {
                    val buffer = ByteArray(PacketCodec.MAX_DATAGRAM_SIZE)
                    while (running.get()) {
                        runCatching {
                            val packet = DatagramPacket(buffer, buffer.size)
                            udpSocket.receive(packet)
                            if (!PacketCodec.isNoctDockPacket(packet.data, packet.length)) return@runCatching
                            val fragment = PacketCodec.decode(packet.data, packet.length)
                            if (fragment.header.type == PacketType.RECEIVER_FEEDBACK &&
                                fragment.header.streamId == streamId
                            ) {
                                val feedback = PacketCodec.decodeReceiverFeedback(fragment)
                                val reassemblyDropDelta =
                                    (feedback.reassemblyDrops - latestFeedback.reassemblyDrops)
                                        .coerceAtLeast(
                                            0,
                                        )
                                if (reassemblyDropDelta >= FEEDBACK_DROP_KEYFRAME_THRESHOLD ||
                                    feedback.queueDepth >= FEEDBACK_QUEUE_KEYFRAME_THRESHOLD
                                ) {
                                    requestFreshKeyFrame()
                                }
                                latestFeedback = feedback
                            } else if (fragment.header.type == PacketType.FRAGMENT_REPAIR_REQUEST &&
                                fragment.header.streamId == streamId
                            ) {
                                val request = PacketCodec.decodeFragmentRepairRequest(fragment)
                                repairRequestsThisSecond += 1
                                repairFragmentsResentThisSecond += resendRequestedFragments(udpSocket, address, request)
                            }
                        }
                    }
                }
                launch {
                    var lastMetricsAtNs = System.nanoTime()
                    while (running.get()) {
                        delay(1000)
                        val nowNs = System.nanoTime()
                        val elapsedSeconds = ((nowNs - lastMetricsAtNs).coerceAtLeast(1L)).toDouble() / 1_000_000_000.0
                        lastMetricsAtNs = nowNs
                        val feedback = latestFeedback
                        val feedbackDelta = feedbackDeltaTracker.update(feedback)
                        val sentFps = (framesSentThisSecond / elapsedSeconds).toInt().coerceAtLeast(0)
                        val bitrateMbps =
                            (((bytesSentThisSecond * 8).toDouble() / elapsedSeconds) / 1_000_000.0)
                                .toInt()
                                .coerceAtLeast(
                                    0,
                                )
                        val packetLossEstimate =
                            if (framesSentThisSecond <= 0) {
                                0
                            } else {
                                (
                                    (feedbackDelta.reassemblyDrops * 100) /
                                        (framesSentThisSecond + feedbackDelta.reassemblyDrops).coerceAtLeast(1)
                                    ).coerceIn(0, 100)
                            }
                        val metrics =
                            StreamMetrics(
                                fps = sentFps,
                                bitrateMbps = bitrateMbps,
                                droppedFrames = droppedFrames,
                                queueDepth = queuedFrames.coerceIn(0, maxQueueSize.coerceIn(1, 8)),
                                receivedFps = feedback.receivedFps,
                                reassemblyDrops = feedback.reassemblyDrops,
                                decoderErrors = feedbackDelta.decoderErrors,
                                packetLossPercent = packetLossEstimate,
                                audioPacketsReceived = feedback.audioPacketsReceived,
                                audioUnderruns = feedbackDelta.audioUnderruns,
                                audioDrops = feedbackDelta.audioDrops,
                                audioBufferMs = feedback.audioBufferMs,
                                avOffsetMs = feedback.avOffsetMs,
                                pacingDelayMs = (pacingDelayNsThisSecond / 1_000_000L).toInt(),
                                pacedPackets = pacedPacketsThisSecond,
                                requestedWidth = latestConfig?.width ?: 0,
                                requestedHeight = latestConfig?.height ?: 0,
                                actualEncoderWidth = latestConfig?.width ?: 0,
                                actualEncoderHeight = latestConfig?.height ?: 0,
                                virtualDisplayWidth = latestConfig?.width ?: 0,
                                virtualDisplayHeight = latestConfig?.height ?: 0,
                                codecMime = latestConfig?.mime.orEmpty(),
                                configuredBitrateMbps = ((latestConfig?.bitrate ?: 0) / 1_000_000).coerceAtLeast(0),
                            )
                        NoctLog.debug(
                            STREAM_LOG_TAG,
                            "sender fps=${metrics.fps} bitrate=${metrics.bitrateMbps}Mbps queue=${metrics.queueDepth} senderDrops=${metrics.droppedFrames} receiverFps=${metrics.receivedFps} reassemblyDrops=${metrics.reassemblyDrops} packetLoss=${metrics.packetLossPercent}% fragments=$fragmentsSentThisSecond maxFrameFragments=$maxFragmentsPerFrameThisSecond repairs=$repairRequestsThisSecond resent=$repairFragmentsResentThisSecond pacing=${metrics.pacingDelayMs}ms/${metrics.pacedPackets}",
                        )
                        onMetrics(metrics)
                        bytesSentThisSecond = 0L
                        framesSentThisSecond = 0
                        pacingDelayNsThisSecond = 0L
                        pacedPacketsThisSecond = 0
                        fragmentsSentThisSecond = 0
                        maxFragmentsPerFrameThisSecond = 0
                        repairRequestsThisSecond = 0
                        repairFragmentsResentThisSecond = 0
                    }
                }
                while (running.get()) {
                    configChannel.tryReceive().getOrNull()?.let { config ->
                        sendBytes(udpSocket, address, PacketCodec.encodeConfig(config))
                    }
                    val frame = frameChannel.receiveCatching().getOrNull() ?: break
                    queuedFrames = (queuedFrames - 1).coerceAtLeast(0)
                    sendFrame(udpSocket, address, frame)
                }
            }
        }
    }

    fun sendConfig(config: StreamConfigPacket) {
        latestConfig = config
        configSent = false
        configChannel.trySend(config)
    }

    fun trySend(frame: EncodedFrame) {
        val queueLimit = maxQueueSize.coerceIn(1, 8)
        if (queuedFrames >= queueLimit) {
            if (dropOldestFrames) {
                val discarded = drainQueuedFrames()
                if (discarded > 0) {
                    droppedFrames += discarded
                    requestFreshKeyFrame()
                }
            } else {
                droppedFrames++
                return
            }
        }
        if (waitingForFreshKeyFrame && !frame.keyFrame) {
            droppedFrames++
            return
        }
        if (frame.keyFrame) {
            waitingForFreshKeyFrame = false
        }
        if (frameChannel.trySend(frame).isSuccess) {
            queuedFrames = (queuedFrames + 1).coerceAtMost(queueLimit)
            return
        }
        if (dropOldestFrames) {
            frameChannel.tryReceive()
            if (frameChannel.trySend(frame).isSuccess) {
                queuedFrames = (queuedFrames + 1).coerceAtMost(queueLimit)
                droppedFrames++
                return
            }
        }
        droppedFrames++
    }

    private fun drainQueuedFrames(): Int {
        var drained = 0
        while (frameChannel.tryReceive().isSuccess) {
            drained++
        }
        queuedFrames = 0
        return drained
    }

    private fun requestFreshKeyFrame() {
        val nowNs = System.nanoTime()
        if (nowNs - lastKeyFrameRequestAtNs < KEYFRAME_REQUEST_COOLDOWN_NS) return
        lastKeyFrameRequestAtNs = nowNs
        onKeyFrameNeeded()
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            runCatching { frameChannel.close() }
            runCatching { configChannel.close() }
            runCatching { socket?.close() }
            runCatching { job.cancel() }
            return
        }
        runCatching {
            socket?.let { udpSocket ->
                val bytes = PacketCodec.encodeStop(streamId)
                udpSocket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), port))
            }
        }
        runCatching { frameChannel.close() }
        runCatching { configChannel.close() }
        runCatching { socket?.close() }
        runCatching { job.cancel() }
    }

    private suspend fun sendFrame(udpSocket: DatagramSocket, address: InetAddress, frame: EncodedFrame) {
        val config = latestConfig
        if (config != null && (!configSent || frame.keyFrame)) {
            sendBytes(udpSocket, address, PacketCodec.encodeConfig(config))
            configSent = true
        }
        val packet = VideoFramePacket(streamId, frame.frameId, frame.presentationTimeUs, frame.keyFrame, frame.bytes)
        val fragments = mutableListOf<ByteArray>()
        val configBitrateMbps = ((config?.bitrate ?: 0) / 1_000_000).coerceAtLeast(0)
        val latencyPriority = config?.latencyPriority ?: LatencyPriority.Balanced
        val senderIsCaughtUp = queuedFrames == 0 && latestFeedback.queueDepth == 0
        val fullHdOrHigher = (config?.width ?: 0) >= 1920
        var framePacingBudgetNs =
            if (packetPacingEnabled && senderIsCaughtUp && latencyPriority == LatencyPriority.Quality &&
                fullHdOrHigher
            ) {
                PacketPacingPolicy.frameBudgetNs(latencyPriority, frame.keyFrame)
            } else {
                0L
            }
        packetPacer.reset()
        PacketCodec.forEachFrameFragment(packet) { bytes ->
            fragments += bytes
            fragmentsSentThisSecond += 1
            if (packetPacingEnabled && configBitrateMbps > 0 && framePacingBudgetNs > 0L) {
                val delayNs = packetPacer.delayForPacketNs(bytes.size, configBitrateMbps, framePacingBudgetNs)
                if (delayNs > 0L && running.get()) {
                    LockSupport.parkNanos(delayNs)
                    framePacingBudgetNs = (framePacingBudgetNs - delayNs).coerceAtLeast(0L)
                    pacingDelayNsThisSecond += delayNs
                    pacedPacketsThisSecond += 1
                }
            }
            sendBytes(udpSocket, address, bytes)
        }
        maxFragmentsPerFrameThisSecond = maxOf(maxFragmentsPerFrameThisSecond, fragments.size)
        rememberFragments(frame.frameId, fragments)
        framesSentThisSecond++
    }

    private fun rememberFragments(frameId: Long, fragments: List<ByteArray>) {
        recentFragments[frameId] = fragments
        while (recentFragments.size > 24) {
            val oldest = recentFragments.keys.first()
            recentFragments.remove(oldest)
        }
    }

    private fun resendRequestedFragments(udpSocket: DatagramSocket, address: InetAddress, request: FragmentRepairRequestPacket): Int {
        val fragments = recentFragments[request.frameId] ?: return 0
        var resent = 0
        request.fragmentIndexes.forEach { index ->
            val bytes = fragments.getOrNull(index) ?: return@forEach
            runCatching {
                sendBytes(udpSocket, address, bytes)
                resent += 1
            }
        }
        return resent
    }

    private fun sendBytes(udpSocket: DatagramSocket, address: InetAddress, bytes: ByteArray) {
        udpSocket.send(DatagramPacket(bytes, bytes.size, address, port))
        bytesSentThisSecond += bytes.size
    }
}

class InternalAudioSender(
    private val context: Context,
    private val projection: MediaProjection,
    private val host: String,
    private val port: Int,
    private val streamId: Int,
    private val onMetrics: (StreamMetrics) -> Unit,
    private val onUnavailable: (String) -> Unit,
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val audioQueue = Channel<PcmAudioPacket>(capacity = 24, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var audioRecord: AudioRecord? = null
    private var socket: DatagramSocket? = null
    private val config = AudioConfig(sampleRateHz = 48_000, channelCount = 2, codec = "pcm16")
    private var packetsSent = 0
    private var packetsCaptured = 0
    private var droppedPackets = 0

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onUnavailable("This game keeps its sound private. Use Retroid Sound for this game.")
            return
        }
        if (!running.compareAndSet(false, true)) return
        startSender()
        startCapture()
        startMetrics()
    }

    private fun startCapture() {
        scope.launch {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                onUnavailable("This game keeps its sound private. Use Retroid Sound for this game.")
                running.set(false)
                audioQueue.close()
                return@launch
            }
            var record: AudioRecord? = null
            runCatching {
                val audioFormat =
                    AudioFormat
                        .Builder()
                        .setSampleRate(config.sampleRateHz)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                val captureConfig =
                    AudioPlaybackCaptureConfiguration
                        .Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()
                val minBuffer =
                    AudioRecord.getMinBufferSize(
                        config.sampleRateHz,
                        AudioFormat.CHANNEL_IN_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                record =
                    AudioRecord
                        .Builder()
                        .setAudioFormat(audioFormat)
                        .setAudioPlaybackCaptureConfig(captureConfig)
                        .setBufferSizeInBytes(maxOf(minBuffer, 4096))
                        .build()
                val activeRecord = requireNotNull(record)
                if (activeRecord.state != AudioRecord.STATE_INITIALIZED) {
                    onUnavailable("This game keeps its sound private. Use Retroid Sound for this game.")
                    running.set(false)
                    audioQueue.close()
                    return@runCatching
                }
                audioRecord = activeRecord
                var sequence = 0L
                val chunk = ByteArray(960)
                var sawAudibleAudio = false
                activeRecord.startRecording()
                while (running.get()) {
                    val read = activeRecord.read(chunk, 0, chunk.size)
                    if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_DEAD_OBJECT) {
                        onUnavailable("This game keeps its sound private. Use Retroid Sound for this game.")
                        running.set(false)
                        audioQueue.close()
                        break
                    }
                    if (read <= 0) continue
                    val payload = chunk.copyOf(read)
                    val silent = payload.all { it == 0.toByte() }
                    if (silent && !sawAudibleAudio) {
                        continue
                    }
                    if (!silent) {
                        sawAudibleAudio = true
                    }
                    val packet =
                        PcmAudioPacket(
                            streamId = streamId,
                            sequenceNumber = sequence++,
                            presentationTimeUs = System.nanoTime() / 1000L,
                            data = payload,
                        )
                    packetsCaptured += 1
                    if (!audioQueue.trySend(packet).isSuccess) droppedPackets += 1
                }
            }.onFailure {
                onUnavailable("This game keeps its sound private. Use Retroid Sound for this game.")
                stop()
            }.also {
                val activeRecord = record
                runCatching { activeRecord?.stop() }
                runCatching { activeRecord?.release() }
                if (audioRecord === activeRecord) audioRecord = null
            }
        }
    }

    private fun startSender() {
        scope.launch {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
            runCatching {
                DatagramSocket().use { udpSocket ->
                    runCatching { udpSocket.sendBufferSize = 256 * 1024 }
                    runCatching { udpSocket.trafficClass = 0x10 }
                    socket = udpSocket
                    val address = InetAddress.getByName(host)
                    var lastConfigAt = 0L
                    while (running.get()) {
                        val now = System.currentTimeMillis()
                        if (now - lastConfigAt > 1_000L) {
                            sendBytes(udpSocket, address, PacketCodec.encodeAudioConfig(streamId, config))
                            lastConfigAt = now
                        }
                        val packet = audioQueue.receiveCatching().getOrNull() ?: break
                        if (packet.sequenceNumber < 0) break
                        sendBytes(udpSocket, address, PacketCodec.encodeAudioPcm(packet))
                        packetsSent += 1
                    }
                }
            }.onFailure {
                stop()
            }
        }
    }

    private fun startMetrics() {
        scope.launch {
            while (running.get()) {
                delay(1000)
                onMetrics(StreamMetrics(audioPacketsSent = packetsSent, audioDrops = droppedPackets))
            }
        }
    }

    fun stop() {
        running.set(false)
        audioQueue.trySend(PcmAudioPacket(streamId, -1, 0, ByteArray(1)))
        audioQueue.close()
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        socket?.close()
        socket = null
        job.cancel()
    }

    private fun sendBytes(udpSocket: DatagramSocket, address: InetAddress, bytes: ByteArray) {
        udpSocket.send(DatagramPacket(bytes, bytes.size, address, port))
    }
}

class HandheldVolumeSession(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousVolume: Int? = null

    fun lowerForDock() {
        val manager = audioManager ?: return
        if (previousVolume != null) return
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        previousVolume = current
        val target = if (current <= 1) 0 else 1
        runCatching { manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    fun restore(): Boolean {
        val manager = audioManager ?: return true
        val previous = previousVolume ?: return true
        previousVolume = null
        return runCatching {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, previous, 0)
        }.isSuccess
    }
}

object SenderForegroundNotification {
    private const val CHANNEL_ID = "noctdock_console_mode"
    private const val NOTIFICATION_ID = 42

    fun id(): Int = NOTIFICATION_ID

    fun build(context: Context, receiverName: String): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "Console Mode", NotificationManager.IMPORTANCE_LOW)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val stopIntent =
            PendingIntent.getService(
                context,
                1,
                Intent(context, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val contentIntent =
            PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("NoctDock Console Mode")
            .setContentText("Playing on $receiverName")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }
}

class ScreenCaptureService : Service() {
    private val streamId = (System.currentTimeMillis() and Int.MAX_VALUE.toLong()).toInt()
    private var projectionController: MediaProjectionController? = null
    private var encoder: H264ScreenEncoder? = null
    private var sender: UdpVideoSender? = null
    private var audioSender: InternalAudioSender? = null
    private var volumeSession: HandheldVolumeSession? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var foregroundStarted = false
    private val pipelineStopping = AtomicBoolean(false)
    private val screenCloakController by lazy { ScreenCloakController(this) }
    private var screenCloakConfig = ScreenCloakConfig(ScreenCloakMode.OFF, overlayDisabledDueToTvPictureIssue = false)
    private var activeCodecMime: String = VideoCodec.AVC.mime
    private var currentBitrateMbps: Int = 12
    private var adaptiveController: ProfileAdaptiveBitrateController? = null
    private var fallbackAvcBitrate: Int = 0
    private var fallbackAvcFloorMbps: Int = 0
    private var fallbackAvcCeilingMbps: Int = 0
    private var pipelineWidth: Int = 1280
    private var pipelineHeight: Int = 720
    private var pipelineFps: Int = 60
    private var pipelineDensityDpi: Int = 320
    private var pipelineKeyframeInterval: Int = 1
    private var pipelineLowLatency: Boolean = true
    private var pipelineLatencyPriority: LatencyPriority = LatencyPriority.Balanced
    private var pipelineReceiverName: String = ""
    private var pipelineProfileTitle: String = ""
    private var lastReportedDecoderErrors: Int = 0
    private var decoderErrorStreak: Int = 0
    private var hevcCompatibilityFallbackUsed: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopPipeline()
            ACTION_START -> startPipeline(intent)
            ACTION_UPDATE_SCREEN_CLOAK -> updateScreenCloak(intent)
            ACTION_TEST_SCREEN_CLOAK -> testScreenCloak(intent)
        }
        return START_NOT_STICKY
    }

    private fun startPipeline(intent: Intent) {
        if (pipelineStopping.get()) return
        if (sender != null || encoder != null || projectionController != null) {
            NoctLog.warn(STREAM_LOG_TAG, "Ignoring duplicate Console Mode start while a pipeline is already active")
            return
        }
        val receiverName = intent.getStringExtra(EXTRA_RECEIVER_NAME).orEmpty()
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val port = intent.getIntExtra(EXTRA_PORT, NoctConstants.DEFAULT_DISCOVERY_PORT)
        val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 720)
        val fps = intent.getIntExtra(EXTRA_FPS, 60)
        val bitrate = intent.getIntExtra(EXTRA_BITRATE, 8_000_000)
        val codecMime = intent.getStringExtra(EXTRA_CODEC_MIME) ?: VideoCodec.AVC.mime
        val fallbackAvcBitrate = intent.getIntExtra(EXTRA_FALLBACK_AVC_BITRATE, bitrate)
        val adaptiveFloorMbps = intent.getIntExtra(EXTRA_ADAPTIVE_FLOOR_MBPS, (bitrate / 1_000_000).coerceAtMost(12))
        val adaptiveCeilingMbps =
            intent.getIntExtra(
                EXTRA_ADAPTIVE_CEILING_MBPS,
                (bitrate / 1_000_000).coerceAtLeast(adaptiveFloorMbps),
            )
        val fallbackAvcFloorMbps = intent.getIntExtra(EXTRA_FALLBACK_AVC_FLOOR_MBPS, adaptiveFloorMbps)
        val fallbackAvcCeilingMbps = intent.getIntExtra(EXTRA_FALLBACK_AVC_CEILING_MBPS, adaptiveCeilingMbps)
        val keyframeInterval = intent.getIntExtra(EXTRA_KEYFRAME_INTERVAL, 1)
        val lowLatency = intent.getBooleanExtra(EXTRA_LOW_LATENCY, true)
        val latencyPriority =
            intent
                .getStringExtra(EXTRA_LATENCY_PRIORITY)
                ?.let { runCatching { LatencyPriority.valueOf(it) }.getOrNull() }
                ?: LatencyPriority.Balanced
        val maxQueueSize = intent.getIntExtra(EXTRA_MAX_QUEUE_SIZE, 2)
        val dropOldestFrames = intent.getBooleanExtra(EXTRA_DROP_OLDEST_FRAMES, true)
        val soundMode =
            intent.getStringExtra(EXTRA_SOUND_MODE)?.let { runCatching { SoundMode.valueOf(it) }.getOrNull() }
                ?: SoundMode.RETROID
        val lowerHandheldSound = intent.getBooleanExtra(EXTRA_LOWER_HANDHELD_SOUND, true)
        val screenCloakMode =
            intent
                .getStringExtra(EXTRA_SCREEN_CLOAK_MODE)
                ?.let { runCatching { ScreenCloakMode.valueOf(it) }.getOrNull() }
                ?: ScreenCloakMode.OFF
        val screenCloakOverlayDisabled = intent.getBooleanExtra(EXTRA_SCREEN_CLOAK_OVERLAY_DISABLED, false)
        screenCloakConfig =
            ScreenCloakConfig(
                mode = screenCloakMode,
                overlayDisabledDueToTvPictureIssue = screenCloakOverlayDisabled,
            )
        val qualityConfig =
            StreamQualityConfig(
                adaptiveBitrateEnabled = intent.getBooleanExtra(EXTRA_ADAPTIVE_BITRATE, true),
                batterySaverMode = intent.getBooleanExtra(EXTRA_BATTERY_SAVER, false),
                packetPacingEnabled = intent.getBooleanExtra(EXTRA_PACKET_PACING, true),
                thermalProtectionEnabled = intent.getBooleanExtra(EXTRA_THERMAL_PROTECTION, true),
            )
        val initialBitrate =
            if (qualityConfig.thermalProtectionEnabled && thermalThrottling()) {
                bitrate.coerceAtMost(adaptiveFloorMbps.coerceAtLeast(8) * 1_000_000)
            } else {
                bitrate
            }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData =
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }
        if (host.isBlank() || resultData == null) {
            StreamSessionController.update(
                SenderStreamState(StreamSessionState.Failed, receiverName, error = "Console Mode could not start."),
            )
            stopSelf()
            return
        }

        runCatching {
            startForeground(SenderForegroundNotification.id(), SenderForegroundNotification.build(this, receiverName))
            foregroundStarted = true
        }.onFailure {
            StreamSessionController.update(
                SenderStreamState(StreamSessionState.Failed, receiverName, error = "Console Mode could not start."),
            )
            stopSelf()
            return
        }
        pipelineReceiverName = receiverName
        pipelineProfileTitle = intent.getStringExtra(EXTRA_PROFILE_TITLE).orEmpty()
        pipelineWidth = width
        pipelineHeight = height
        pipelineFps = fps
        pipelineKeyframeInterval = keyframeInterval
        pipelineLowLatency = lowLatency
        pipelineLatencyPriority = latencyPriority
        this.fallbackAvcBitrate = fallbackAvcBitrate
        this.fallbackAvcFloorMbps = fallbackAvcFloorMbps
        this.fallbackAvcCeilingMbps = fallbackAvcCeilingMbps
        activeCodecMime = codecMime
        hevcCompatibilityFallbackUsed = false
        lastReportedDecoderErrors = 0
        decoderErrorStreak = 0
        StreamSessionController.update(SenderStreamState(StreamSessionState.StartingService, receiverName))
        runCatching {
            acquireLowLatencyWifiLock()
            val requestedBitrateMbps = (initialBitrate / 1_000_000).coerceAtLeast(1)
            currentBitrateMbps = requestedBitrateMbps
            adaptiveController =
                ProfileAdaptiveBitrateController(
                    floorMbps = adaptiveFloorMbps,
                    ceilingMbps = adaptiveCeilingMbps,
                )
            var lastDroppedFrames = 0
            val udpSender =
                UdpVideoSender(
                    host = host,
                    port = port,
                    streamId = streamId,
                    maxQueueSize = maxQueueSize,
                    dropOldestFrames = dropOldestFrames,
                    packetPacingEnabled = qualityConfig.packetPacingEnabled,
                    onKeyFrameNeeded = { encoder?.requestKeyFrame() },
                ) { metrics ->
                    val droppedThisSecond = (metrics.droppedFrames - lastDroppedFrames).coerceAtLeast(0)
                    lastDroppedFrames = metrics.droppedFrames
                    val decoderErrorDelta = (metrics.decoderErrors - lastReportedDecoderErrors).coerceAtLeast(0)
                    lastReportedDecoderErrors = metrics.decoderErrors
                    decoderErrorStreak = HevcFallbackPolicy.nextStreak(decoderErrorStreak, decoderErrorDelta)
                    if (
                        HevcFallbackPolicy.shouldFallback(
                            activeCodecMime = activeCodecMime,
                            decoderErrorStreak = decoderErrorStreak,
                            fallbackAlreadyPerformed = hevcCompatibilityFallbackUsed,
                        )
                    ) {
                        runCatching { switchToAvcCompatibilityEncoder(rebindProjection = true) }
                            .onFailure { error ->
                                NoctLog.warn(STREAM_LOG_TAG, "HEVC compatibility fallback failed", error)
                            }
                    }
                    val nextBitrateMbps =
                        adaptiveController?.nextBitrate(
                            currentBitrateMbps = currentBitrateMbps,
                            health =
                            StreamHealth(
                                packetLossPercent = metrics.packetLossPercent,
                                jitterMs = metrics.jitterMs,
                                queueDepth = metrics.queueDepth,
                                droppedFramesPerMinute = droppedThisSecond * 60,
                                receiverDecodeErrors = metrics.decoderErrors,
                                thermalThrottling = qualityConfig.thermalProtectionEnabled && thermalThrottling(),
                                encoderOverloaded =
                                droppedThisSecond > fps / 4 ||
                                    metrics.queueDepth >= maxQueueSize,
                            ),
                            config = qualityConfig,
                        )
                    if (nextBitrateMbps != null && nextBitrateMbps != currentBitrateMbps) {
                        currentBitrateMbps = nextBitrateMbps
                        encoder?.updateBitrate(nextBitrateMbps * 1_000_000)
                    }
                    val current = StreamSessionController.state.value
                    StreamSessionController.update(
                        current.copy(
                            state = StreamSessionState.Active,
                            metrics =
                            metrics.copy(
                                bitrateMbps = currentBitrateMbps,
                                audioPacketsSent = current.metrics.audioPacketsSent,
                                requestedWidth = width,
                                requestedHeight = height,
                                actualEncoderWidth = width,
                                actualEncoderHeight = height,
                                virtualDisplayWidth = width,
                                virtualDisplayHeight = height,
                                codecMime = activeCodecMime,
                                configuredBitrateMbps = requestedBitrateMbps,
                            ),
                        ),
                    )
                }.also { it.start() }
            sender = udpSender
            var screenEncoder =
                H264ScreenEncoder(
                    width = width,
                    height = height,
                    fps = fps,
                    bitrate = initialBitrate,
                    codecMime = activeCodecMime,
                    keyframeIntervalSeconds = keyframeInterval,
                    lowLatency = lowLatency,
                    latencyPriority = latencyPriority,
                    onConfig = udpSender::sendConfig,
                    onFrame = udpSender::trySend,
                    onError = { error ->
                        StreamSessionController.update(
                            StreamSessionController.state.value.copy(
                                state = StreamSessionState.Failed,
                                error = error.message,
                            ),
                        )
                        stopPipeline()
                    },
                )
            encoder = screenEncoder
            val controller =
                MediaProjectionController(this, resultCode, resultData, onProjectionStopped = { stopPipeline() })
            val projection = controller.start()
            projectionController = controller
            pipelineDensityDpi = resources.displayMetrics.densityDpi
            val surface =
                runCatching { screenEncoder.start(streamId) }
                    .getOrElse { hevcError ->
                        if (activeCodecMime != VideoCodec.HEVC.mime) throw hevcError
                        switchToAvcCompatibilityEncoder(rebindProjection = false)
                    }
            controller.renderTo(surface, pipelineWidth, pipelineHeight, pipelineDensityDpi)
            if (soundMode == SoundMode.QUIET || (soundMode == SoundMode.TV && lowerHandheldSound)) {
                volumeSession = HandheldVolumeSession(this).also { it.lowerForDock() }
            }
            if (soundMode == SoundMode.TV || soundMode == SoundMode.BOTH) {
                audioSender =
                    InternalAudioSender(
                        context = this,
                        projection = projection,
                        host = host,
                        port = port,
                        streamId = streamId,
                        onMetrics = { audioMetrics ->
                            val current = StreamSessionController.state.value
                            StreamSessionController.update(
                                current.copy(
                                    metrics =
                                    current.metrics.copy(
                                        audioPacketsSent = audioMetrics.audioPacketsSent,
                                        audioDrops = maxOf(current.metrics.audioDrops, audioMetrics.audioDrops),
                                    ),
                                ),
                            )
                        },
                        onUnavailable = { message ->
                            handleAudioUnavailable(message)
                        },
                    ).also { it.start() }
            }
            val cloakStatus = screenCloakController.apply(screenCloakConfig)
            val activeEncoder = encoder ?: screenEncoder
            activeEncoder.requestKeyFrame()
            StreamSessionController.update(
                SenderStreamState(
                    state = StreamSessionState.Active,
                    receiverName = receiverName,
                    encoderName = activeEncoder.encoderName,
                    metrics =
                    StreamMetrics(
                        requestedWidth = width,
                        requestedHeight = height,
                        actualEncoderWidth = width,
                        actualEncoderHeight = height,
                        virtualDisplayWidth = width,
                        virtualDisplayHeight = height,
                        codecMime = activeCodecMime,
                        configuredBitrateMbps = currentBitrateMbps,
                    ),
                    screenCloakStatus = cloakStatus,
                ),
            )
        }.onFailure { error ->
            StreamSessionController.update(
                SenderStreamState(StreamSessionState.Failed, receiverName, error = "Console Mode could not start."),
            )
            stopPipeline()
        }
    }

    private fun thermalThrottling(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
    }

    private fun switchToAvcCompatibilityEncoder(rebindProjection: Boolean): Surface {
        val udpSender = sender ?: error("Console Mode network sender is not ready")
        runCatching { encoder?.release() }
        activeCodecMime = VideoCodec.AVC.mime
        hevcCompatibilityFallbackUsed = true
        currentBitrateMbps = (fallbackAvcBitrate / 1_000_000).coerceAtLeast(1)
        adaptiveController =
            ProfileAdaptiveBitrateController(
                floorMbps = fallbackAvcFloorMbps,
                ceilingMbps = fallbackAvcCeilingMbps,
            )
        decoderErrorStreak = 0
        lastReportedDecoderErrors = 0
        val fallbackEncoder =
            H264ScreenEncoder(
                width = pipelineWidth,
                height = pipelineHeight,
                fps = pipelineFps,
                bitrate = fallbackAvcBitrate,
                codecMime = activeCodecMime,
                keyframeIntervalSeconds = pipelineKeyframeInterval,
                lowLatency = pipelineLowLatency,
                latencyPriority = pipelineLatencyPriority,
                onConfig = udpSender::sendConfig,
                onFrame = udpSender::trySend,
                onError = { error ->
                    StreamSessionController.update(
                        StreamSessionController.state.value.copy(
                            state = StreamSessionState.Failed,
                            error = error.message,
                        ),
                    )
                    stopPipeline()
                },
            )
        encoder = fallbackEncoder
        val surface = fallbackEncoder.start(streamId)
        if (rebindProjection) {
            projectionController?.renderTo(surface, pipelineWidth, pipelineHeight, pipelineDensityDpi)
        }
        fallbackEncoder.requestKeyFrame()
        val compatibilityMessage =
            CodecCompatibilityMessaging.hevcToAvcFallbackMessage(
                profileTitle = pipelineProfileTitle,
                height = pipelineHeight,
            )
        StreamSessionController.update(
            StreamSessionController.state.value.copy(
                state = StreamSessionState.Active,
                receiverName = pipelineReceiverName,
                encoderName = fallbackEncoder.encoderName,
                error = compatibilityMessage,
                metrics =
                StreamSessionController.state.value.metrics.copy(
                    codecMime = activeCodecMime,
                    configuredBitrateMbps = currentBitrateMbps,
                    actualEncoderWidth = pipelineWidth,
                    actualEncoderHeight = pipelineHeight,
                ),
            ),
        )
        return surface
    }

    private fun stopPipeline() {
        if (!pipelineStopping.compareAndSet(false, true)) return
        val cloakRestore = screenCloakController.restore()
        runCatching { audioSender?.stop() }
        audioSender = null
        val volumeRestored = runCatching { volumeSession?.restore() ?: true }.getOrDefault(false)
        volumeSession = null
        runCatching { sender?.stop() }
        sender = null
        runCatching { projectionController?.release() }
        projectionController = null
        runCatching { encoder?.release() }
        encoder = null
        releaseLowLatencyWifiLock()
        StreamSessionController.update(
            SenderStreamState(
                state = StreamSessionState.Stopped,
                error = if (volumeRestored) null else "Handheld sound could not be restored automatically.",
                screenCloakStatus = cloakRestore,
            ),
        )
        if (foregroundStarted) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            foregroundStarted = false
        }
        pipelineStopping.set(false)
        stopSelf()
    }

    private fun updateScreenCloak(intent: Intent) {
        screenCloakConfig =
            ScreenCloakConfig(
                mode =
                intent
                    .getStringExtra(EXTRA_SCREEN_CLOAK_MODE)
                    ?.let { runCatching { ScreenCloakMode.valueOf(it) }.getOrNull() }
                    ?: screenCloakConfig.mode,
                overlayDisabledDueToTvPictureIssue =
                intent.getBooleanExtra(
                    EXTRA_SCREEN_CLOAK_OVERLAY_DISABLED,
                    screenCloakConfig.overlayDisabledDueToTvPictureIssue,
                ),
            )
        val nextStatus =
            if (StreamSessionController.state.value.state == StreamSessionState.Active) {
                screenCloakController.apply(screenCloakConfig)
            } else {
                screenCloakController.restore()
            }
        StreamSessionController.update(StreamSessionController.state.value.copy(screenCloakStatus = nextStatus))
    }

    private fun testScreenCloak(intent: Intent) {
        updateScreenCloak(intent)
    }

    private fun handleAudioUnavailable(message: String) {
        val volumeRestored = runCatching { volumeSession?.restore() ?: true }.getOrDefault(false)
        volumeSession = null
        runCatching { audioSender?.stop() }
        audioSender = null
        val warning =
            if (volumeRestored) {
                message
            } else {
                "$message Handheld sound could not be restored automatically."
            }
        StreamSessionController.update(StreamSessionController.state.value.copy(error = warning))
    }

    private fun acquireLowLatencyWifiLock() {
        if (wifiLock?.isHeld == true) return
        val wifiManager = applicationContext.getSystemService(WifiManager::class.java) ?: return
        wifiLock =
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "NoctDockSenderLowLatency").apply {
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

    override fun onDestroy() {
        stopPipeline()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.glowseed.noctdock.sender.START_CAPTURE"
        const val ACTION_STOP = "com.glowseed.noctdock.sender.STOP_CAPTURE"
        const val ACTION_UPDATE_SCREEN_CLOAK = "com.glowseed.noctdock.sender.UPDATE_SCREEN_CLOAK"
        const val ACTION_TEST_SCREEN_CLOAK = "com.glowseed.noctdock.sender.TEST_SCREEN_CLOAK"
        const val EXTRA_RECEIVER_NAME = "receiver_name"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"
        const val EXTRA_BITRATE = "bitrate"
        const val EXTRA_CODEC_MIME = "codec_mime"
        const val EXTRA_FALLBACK_AVC_BITRATE = "fallback_avc_bitrate"
        const val EXTRA_ADAPTIVE_FLOOR_MBPS = "adaptive_floor_mbps"
        const val EXTRA_ADAPTIVE_CEILING_MBPS = "adaptive_ceiling_mbps"
        const val EXTRA_FALLBACK_AVC_FLOOR_MBPS = "fallback_avc_floor_mbps"
        const val EXTRA_FALLBACK_AVC_CEILING_MBPS = "fallback_avc_ceiling_mbps"
        const val EXTRA_KEYFRAME_INTERVAL = "keyframe_interval"
        const val EXTRA_LOW_LATENCY = "low_latency"
        const val EXTRA_LATENCY_PRIORITY = "latency_priority"
        const val EXTRA_MAX_QUEUE_SIZE = "max_queue_size"
        const val EXTRA_DROP_OLDEST_FRAMES = "drop_oldest_frames"
        const val EXTRA_ADAPTIVE_BITRATE = "adaptive_bitrate"
        const val EXTRA_BATTERY_SAVER = "battery_saver"
        const val EXTRA_PACKET_PACING = "packet_pacing"
        const val EXTRA_THERMAL_PROTECTION = "thermal_protection"
        const val EXTRA_SOUND_MODE = "sound_mode"
        const val EXTRA_LOWER_HANDHELD_SOUND = "lower_handheld_sound"
        const val EXTRA_SCREEN_CLOAK_MODE = "screen_cloak_mode"
        const val EXTRA_SCREEN_CLOAK_OVERLAY_DISABLED = "screen_cloak_overlay_disabled"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_PROFILE_TITLE = "profile_title"
    }
}
