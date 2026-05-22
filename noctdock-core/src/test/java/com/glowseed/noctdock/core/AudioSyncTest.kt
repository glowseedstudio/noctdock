package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSyncTest {
    @Test
    fun soundModeDefaultsToRetroidSound() {
        assertEquals(SoundMode.RETROID, PerformanceSettings().soundMode)
        assertEquals("Retroid Sound", SoundMode.RETROID.label)
    }

    @Test
    fun jitterBufferWaitsUntilTargetBuffer() {
        val buffer = AudioJitterBuffer(targetBufferMs = 45)
        buffer.configure()
        buffer.offer(packet(sequence = 1, timestampUs = 1_000_000), nowUs = 1_010_000)

        val decision = buffer.next(nowUs = 1_020_000)

        assertTrue(decision is AudioJitterDecision.Wait)
    }

    @Test
    fun jitterBufferDropsStaleAudio() {
        val buffer = AudioJitterBuffer(targetBufferMs = 30, maxLateMs = 20)
        buffer.configure()
        buffer.offer(packet(sequence = 1, timestampUs = 1_000_000), nowUs = 1_000_000)

        val decision = buffer.next(nowUs = 1_200_000)
        val snapshot = buffer.snapshot(nowUs = 1_200_000)

        assertTrue(decision is AudioJitterDecision.Underrun)
        assertEquals(1, snapshot.drops)
    }

    @Test
    fun jitterBufferReportsEstimatedOffset() {
        val buffer = AudioJitterBuffer(targetBufferMs = 45)
        buffer.configure()
        buffer.offer(packet(sequence = 1, timestampUs = 1_000_000), nowUs = 1_008_000)

        val snapshot = buffer.snapshot(nowUs = 1_020_000)

        assertTrue(snapshot.estimatedAvOffsetMs >= 0)
    }

    @Test
    fun packetSchedulerPrioritisesControlAndKeyFrames() {
        val scheduler = PacketScheduler()
        val ordered =
            scheduler.order(
                listOf(
                    ScheduledPacket(PacketPriority.Audio, 1),
                    ScheduledPacket(PacketPriority.VideoFrame, 1),
                    ScheduledPacket(PacketPriority.Control, 2),
                    ScheduledPacket(PacketPriority.VideoKeyFrame, 1),
                ),
            )

        assertEquals(PacketPriority.Control, ordered[0].priority)
        assertEquals(PacketPriority.VideoKeyFrame, ordered[1].priority)
        assertEquals(PacketPriority.VideoFrame, ordered[2].priority)
        assertEquals(PacketPriority.Audio, ordered[3].priority)
    }

    @Test
    fun volumeRestoreStateMachineRestoresExactlyOnce() {
        val state = VolumeRestoreStateMachine()

        assertTrue(state.begin(7))
        assertEquals(7, state.restore())
        assertEquals(null, state.restore())
    }

    private fun packet(sequence: Long, timestampUs: Long): PcmAudioPacket = PcmAudioPacket(
        streamId = 1,
        sequenceNumber = sequence,
        presentationTimeUs = timestampUs,
        data = ByteArray(960),
    )
}
