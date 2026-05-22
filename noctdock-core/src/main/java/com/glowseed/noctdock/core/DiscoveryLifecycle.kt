package com.glowseed.noctdock.core

import kotlinx.serialization.Serializable

/**
 * Shared discovery/advertisement lifecycle for sender discovery and receiver NSD broadcast.
 */
@Serializable
enum class DiscoveryLifecycleState {
    WAITING,
    ADVERTISING,
    PAIRING,
    CONNECTED,
    STREAMING,
    DISCONNECTED,
    RESTARTING_ADVERTISEMENT,
    ERROR,
}

object DiscoveryLifecycleReducer {
    fun receiverState(advertising: Boolean, streamActive: Boolean, pairingState: PairingState, restarting: Boolean, failed: Boolean): DiscoveryLifecycleState = when {
        failed -> DiscoveryLifecycleState.ERROR
        restarting -> DiscoveryLifecycleState.RESTARTING_ADVERTISEMENT
        streamActive -> DiscoveryLifecycleState.STREAMING
        pairingState == PairingState.AwaitingCode -> DiscoveryLifecycleState.PAIRING
        pairingState == PairingState.Trusted && !streamActive -> DiscoveryLifecycleState.CONNECTED
        advertising -> DiscoveryLifecycleState.ADVERTISING
        else -> DiscoveryLifecycleState.WAITING
    }

    fun senderState(discoveryState: DiscoveryState, streamState: StreamSessionState, pairingState: PairingState, refreshing: Boolean): DiscoveryLifecycleState = when {
        streamState == StreamSessionState.Active -> DiscoveryLifecycleState.STREAMING

        streamState == StreamSessionState.StartingService ||
            streamState == StreamSessionState.CheckingEncoder ||
            streamState == StreamSessionState.ConnectingReceiver -> DiscoveryLifecycleState.CONNECTED

        refreshing -> DiscoveryLifecycleState.RESTARTING_ADVERTISEMENT

        pairingState == PairingState.AwaitingCode -> DiscoveryLifecycleState.PAIRING

        discoveryState == DiscoveryState.Scanning -> DiscoveryLifecycleState.ADVERTISING

        discoveryState == DiscoveryState.ReceiverFound -> DiscoveryLifecycleState.CONNECTED

        discoveryState == DiscoveryState.Failed -> DiscoveryLifecycleState.ERROR

        discoveryState == DiscoveryState.ReceiverLost -> DiscoveryLifecycleState.DISCONNECTED

        else -> DiscoveryLifecycleState.WAITING
    }
}

object NsdRestartBackoff {
    private val delaysMs = longArrayOf(300L, 600L, 1_200L, 2_400L, 5_000L)

    fun delayForAttempt(attempt: Int): Long = delaysMs[attempt.coerceIn(0, delaysMs.lastIndex)]

    fun hasMoreAttempts(attempt: Int): Boolean = attempt < delaysMs.lastIndex
}

/**
 * Prevents overlapping NSD register calls while an unregister is still in flight.
 */
class NsdRegistrationGate {
    private var registrationInFlight: Boolean = false
    private var pendingRestart: Boolean = false

    fun canStartRegistration(listenerAttached: Boolean): Boolean = !registrationInFlight && !listenerAttached

    fun markRegistering() {
        registrationInFlight = true
        pendingRestart = false
    }

    fun markRegistered() {
        registrationInFlight = false
    }

    fun markUnregistered() {
        registrationInFlight = false
    }

    fun requestRestart(): Boolean {
        pendingRestart = true
        return registrationInFlight
    }

    fun consumePendingRestart(): Boolean {
        val pending = pendingRestart
        pendingRestart = false
        return pending
    }

    fun clear() {
        registrationInFlight = false
        pendingRestart = false
    }
}
