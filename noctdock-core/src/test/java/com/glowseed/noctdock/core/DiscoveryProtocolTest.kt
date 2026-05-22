package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryProtocolTest {
    private val identity = ReceiverIdentity(id = "receiver-1", publicKey = "local-key")

    @Test
    fun nsdTxtRecordEncodeDecodePreservesReceiverMetadata() {
        val encoded =
            NsdServiceInfoMapper.encodeTxt(
                identity = identity,
                deviceName = "Living Room TV",
                receiverAppVersion = "0.1.0",
                supportedCodecs = listOf(CodecCapability.H264, CodecCapability.H265),
                supportedMaxResolution = "1920x1080",
                formFactor = ReceiverFormFactor.TV,
                pairingRequired = true,
            )

        val decoded = NsdServiceInfoMapper.decodeTxtStrings(encoded)

        assertEquals(identity, decoded.identity)
        assertEquals("Living Room TV", decoded.deviceName)
        assertEquals(ProtocolVersion.Current, decoded.protocolVersion)
        assertEquals("0.1.0", decoded.receiverAppVersion)
        assertEquals(listOf(CodecCapability.H264, CodecCapability.H265), decoded.supportedCodecs)
        assertEquals("1920x1080", decoded.supportedMaxResolution)
        assertEquals(ReceiverFormFactor.TV, decoded.formFactor)
        assertTrue(decoded.videoCapabilities.supportsHevc)
        assertEquals(1920, decoded.videoCapabilities.maxWidth)
        assertEquals(1080, decoded.videoCapabilities.maxHeight)
        assertTrue(decoded.pairingRequired)
    }

    @Test
    fun receiverIdentityRequiresStableNonBlankValues() {
        val trusted =
            TrustedReceiver(
                identity = identity,
                displayName = "NoctDock TV - Living Room",
                lastHostAddress = "192.168.1.20",
                port = 45454,
                trustedAtMillis = 1000L,
            )

        assertEquals("receiver-1", trusted.identity.id)
        assertEquals("local-key", trusted.identity.publicKey)
    }

    @Test
    fun discoveredReceiversSortOnlineAndAlphabetically() {
        val sorted =
            DiscoverySorter.sort(
                receivers =
                listOf(
                    receiver("z", "Bedroom", online = true),
                    receiver("a", "Arcade", online = false),
                    receiver("m", "Media Room", online = true),
                ),
                lastUsedIdentityId = null,
            )

        assertEquals(listOf("Bedroom", "Media Room", "Arcade"), sorted.map { it.displayName })
    }

    @Test
    fun lastUsedReceiverHasPriorityWhenFound() {
        val sorted =
            DiscoverySorter.sort(
                receivers = listOf(receiver("a", "Arcade"), receiver("m", "Media Room")),
                lastUsedIdentityId = "m",
            )

        assertEquals("m", sorted.first().identity.id)
    }

    @Test
    fun pairingStateTransitionsRepresentFirstTrustAndReconnect() {
        val firstRun = listOf(PairingState.Required, PairingState.AwaitingCode, PairingState.Trusted)
        val trustedReconnect = listOf(PairingState.NotRequired, PairingState.Trusted)

        assertEquals(PairingState.Required, firstRun.first())
        assertEquals(PairingState.Trusted, firstRun.last())
        assertEquals(PairingState.NotRequired, trustedReconnect.first())
        assertEquals(PairingState.Trusted, trustedReconnect.last())
    }

    @Test
    fun manualIpFallbackValidationAcceptsLanHostsAndRejectsBadPorts() {
        assertTrue(ManualConnectionValidator.isValidHost("192.168.1.80"))
        assertTrue(ManualConnectionValidator.isValidHost("noctdock-tv.local"))
        assertFalse(ManualConnectionValidator.isValidHost("192.168.1.999"))
        assertFalse(ManualConnectionValidator.isValidHost(""))
        assertTrue(ManualConnectionValidator.isValidPort("45454"))
        assertFalse(ManualConnectionValidator.isValidPort("70000"))
    }

    private fun receiver(id: String, name: String, online: Boolean = true): DiscoveredReceiver = DiscoveredReceiver(
        identity = ReceiverIdentity(id, "key-$id"),
        deviceName = name,
        serviceName = name,
        hostAddress = "192.168.1.${id.hashCode().mod(200) + 20}",
        port = 45454,
        protocolVersion = ProtocolVersion.Current,
        receiverAppVersion = "0.1.0",
        supportedCodecs = listOf(CodecCapability.H264),
        supportedMaxResolution = "1920x1080",
        pairingRequired = true,
        isOnline = online,
    )
}
