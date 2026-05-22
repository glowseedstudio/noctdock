package com.glowseed.noctdock.core

import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-format major/minor for LAN discovery and streaming compatibility checks.
 *
 * Discovery TXT records and session setup use this alongside [ReceiverIdentity] and codec caps.
 */
@Serializable
data class ProtocolVersion(val major: Int = 1, val minor: Int = 0) : Comparable<ProtocolVersion> {
    override fun compareTo(other: ProtocolVersion): Int = compareValuesBy(this, other, ProtocolVersion::major, ProtocolVersion::minor)

    override fun toString(): String = "$major.$minor"

    companion object {
        val Current = ProtocolVersion(1, 1)

        fun parse(value: String): ProtocolVersion {
            val parts = value.split(".")
            require(parts.size == 2) { "Protocol version must use major.minor format." }
            return ProtocolVersion(parts[0].toInt(), parts[1].toInt())
        }
    }
}

@Serializable
enum class CodecCapability(val wireName: String) {
    @SerialName("h264")
    H264("h264"),

    @SerialName("h265")
    H265("h265"),

    @SerialName("av1")
    AV1("av1"),
    ;

    companion object {
        fun fromWireName(value: String): CodecCapability = entries.firstOrNull { it.wireName == value.lowercase() }
            ?: throw IllegalArgumentException("Unsupported codec: $value")

        fun fromVideoCodec(codec: VideoCodec): CodecCapability = when (codec) {
            VideoCodec.AVC -> H264
            VideoCodec.HEVC -> H265
        }
    }
}

@Serializable
data class ReceiverIdentity(val id: String, val publicKey: String) {
    init {
        require(id.isNotBlank()) { "Receiver identity id must not be blank." }
        require(publicKey.isNotBlank()) { "Receiver identity key must not be blank." }
    }
}

@Serializable
enum class PairingState {
    NotRequired,
    Required,
    AwaitingCode,
    Trusted,
    Failed,
}

@Serializable
enum class DiscoveryState {
    Idle,
    Scanning,
    ReceiverFound,
    ReceiverLost,
    Failed,
}

@Serializable
data class DiscoveredReceiver(
    val identity: ReceiverIdentity,
    val deviceName: String,
    val serviceName: String,
    val hostAddress: String,
    val port: Int,
    val protocolVersion: ProtocolVersion,
    val receiverAppVersion: String,
    val supportedCodecs: List<CodecCapability>,
    val supportedMaxResolution: String,
    val formFactor: ReceiverFormFactor = ReceiverFormFactor.UNKNOWN,
    val videoCapabilities: ReceiverVideoCapabilities = ReceiverVideoCapabilities(
        supportsAvc = CodecCapability.H264 in supportedCodecs,
        supportsHevc = CodecCapability.H265 in supportedCodecs,
        maxWidth = supportedMaxResolution.substringBefore("x").toIntOrNull() ?: 1280,
        maxHeight = supportedMaxResolution.substringAfter("x").toIntOrNull() ?: 720,
    ),
    val pairingRequired: Boolean,
    val isOnline: Boolean = true,
    val discoveredAtMillis: Long = 0L,
) {
    val displayName: String = serviceName.ifBlank { deviceName }
    val endpoint: String = "$hostAddress:$port"
}

@Serializable
data class TrustedReceiver(val identity: ReceiverIdentity, val displayName: String, val lastHostAddress: String, val port: Int, val trustedAtMillis: Long, val trustedSenderToken: String? = null)

@Serializable
sealed interface PairingPacket {
    val receiverIdentityId: String

    @Serializable
    @SerialName("pairing_request")
    data class PairingRequest(override val receiverIdentityId: String, val senderName: String, val trustedIdentityId: String? = null, val trustedSenderToken: String? = null) : PairingPacket

    @Serializable
    @SerialName("pairing_challenge")
    data class PairingChallenge(override val receiverIdentityId: String, val receiverIdentityKey: String, val pairingCodeRequired: Boolean) : PairingPacket

    @Serializable
    @SerialName("pairing_code")
    data class PairingCode(override val receiverIdentityId: String, val code: String) : PairingPacket

    @Serializable
    @SerialName("pairing_result")
    data class PairingResult(override val receiverIdentityId: String, val accepted: Boolean, val trusted: Boolean, val reason: String = "", val trustedSenderToken: String? = null) : PairingPacket
}

object PairingTrust {
    fun canSkipChallenge(storedToken: String?, requestToken: String?): Boolean = !storedToken.isNullOrBlank() &&
        !requestToken.isNullOrBlank() &&
        storedToken == requestToken
}

object DiscoverySorter {
    fun sort(receivers: Collection<DiscoveredReceiver>, lastUsedIdentityId: String?): List<DiscoveredReceiver> = receivers.sortedWith(
        compareByDescending<DiscoveredReceiver> { it.identity.id == lastUsedIdentityId }
            .thenByDescending { it.isOnline }
            .thenBy { it.displayName.lowercase() },
    )
}

