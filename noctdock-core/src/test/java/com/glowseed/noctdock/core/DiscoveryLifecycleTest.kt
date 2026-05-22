package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryLifecycleTest {
    @Test
    fun receiverReturnsToAdvertisingAfterStreamEnds() {
        val state =
            DiscoveryLifecycleReducer.receiverState(
                advertising = true,
                streamActive = false,
                pairingState = PairingState.Required,
                restarting = false,
                failed = false,
            )
        assertEquals(DiscoveryLifecycleState.ADVERTISING, state)
    }

    @Test
    fun receiverRestartingAdvertisementState() {
        val state =
            DiscoveryLifecycleReducer.receiverState(
                advertising = false,
                streamActive = false,
                pairingState = PairingState.NotRequired,
                restarting = true,
                failed = false,
            )
        assertEquals(DiscoveryLifecycleState.RESTARTING_ADVERTISEMENT, state)
    }

    @Test
    fun senderDisconnectedState() {
        val state =
            DiscoveryLifecycleReducer.senderState(
                discoveryState = DiscoveryState.ReceiverLost,
                streamState = StreamSessionState.Stopped,
                pairingState = PairingState.NotRequired,
                refreshing = false,
            )
        assertEquals(DiscoveryLifecycleState.DISCONNECTED, state)
    }

    @Test
    fun nsdRegistrationGatePreventsDuplicateStart() {
        val gate = NsdRegistrationGate()
        assertTrue(gate.canStartRegistration(listenerAttached = false))
        gate.markRegistering()
        assertFalse(gate.canStartRegistration(listenerAttached = false))
        gate.markRegistered()
        assertFalse(gate.canStartRegistration(listenerAttached = true))
    }

    @Test
    fun nsdRestartBackoffHasRetries() {
        assertTrue(NsdRestartBackoff.hasMoreAttempts(0))
        assertTrue(NsdRestartBackoff.delayForAttempt(0) >= 300L)
    }
}
