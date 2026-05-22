package com.glowseed.noctdock.core

import kotlin.math.max
import kotlin.math.min

enum class ReceiverAudioState {
    Waiting,
    Configured,
    Playing,
    Underrun,
    Stopped,
    Error,
}

data class AudioSyncSnapshot(
    val state: ReceiverAudioState = ReceiverAudioState.Waiting,
    val bufferMs: Int = 0,
    val underruns: Int = 0,
    val drops: Int = 0,
    val packetsReceived: Int = 0,
    val estimatedAvOffsetMs: Int = 0,
)

sealed class AudioJitterDecision {
    data class Play(val packet: PcmAudioPacket) : AudioJitterDecision()

    data class Wait(val delayMs: Long) : AudioJitterDecision()

    data object Underrun : AudioJitterDecision()
}

/**
 * Receiver-side PCM jitter buffer targeting ~30–60 ms playout delay.
 * Drops late packets instead of building unbounded latency when the sender runs ahead.
 */
class AudioJitterBuffer(private val targetBufferMs: Int = 45, private val maxBufferMs: Int = 90, private val maxLateMs: Int = 70) {
    private val packets = ArrayDeque<PcmAudioPacket>()
    private var senderToReceiverOffsetUs: Long? = null
    private var state = ReceiverAudioState.Waiting
    private var underruns = 0
    private var drops = 0
    private var received = 0

    fun configure() {
        packets.clear()
        senderToReceiverOffsetUs = null
        state = ReceiverAudioState.Configured
    }

    fun offer(packet: PcmAudioPacket, nowUs: Long) {
        if (state == ReceiverAudioState.Waiting ||
            state == ReceiverAudioState.Stopped
        ) {
            state = ReceiverAudioState.Configured
        }
        senderToReceiverOffsetUs = senderToReceiverOffsetUs ?: (nowUs - packet.presentationTimeUs)
        received += 1
        while (packets.isNotEmpty() && packet.presentationTimeUs <= packets.last().presentationTimeUs) {
            packets.removeLast()
            drops += 1
        }
        packets.addLast(packet)
        trimLargeBuffer(nowUs)
    }

    fun next(nowUs: Long): AudioJitterDecision {
        val first = packets.firstOrNull()
        if (first == null) {
            underruns += 1
            state = ReceiverAudioState.Underrun
            return AudioJitterDecision.Underrun
        }
        val offset = senderToReceiverOffsetUs ?: 0L
        while (packets.isNotEmpty()) {
            val playAtUs = packets.first().presentationTimeUs + offset + targetBufferMs * 1_000L
            val lateByMs = (nowUs - playAtUs) / 1_000L
            if (lateByMs <= maxLateMs) break
            packets.removeFirst()
            drops += 1
        }
        val packet = packets.firstOrNull()
        if (packet == null) {
            underruns += 1
            state = ReceiverAudioState.Underrun
            return AudioJitterDecision.Underrun
        }
        val playAtUs = packet.presentationTimeUs + offset + targetBufferMs * 1_000L
        val waitMs = (playAtUs - nowUs) / 1_000L
        if (waitMs > 2L) return AudioJitterDecision.Wait(min(waitMs, 12L))
        state = ReceiverAudioState.Playing
        return AudioJitterDecision.Play(packets.removeFirst())
    }

    fun stop() {
        packets.clear()
        senderToReceiverOffsetUs = null
        state = ReceiverAudioState.Stopped
    }

    fun snapshot(nowUs: Long): AudioSyncSnapshot {
        val offset = senderToReceiverOffsetUs ?: 0L
        val bufferMs =
            if (packets.isEmpty()) {
                0
            } else {
                val lastPlayUs = packets.last().presentationTimeUs + offset + targetBufferMs * 1_000L
                max(0, ((lastPlayUs - nowUs) / 1_000L).toInt())
            }
        val avOffsetMs =
            packets.firstOrNull()?.let { packet ->
                ((nowUs - (packet.presentationTimeUs + offset)) / 1_000L).toInt()
            } ?: 0
        return AudioSyncSnapshot(
            state = state,
            bufferMs = bufferMs.coerceIn(0, maxBufferMs),
            underruns = underruns,
            drops = drops,
            packetsReceived = received,
            estimatedAvOffsetMs = avOffsetMs,
        )
    }

    private fun trimLargeBuffer(nowUs: Long) {
        val offset = senderToReceiverOffsetUs ?: return
        while (packets.size > 1) {
            val bufferedMs =
                ((packets.last().presentationTimeUs + offset + targetBufferMs * 1_000L - nowUs) / 1_000L)
                    .toInt()
            if (bufferedMs <= maxBufferMs) return
            packets.removeFirst()
            drops += 1
        }
    }
}

enum class PacketPriority {
    Control,
    VideoKeyFrame,
    VideoFrame,
    Audio,
}

data class ScheduledPacket(val priority: PacketPriority, val createdAtUs: Long)

class PacketScheduler {
    fun order(packets: List<ScheduledPacket>): List<ScheduledPacket> = packets.sortedWith(
        compareBy<ScheduledPacket> {
            when (it.priority) {
                PacketPriority.Control -> 0
                PacketPriority.VideoKeyFrame -> 1
                PacketPriority.VideoFrame -> 2
                PacketPriority.Audio -> 3
            }
        }.thenBy { it.createdAtUs },
    )
}

class VolumeRestoreStateMachine {
    private var savedVolume: Int? = null

    fun begin(currentVolume: Int): Boolean {
        if (savedVolume != null) return false
        savedVolume = currentVolume
        return true
    }

    fun restore(): Int? {
        val volume = savedVolume
        savedVolume = null
        return volume
    }

    val active: Boolean get() = savedVolume != null
}