object ManualConnectionValidator {
    fun isValidHost(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length > 253 || trimmed.contains(" ")) return false
        val ipv4 = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        if (ipv4.matches(trimmed)) {
            return trimmed.split(".").all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
        }
        return Regex("""^[A-Za-z0-9.-]+$""").matches(trimmed) && trimmed.any { it.isLetterOrDigit() }
    }

    fun isValidPort(value: String): Boolean = value.toIntOrNull() in 1..65535
}

/** Parses `_noctdock._udp` NSD TXT attributes into typed discovery models. */
object NsdServiceInfoMapper {
    private const val KEY_IDENTITY_ID = "identity_id"
    private const val KEY_IDENTITY_PUBLIC = "identity_key"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_APP_VERSION = "app_version"
    private const val KEY_CODECS = "codecs"
    private const val KEY_MAX_RESOLUTION = "max_resolution"
    private const val KEY_MAX_FPS = "max_fps"
    private const val KEY_PREFERRED_CODEC = "pref_codec"
    private const val KEY_RECEIVER_MODEL = "model"
    private const val KEY_DECODER_NAME = "decoder"
    private const val KEY_LOW_LATENCY_DECODE = "ll_decode"
    private const val KEY_SHIELD = "shield"
    private const val KEY_FORM_FACTOR = "form"
    private const val KEY_PAIRING_REQUIRED = "pairing_required"

    fun createServiceInfo(
        identity: ReceiverIdentity,
        deviceName: String,
        serviceName: String,
        receiverAppVersion: String,
        supportedCodecs: List<CodecCapability>,
        supportedMaxResolution: String,
        videoCapabilities: ReceiverVideoCapabilities = ReceiverVideoCapabilities(
            supportsAvc = CodecCapability.H264 in supportedCodecs,
            supportsHevc = CodecCapability.H265 in supportedCodecs,
            maxWidth = supportedMaxResolution.substringBefore("x").toIntOrNull() ?: 1280,
            maxHeight = supportedMaxResolution.substringAfter("x").toIntOrNull() ?: 720,
            preferredCodec = if (CodecCapability.H265 in supportedCodecs) VideoCodec.HEVC else VideoCodec.AVC,
        ),
        pairingRequired: Boolean,
        formFactor: ReceiverFormFactor = ReceiverFormFactor.UNKNOWN,
        port: Int = NoctConstants.DEFAULT_DISCOVERY_PORT,
    ): NsdServiceInfo = NsdServiceInfo().apply {
        this.serviceName = serviceName
        serviceType = NoctConstants.NSD_SERVICE_TYPE
        this.port = port
        encodeTxt(
            identity = identity,
            deviceName = deviceName,
            receiverAppVersion = receiverAppVersion,
            supportedCodecs = supportedCodecs,
            supportedMaxResolution = supportedMaxResolution,
            videoCapabilities = videoCapabilities,
            pairingRequired = pairingRequired,
            formFactor = formFactor,
        ).forEach { (key, value) -> setAttribute(key, value) }
    }

    fun toDiscoveredReceiver(serviceInfo: NsdServiceInfo, nowMillis: Long): DiscoveredReceiver {
        val txt = decodeTxt(serviceInfo.attributes)
        return DiscoveredReceiver(
            identity = txt.identity,
            deviceName = txt.deviceName,
            serviceName = serviceInfo.serviceName,
            hostAddress = serviceInfo.resolvedHostAddress(),
            port = serviceInfo.port,
            protocolVersion = txt.protocolVersion,
            receiverAppVersion = txt.receiverAppVersion,
            supportedCodecs = txt.supportedCodecs,
            supportedMaxResolution = txt.supportedMaxResolution,
            videoCapabilities = txt.videoCapabilities,
            formFactor = txt.formFactor,
            pairingRequired = txt.pairingRequired,
            discoveredAtMillis = nowMillis,
        )
    }

    private fun NsdServiceInfo.resolvedHostAddress(): String = if (Build.VERSION.SDK_INT >= 34) {
        hostAddresses.firstOrNull()?.hostAddress.orEmpty()
    } else {
        @Suppress("DEPRECATION")
        host?.hostAddress.orEmpty()
    }

    fun encodeTxt(
        identity: ReceiverIdentity,
        deviceName: String,
        receiverAppVersion: String,
        supportedCodecs: List<CodecCapability>,
        supportedMaxResolution: String,
        videoCapabilities: ReceiverVideoCapabilities = ReceiverVideoCapabilities(
            supportsAvc = CodecCapability.H264 in supportedCodecs,
            supportsHevc = CodecCapability.H265 in supportedCodecs,
            maxWidth = supportedMaxResolution.substringBefore("x").toIntOrNull() ?: 1280,
            maxHeight = supportedMaxResolution.substringAfter("x").toIntOrNull() ?: 720,
            preferredCodec = if (CodecCapability.H265 in supportedCodecs) VideoCodec.HEVC else VideoCodec.AVC,
        ),
        pairingRequired: Boolean,
        formFactor: ReceiverFormFactor = ReceiverFormFactor.UNKNOWN,
    ): Map<String, String> = mapOf(
        KEY_IDENTITY_ID to identity.id,
        KEY_IDENTITY_PUBLIC to identity.publicKey,
        KEY_DEVICE_NAME to deviceName,
        KEY_PROTOCOL to ProtocolVersion.Current.toString(),
        KEY_APP_VERSION to receiverAppVersion,
        KEY_CODECS to supportedCodecs.joinToString(",") { it.wireName },
        KEY_MAX_RESOLUTION to supportedMaxResolution,
        KEY_MAX_FPS to videoCapabilities.maxFps.toString(),
        KEY_PREFERRED_CODEC to videoCapabilities.preferredCodec.name.lowercase(),
        KEY_RECEIVER_MODEL to videoCapabilities.receiverModel.take(36),
        KEY_DECODER_NAME to videoCapabilities.decoderName.take(36),
        KEY_LOW_LATENCY_DECODE to videoCapabilities.lowLatencyDecodeSupported.toString(),
        KEY_SHIELD to videoCapabilities.shieldOptimized.toString(),
        KEY_FORM_FACTOR to formFactor.name,
        KEY_PAIRING_REQUIRED to pairingRequired.toString(),
    )

    fun decodeTxt(attributes: Map<String, ByteArray>): NsdTxtRecord = decodeTxtStrings(attributes.mapValues { it.value.toString(Charsets.UTF_8) })

    fun decodeTxtStrings(attributes: Map<String, String>): NsdTxtRecord {
        fun required(key: String): String = attributes[key]?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing NSD TXT attribute: $key")

        val supportedCodecs = required(KEY_CODECS).split(",").filter { it.isNotBlank() }.map(CodecCapability::fromWireName)
        val maxResolution = required(KEY_MAX_RESOLUTION)
        val maxWidth = maxResolution.substringBefore("x").toIntOrNull() ?: 1280
        val maxHeight = maxResolution.substringAfter("x").toIntOrNull() ?: 720
        val preferredCodec =
            when (attributes[KEY_PREFERRED_CODEC]?.lowercase()) {
                "hevc" -> VideoCodec.HEVC
                else -> VideoCodec.AVC
            }
        return NsdTxtRecord(
            identity = ReceiverIdentity(required(KEY_IDENTITY_ID), required(KEY_IDENTITY_PUBLIC)),
            deviceName = required(KEY_DEVICE_NAME),
            protocolVersion = ProtocolVersion.parse(required(KEY_PROTOCOL)),
            receiverAppVersion = required(KEY_APP_VERSION),
            supportedCodecs = supportedCodecs,
            supportedMaxResolution = maxResolution,
            formFactor = attributes[KEY_FORM_FACTOR]?.toReceiverFormFactor() ?: ReceiverFormFactor.UNKNOWN,
            videoCapabilities =
            ReceiverVideoCapabilities(
                supportsAvc = CodecCapability.H264 in supportedCodecs,
                supportsHevc = CodecCapability.H265 in supportedCodecs,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                maxFps = attributes[KEY_MAX_FPS]?.toIntOrNull() ?: 60,
                preferredCodec = preferredCodec,
                decoderName = attributes[KEY_DECODER_NAME].orEmpty(),
                lowLatencyDecodeSupported = attributes[KEY_LOW_LATENCY_DECODE]?.toBooleanStrictOrNull() ?: false,
                receiverModel = attributes[KEY_RECEIVER_MODEL].orEmpty(),
                shieldOptimized = attributes[KEY_SHIELD]?.toBooleanStrictOrNull() ?: false,
            ),
            pairingRequired = required(KEY_PAIRING_REQUIRED).toBooleanStrict(),
        )
    }
}

private fun String.toReceiverFormFactor(): ReceiverFormFactor = runCatching { ReceiverFormFactor.valueOf(uppercase()) }.getOrDefault(ReceiverFormFactor.UNKNOWN)

data class NsdTxtRecord(
    val identity: ReceiverIdentity,
    val deviceName: String,
    val protocolVersion: ProtocolVersion,
    val receiverAppVersion: String,
    val supportedCodecs: List<CodecCapability>,
    val supportedMaxResolution: String,
    val formFactor: ReceiverFormFactor,
    val videoCapabilities: ReceiverVideoCapabilities,
    val pairingRequired: Boolean,
)
