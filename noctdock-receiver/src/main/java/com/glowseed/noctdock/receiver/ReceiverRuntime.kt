package com.glowseed.noctdock.receiver

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glowseed.noctdock.core.CodecCapability
import com.glowseed.noctdock.core.DiscoveryLifecycleReducer
import com.glowseed.noctdock.core.DiscoveryLifecycleState
import com.glowseed.noctdock.core.NoctConstants
import com.glowseed.noctdock.core.NoctLog
import com.glowseed.noctdock.core.NoctSupportReportMetadata
import com.glowseed.noctdock.core.NsdRegistrationGate
import com.glowseed.noctdock.core.NsdRestartBackoff
import com.glowseed.noctdock.core.NsdServiceInfoMapper
import com.glowseed.noctdock.core.PairingPacket
import com.glowseed.noctdock.core.PairingState
import com.glowseed.noctdock.core.PairingTrust
import com.glowseed.noctdock.core.ReceiverDeviceTraits
import com.glowseed.noctdock.core.ReceiverDisplayWording
import com.glowseed.noctdock.core.ReceiverFormFactor
import com.glowseed.noctdock.core.ReceiverFormFactorDetector
import com.glowseed.noctdock.core.ReceiverIdentity
import com.glowseed.noctdock.core.ReceiverScaleMode
import com.glowseed.noctdock.core.ReceiverVideoCapabilities
import com.glowseed.noctdock.core.StreamMetrics
import com.glowseed.noctdock.core.StreamSourceMetadata
import com.glowseed.noctdock.core.VideoCodec
import com.glowseed.noctdock.core.formatSupportReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.UUID

private val Context.receiverDataStore by preferencesDataStore(name = "noctdock_receiver")

private fun Context.receiverFormFactor(): ReceiverFormFactor {
    val configuration = resources.configuration
    return ReceiverFormFactorDetector.detect(
        ReceiverDeviceTraits(
            hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
            smallestWidthDp = configuration.smallestScreenWidthDp,
        ),
    )
}

private fun ReceiverFormFactor.fallbackDeviceName(): String = when (this) {
    ReceiverFormFactor.TV -> "Android TV"
    ReceiverFormFactor.PHONE -> "Android phone"
    ReceiverFormFactor.TABLET -> "Android tablet"
    ReceiverFormFactor.UNKNOWN -> "Android screen"
}

internal enum class ReceiverUiPhase {
    WAITING,
    PAIRING,
    ACTIVE,
    INTERRUPTED,
}

internal object ReceiverUiPhaseResolver {
    fun resolve(pairingState: PairingState, streamActive: Boolean, hasPlaybackError: Boolean): ReceiverUiPhase = when {
        hasPlaybackError || pairingState == PairingState.Failed -> ReceiverUiPhase.INTERRUPTED
        streamActive -> ReceiverUiPhase.ACTIVE
        pairingState == PairingState.AwaitingCode -> ReceiverUiPhase.PAIRING
        else -> ReceiverUiPhase.WAITING
    }
}

internal data class ReceiverPersistedSettings(
    val startFullscreen: Boolean,
    val keepScreenAwake: Boolean,
    val preferLandscapeWhilePlaying: Boolean,
    val scaleModeName: String,
    val receiverName: String,
)

internal object ReceiverSettingsPersistence {
    fun fromStoredValues(startFullscreen: Boolean?, keepScreenAwake: Boolean?, preferLandscapeWhilePlaying: Boolean?, scaleModeName: String?, receiverName: String?): ReceiverSettings =
        ReceiverSettings(
            startFullscreen = startFullscreen ?: true,
            keepScreenAwake = keepScreenAwake ?: true,
            preferLandscapeWhilePlaying = preferLandscapeWhilePlaying ?: true,
            scaleMode = scaleModeName?.let { runCatching { ReceiverScaleMode.valueOf(it) }.getOrNull() } ?: ReceiverScaleMode.FIT,
            receiverName = sanitizeReceiverName(receiverName.orEmpty()),
        )

    fun toStoredValues(settings: ReceiverSettings): ReceiverPersistedSettings = ReceiverPersistedSettings(
        startFullscreen = settings.startFullscreen,
        keepScreenAwake = settings.keepScreenAwake,
        preferLandscapeWhilePlaying = settings.preferLandscapeWhilePlaying,
        scaleModeName = settings.scaleMode.name,
        receiverName = sanitizeReceiverName(settings.receiverName),
    )

    fun sanitizeReceiverName(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(40)

    fun resolvedReceiverName(savedName: String, defaultName: String): String = sanitizeReceiverName(savedName).ifBlank { defaultName }
}

private fun defaultFriendlyReceiverName(formFactor: ReceiverFormFactor, deviceName: String?): String = deviceName?.takeIf { it.isNotBlank() } ?: ReceiverDisplayWording.genericName(formFactor)

data class ReceiverUiState(
    val identity: ReceiverIdentity? = null,
    val advertisedName: String = "",
    val defaultReceiverName: String = "NoctDock Receiver",
    val localIpAddress: String = "Unavailable",
    val port: Int = NoctConstants.DEFAULT_DISCOVERY_PORT,
    val pairingCode: String = "",
    val pairingState: PairingState = PairingState.Required,
    val advertising: Boolean = false,
    val udpListening: Boolean = false,
    val trustedSenderCount: Int = 0,
    val senderName: String = "",
    val streamActive: Boolean = false,
    val streamError: String? = null,
    val metrics: StreamMetrics = StreamMetrics(),
    val decoderName: String = "Not selected",
    val sourceMetadata: StreamSourceMetadata = StreamSourceMetadata.unknown(),
    val status: String = "Starting receiver",
    val formFactor: ReceiverFormFactor = ReceiverFormFactor.UNKNOWN,
    val receiverSettings: ReceiverSettings = ReceiverSettings(),
    val discoveryLifecycleState: DiscoveryLifecycleState = DiscoveryLifecycleState.WAITING,
    val lastBroadcastRestartAtMillis: Long = 0L,
    val broadcastRestartAttempts: Int = 0,
    val supportReportCopied: Boolean = false,
)

data class ReceiverSettings(
    val startFullscreen: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val preferLandscapeWhilePlaying: Boolean = true,
    val scaleMode: ReceiverScaleMode = ReceiverScaleMode.FIT,
    val receiverName: String = "",
)

class ReceiverViewModel(private val app: Application) : AndroidViewModel(app) {
    private val identityStore = ReceiverIdentityStore(app)
    private val json = Json { ignoreUnknownKeys = true }
    private val nsdManager = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val secureRandom = SecureRandom()
    val receiverSessionController = ReceiverSessionController(app.applicationContext)
    private var registrationListener: NsdManager.RegistrationListener? = null
    private val registrationGate = NsdRegistrationGate()
    private var advertisingRestartJob: Job? = null
    private var pendingSenderName: String = ""
    private var cachedTrustedSenderToken: String? = null
    private val pairingFailuresByHost = mutableMapOf<String, PairingFailureTracker>()
    private var isAppForeground: Boolean = true
    private var wasStreamActive: Boolean = false

    private data class PairingFailureTracker(var count: Int = 0, var windowStartMillis: Long = 0L)

    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val identity = identityStore.identity()
            val formFactor = app.receiverFormFactor()
            val settings = identityStore.receiverSettings()
            val code = nextPairingCode()
            val trustedCount = identityStore.trustedSenderNames().size
            cachedTrustedSenderToken = identityStore.trustedSenderToken()
            val deviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: formFactor.fallbackDeviceName()
            val defaultName = defaultFriendlyReceiverName(formFactor, deviceName)
            val advertisedName = ReceiverSettingsPersistence.resolvedReceiverName(settings.receiverName, defaultName)
            _uiState.value =
                _uiState.value.copy(
                    identity = identity,
                    advertisedName = advertisedName,
                    defaultReceiverName = defaultName,
                    localIpAddress = localIpAddress(),
                    pairingCode = code,
                    pairingState = PairingState.Required,
                    trustedSenderCount = trustedCount,
                    status = "Waiting for handheld",
                    formFactor = formFactor,
                    receiverSettings = settings,
                )
            if (isAppForeground) {
                startUdpListener(identity)
                startAdvertising(identity, deviceName, advertisedName, formFactor)
            }
        }
        viewModelScope.launch {
            receiverSessionController.state.collect { playback ->
                val previousActive = wasStreamActive
                wasStreamActive = playback.active
                val nextStatus =
                    when {
                        playback.error != null -> "Connection interrupted"
                        playback.active -> playback.sourceMetadata.displayTitle
                        !playback.active && previousActive -> "Waiting for handheld"
                        else -> _uiState.value.status
                    }
                playback.error?.let { errorMessage ->
                    if (_uiState.value.streamError != errorMessage) NoctLog.warn("Receiver", errorMessage)
                }
                _uiState.value =
                    _uiState.value.copy(
                        streamActive = playback.active && playback.error == null,
                        streamError = playback.error,
                        senderName = _uiState.value.senderName.ifBlank { playback.senderAddress },
                        metrics = playback.metrics,
                        decoderName = playback.decoderName,
                        sourceMetadata = playback.sourceMetadata,
                        status = nextStatus,
                        pairingState =
                        if (
                            !playback.active &&
                            previousActive &&
                            _uiState.value.pairingState != PairingState.Trusted
                        ) {
                            PairingState.Required
                        } else {
                            _uiState.value.pairingState
                        },
                    )
                publishDiscoveryLifecycle(advertisingFailed = _uiState.value.discoveryLifecycleState == DiscoveryLifecycleState.ERROR)
                if (!playback.active && previousActive) {
                    handleStreamDisconnected()
                }
            }
        }
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        if (foreground) {
            val identity = _uiState.value.identity ?: return
            if (!_uiState.value.udpListening) startUdpListener(identity)
            ensureBroadcasting(forceRestart = !_uiState.value.advertising)
        } else {
            pauseForBackground()
        }
    }

    private fun pauseForBackground() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
        advertisingRestartJob?.cancel()
        val status =
            when {
                _uiState.value.streamActive -> "Receiver in background — stream still active"
                else -> "Receiver paused"
            }
        _uiState.value = _uiState.value.copy(advertising = false, status = status)
        publishDiscoveryLifecycle(restarting = false, advertisingFailed = false)
        if (!_uiState.value.streamActive) {
            receiverSessionController.stop()
            _uiState.value = _uiState.value.copy(udpListening = false, streamActive = false, metrics = StreamMetrics())
        }
    }

    fun stopReceiverRuntime(status: String = "Receiver stopped") {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
        receiverSessionController.stop()
        _uiState.value =
            _uiState.value.copy(
                advertising = false,
                udpListening = false,
                streamActive = false,
                metrics = StreamMetrics(),
                status = status,
            )
    }

    private fun handleStreamDisconnected() {
        ensureBroadcasting(forceRestart = true)
    }

    private fun ensureBroadcasting(forceRestart: Boolean = false) {
        if (!isAppForeground) return
        val identity = _uiState.value.identity ?: return
        if (forceRestart || !_uiState.value.advertising || registrationListener == null) {
            scheduleAdvertisingRestart(resetAttempts = forceRestart)
        }
    }

    private fun scheduleAdvertisingRestart(resetAttempts: Boolean = false) {
        if (!isAppForeground) return
        advertisingRestartJob?.cancel()
        advertisingRestartJob =
            viewModelScope.launch {
                val attempt = if (resetAttempts) 0 else _uiState.value.broadcastRestartAttempts
                publishDiscoveryLifecycle(restarting = true, advertisingFailed = false)
                unregisterAdvertising()
                delay(NsdRestartBackoff.delayForAttempt(attempt))
                val identity = _uiState.value.identity ?: return@launch
                val formFactor = _uiState.value.formFactor
                val deviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: formFactor.fallbackDeviceName()
                val advertisedName =
                    ReceiverSettingsPersistence.resolvedReceiverName(
                        _uiState.value.receiverSettings.receiverName,
                        _uiState.value.defaultReceiverName.ifBlank { defaultFriendlyReceiverName(formFactor, deviceName) },
                    )
                registerAdvertising(identity, deviceName, advertisedName, formFactor, attempt)
            }
    }

    private fun unregisterAdvertising() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
        registrationGate.clear()
        _uiState.value = _uiState.value.copy(advertising = false)
    }

    private fun registerAdvertising(identity: ReceiverIdentity, deviceName: String, advertisedName: String, formFactor: ReceiverFormFactor, attempt: Int) {
        if (!registrationGate.canStartRegistration(registrationListener != null)) {
            registrationGate.requestRestart()
            return
        }
        registrationGate.markRegistering()
        startAdvertising(identity, deviceName, advertisedName, formFactor, attempt)
    }

    private fun startAdvertising(identity: ReceiverIdentity, deviceName: String, advertisedName: String, formFactor: ReceiverFormFactor, attempt: Int = 0) {
        if (registrationListener != null) {
            registrationGate.requestRestart()
            return
        }
        val capabilities = receiverVideoCapabilities(deviceName)
        val serviceInfo =
            NsdServiceInfoMapper.createServiceInfo(
                identity = identity,
                deviceName = deviceName,
                serviceName = advertisedName,
                receiverAppVersion = BuildConfig.VERSION_NAME,
                supportedCodecs =
                buildList {
                    if (capabilities.supportsAvc) add(CodecCapability.H264)
                    if (capabilities.supportsHevc) add(CodecCapability.H265)
                }.ifEmpty { listOf(CodecCapability.H264) },
                supportedMaxResolution = "${capabilities.maxWidth}x${capabilities.maxHeight}",
                videoCapabilities = capabilities,
                pairingRequired = true,
                formFactor = formFactor,
            )

        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    registrationGate.markRegistered()
                    val now = System.currentTimeMillis()
                    _uiState.value =
                        _uiState.value.copy(
                            advertisedName = info.serviceName,
                            advertising = true,
                            lastBroadcastRestartAtMillis = now,
                            broadcastRestartAttempts = 0,
                            status =
                            if (_uiState.value.streamActive) {
                                _uiState.value.sourceMetadata.displayTitle
                            } else {
                                "Waiting for handheld"
                            },
                        )
                    publishDiscoveryLifecycle(restarting = false, advertisingFailed = false)
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    registrationListener = null
                    registrationGate.markUnregistered()
                    val nextAttempt = attempt + 1
                    _uiState.value =
                        _uiState.value.copy(
                            advertising = false,
                            broadcastRestartAttempts = nextAttempt,
                            status = "Advertising failed: $errorCode",
                        )
                    publishDiscoveryLifecycle(restarting = false, advertisingFailed = true)
                    if (isAppForeground && NsdRestartBackoff.hasMoreAttempts(attempt)) {
                        scheduleAdvertisingRestart(resetAttempts = false)
                    }
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    registrationListener = null
                    registrationGate.markUnregistered()
                    _uiState.value = _uiState.value.copy(advertising = false)
                    publishDiscoveryLifecycle(restarting = false, advertisingFailed = false)
                    if (registrationGate.consumePendingRestart() || isAppForeground) {
                        scheduleAdvertisingRestart(resetAttempts = true)
                    }
                }

                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    _uiState.value = _uiState.value.copy(status = "Advertising stop failed: $errorCode")
                }
            }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun receiverVideoCapabilities(deviceName: String): ReceiverVideoCapabilities {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        fun decoderFor(mime: String): MediaCodecInfo? {
            val candidates = codecs.filter { info -> info.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) } }
            return candidates.firstOrNull { it.isHardwareAccelerated }
                ?: candidates.firstOrNull { !it.name.contains("google", ignoreCase = true) }
                ?: candidates.firstOrNull()
        }
        val avc = decoderFor(MediaFormat.MIMETYPE_VIDEO_AVC)
        val hevc = decoderFor(MediaFormat.MIMETYPE_VIDEO_HEVC)
        val decoder = hevc ?: avc
        val lowLatency =
            decoder?.let { info ->
                val mime = if (info == hevc) MediaFormat.MIMETYPE_VIDEO_HEVC else MediaFormat.MIMETYPE_VIDEO_AVC
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        info.getCapabilitiesForType(mime).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
                    }
                        .getOrDefault(false)
                } else {
                    false
                }
            } ?: false
        val shield = isNvidiaShield()
        return ReceiverVideoCapabilities(
            supportsAvc = avc != null,
            supportsHevc = hevc != null,
            maxWidth = if (hevc != null || avc != null) 1920 else 1280,
            maxHeight = if (hevc != null || avc != null) 1080 else 720,
            maxFps = 60,
            preferredCodec = if (hevc != null) VideoCodec.HEVC else VideoCodec.AVC,
            decoderName = decoder?.name ?: "Unavailable",
            lowLatencyDecodeSupported = lowLatency,
            receiverModel = if (shield) "NVIDIA Shield" else deviceName,
            shieldOptimized = shield,
        )
    }

    private fun isNvidiaShield(): Boolean {
        val text =
            listOf(Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.PRODUCT)
                .joinToString(" ")
                .lowercase()
        return "nvidia" in text && ("shield" in text || "mdarcy" in text || "darcy" in text || "sif" in text)
    }

    private fun startUdpListener(identity: ReceiverIdentity) {
        if (_uiState.value.udpListening) return
        val deviceName = _uiState.value.advertisedName.ifBlank { _uiState.value.defaultReceiverName }
        receiverSessionController.start(
            onPairingPayload = { boundSocket, address, port, payload ->
                handlePacket(identity, boundSocket, address, port, payload)
            },
            onStreamEnded = { handleStreamDisconnected() },
            receiverVideoCapabilities = { receiverVideoCapabilities(deviceName) },
        )
        _uiState.value = _uiState.value.copy(udpListening = true)
    }

    private fun publishDiscoveryLifecycle(restarting: Boolean = _uiState.value.discoveryLifecycleState == DiscoveryLifecycleState.RESTARTING_ADVERTISEMENT, advertisingFailed: Boolean = false) {
        val state =
            DiscoveryLifecycleReducer.receiverState(
                advertising = _uiState.value.advertising,
                streamActive = _uiState.value.streamActive,
                pairingState = _uiState.value.pairingState,
                restarting = restarting,
                failed = advertisingFailed,
            )
        _uiState.value = _uiState.value.copy(discoveryLifecycleState = state)
    }

    private fun handlePacket(identity: ReceiverIdentity, socket: DatagramSocket, address: InetAddress, port: Int, payload: String) {
        val packet = runCatching { json.decodeFromString<PairingPacket>(payload) }.getOrNull() ?: return
        if (packet.receiverIdentityId != identity.id && packet.receiverIdentityId != "*") return

        when (packet) {
            is PairingPacket.PairingRequest -> {
                val trusted = PairingTrust.canSkipChallenge(cachedTrustedSenderToken, packet.trustedSenderToken)
                pendingSenderName = packet.senderName
                if (trusted) {
                    send(
                        socket,
                        address,
                        port,
                        PairingPacket.PairingResult(
                            identity.id,
                            accepted = true,
                            trusted = true,
                            trustedSenderToken = cachedTrustedSenderToken,
                        ),
                    )
                    _uiState.value =
                        _uiState.value.copy(
                            pairingState = PairingState.Trusted,
                            senderName = packet.senderName,
                            streamActive = false,
                            status = "Screen ready",
                        )
                } else {
                    send(
                        socket,
                        address,
                        port,
                        PairingPacket.PairingChallenge(
                            receiverIdentityId = identity.id,
                            receiverIdentityKey = identity.publicKey,
                            pairingCodeRequired = true,
                        ),
                    )
                    _uiState.value = _uiState.value.copy(pairingState = PairingState.AwaitingCode, status = "Pairing requested")
                }
            }

            is PairingPacket.PairingCode -> {
                val hostKey = address.hostAddress.orEmpty()
                if (isPairingRateLimited(hostKey)) {
                    send(
                        socket,
                        address,
                        port,
                        PairingPacket.PairingResult(identity.id, accepted = false, trusted = false, reason = "Too many attempts"),
                    )
                    return
                }
                val accepted = packet.code == _uiState.value.pairingCode
                if (!accepted) {
                    recordPairingFailure(hostKey)
                } else {
                    pairingFailuresByHost.remove(hostKey)
                }
                val trustedToken = if (accepted) UUID.randomUUID().toString() else null
                val resultState = if (accepted) PairingState.Trusted else PairingState.Failed
                send(
                    socket,
                    address,
                    port,
                    PairingPacket.PairingResult(
                        identity.id,
                        accepted = accepted,
                        trusted = accepted,
                        trustedSenderToken = trustedToken,
                    ),
                )
                if (accepted && trustedToken != null) {
                    cachedTrustedSenderToken = trustedToken
                    viewModelScope.launch {
                        identityStore.addTrustedSenderName(pendingSenderName.ifBlank { "Retroid" })
                        identityStore.saveTrustedSenderToken(trustedToken)
                    }
                }
                _uiState.value =
                    _uiState.value.copy(
                        pairingState = resultState,
                        pairingCode = if (accepted) nextPairingCode() else _uiState.value.pairingCode,
                        trustedSenderCount = if (accepted) _uiState.value.trustedSenderCount + 1 else _uiState.value.trustedSenderCount,
                        senderName = if (accepted) pendingSenderName else _uiState.value.senderName,
                        streamActive = false,
                        status = if (accepted) "Screen ready" else "Pairing code did not match",
                    )
            }

            is PairingPacket.PairingChallenge,
            is PairingPacket.PairingResult,
            -> Unit
        }
    }

    private fun send(socket: DatagramSocket, address: InetAddress, port: Int, packet: PairingPacket) {
        val bytes = json.encodeToString(packet).toByteArray()
        socket.send(DatagramPacket(bytes, bytes.size, address, port))
    }

    private fun nextPairingCode(): String = secureRandom.nextInt(10_000).toString().padStart(4, '0')

    fun regeneratePairingCode() {
        _uiState.value =
            _uiState.value.copy(
                pairingCode = nextPairingCode(),
                pairingState = PairingState.Required,
                status = "Waiting for Retroid",
            )
    }

    fun clearPairedDevices() {
        viewModelScope.launch {
            identityStore.clearTrustedSenders()
            cachedTrustedSenderToken = null
            receiverSessionController.clearStreamSenderBinding()
            _uiState.value =
                _uiState.value.copy(
                    trustedSenderCount = 0,
                    pairingState = PairingState.Required,
                    status = "Paired devices cleared",
                )
        }
    }

    private fun isPairingRateLimited(hostKey: String): Boolean {
        if (hostKey.isBlank()) return false
        val tracker = pairingFailuresByHost[hostKey] ?: return false
        val now = System.currentTimeMillis()
        if (now - tracker.windowStartMillis > 300_000L) {
            pairingFailuresByHost.remove(hostKey)
            return false
        }
        return tracker.count >= 5
    }

    private fun recordPairingFailure(hostKey: String) {
        if (hostKey.isBlank()) return
        val now = System.currentTimeMillis()
        val tracker =
            pairingFailuresByHost.getOrPut(hostKey) {
                PairingFailureTracker(windowStartMillis = now)
            }
        if (now - tracker.windowStartMillis > 300_000L) {
            tracker.count = 0
            tracker.windowStartMillis = now
        }
        tracker.count += 1
    }

    fun updateStartFullscreen(enabled: Boolean) = saveReceiverSettings(_uiState.value.receiverSettings.copy(startFullscreen = enabled))

    fun updateKeepScreenAwake(enabled: Boolean) = saveReceiverSettings(_uiState.value.receiverSettings.copy(keepScreenAwake = enabled))

    fun updatePreferLandscapeWhilePlaying(enabled: Boolean) = saveReceiverSettings(_uiState.value.receiverSettings.copy(preferLandscapeWhilePlaying = enabled))

    fun updateScaleMode(mode: ReceiverScaleMode) = saveReceiverSettings(_uiState.value.receiverSettings.copy(scaleMode = mode))

    fun updateReceiverName(name: String) = saveReceiverSettings(_uiState.value.receiverSettings.copy(receiverName = name), refreshAdvertising = true)

    fun resetReceiverName() = saveReceiverSettings(_uiState.value.receiverSettings.copy(receiverName = ""), refreshAdvertising = true)

    private fun saveReceiverSettings(settings: ReceiverSettings, refreshAdvertising: Boolean = false) {
        val sanitized = settings.copy(receiverName = ReceiverSettingsPersistence.sanitizeReceiverName(settings.receiverName))
        val defaultName = _uiState.value.defaultReceiverName.ifBlank {
            defaultFriendlyReceiverName(
                _uiState.value.formFactor,
                Build.MODEL?.takeIf { it.isNotBlank() } ?: _uiState.value.formFactor.fallbackDeviceName(),
            )
        }
        _uiState.value = _uiState.value.copy(receiverSettings = settings)
        _uiState.value =
            _uiState.value.copy(
                receiverSettings = sanitized,
                advertisedName = ReceiverSettingsPersistence.resolvedReceiverName(sanitized.receiverName, defaultName),
            )
        viewModelScope.launch {
            identityStore.saveReceiverSettings(sanitized)
            if (refreshAdvertising && isAppForeground) {
                restartAdvertising()
            }
        }
    }

    private fun restartAdvertising() {
        scheduleAdvertisingRestart(resetAttempts = true)
    }

    private suspend fun localIpAddress(): String = withContext(Dispatchers.IO) {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
            ?.hostAddress
            ?: "Unavailable"
    }

    fun supportReportText(): String = formatSupportReport(
        metadata =
        NoctSupportReportMetadata(
            role = "Receiver",
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            debugLogsEnabled = BuildConfig.NOCT_DEBUG_LOGS,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT,
        ),
        diagnosticsSection = formatReceiverDiagnostics(_uiState.value),
        recentLogs = NoctLog.recentEntries(),
    )

    fun copySupportReportToClipboard() {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NoctDock support report", supportReportText()))
        _uiState.value = _uiState.value.copy(supportReportCopied = true)
    }

    override fun onCleared() {
        stopReceiverRuntime()
        super.onCleared()
    }
}

class ReceiverIdentityStore(private val context: Context) {
    private val idKey = stringPreferencesKey("receiver_identity_id")
    private val publicKeyKey = stringPreferencesKey("receiver_identity_public_key")
    private val trustedSenderNamesKey = stringSetPreferencesKey("trusted_sender_names")
    private val trustedSenderTokenKey = stringPreferencesKey("trusted_sender_token")
    private val startFullscreenKey = booleanPreferencesKey("receiver_start_fullscreen")
    private val keepScreenAwakeKey = booleanPreferencesKey("receiver_keep_screen_awake")
    private val preferLandscapeKey = booleanPreferencesKey("receiver_prefer_landscape")
    private val scaleModeKey = stringPreferencesKey("receiver_scale_mode")
    private val receiverNameKey = stringPreferencesKey("receiver_name")

    suspend fun identity(): ReceiverIdentity {
        val existing =
            context.receiverDataStore.data
                .map { preferences ->
                    val id = preferences[idKey]
                    val publicKey = preferences[publicKeyKey]
                    if (id != null && publicKey != null) ReceiverIdentity(id, publicKey) else null
                }
                .first()
        if (existing != null) return existing

        val created = ReceiverIdentity(id = UUID.randomUUID().toString(), publicKey = UUID.randomUUID().toString())
        context.receiverDataStore.edit { preferences ->
            preferences[idKey] = created.id
            preferences[publicKeyKey] = created.publicKey
        }
        return created
    }

    suspend fun trustedSenderNames(): Set<String> = context.receiverDataStore.data.map { it[trustedSenderNamesKey] ?: emptySet() }.first()

    suspend fun addTrustedSenderName(name: String) {
        context.receiverDataStore.edit { preferences ->
            preferences[trustedSenderNamesKey] = (preferences[trustedSenderNamesKey] ?: emptySet()) + name
        }
    }

    suspend fun trustedSenderToken(): String? = context.receiverDataStore.data.map { it[trustedSenderTokenKey] }.first()

    suspend fun saveTrustedSenderToken(token: String) {
        context.receiverDataStore.edit { preferences ->
            preferences[trustedSenderTokenKey] = token
        }
    }

    suspend fun clearTrustedSenders() {
        context.receiverDataStore.edit { preferences ->
            preferences[trustedSenderNamesKey] = emptySet<String>()
            preferences.remove(trustedSenderTokenKey)
        }
    }

    suspend fun receiverSettings(): ReceiverSettings = context.receiverDataStore.data
        .map { preferences ->
            ReceiverSettingsPersistence.fromStoredValues(
                startFullscreen = preferences[startFullscreenKey],
                keepScreenAwake = preferences[keepScreenAwakeKey],
                preferLandscapeWhilePlaying = preferences[preferLandscapeKey],
                scaleModeName = preferences[scaleModeKey],
                receiverName = preferences[receiverNameKey],
            )
        }
        .first()

    suspend fun saveReceiverSettings(settings: ReceiverSettings) {
        val stored = ReceiverSettingsPersistence.toStoredValues(settings)
        context.receiverDataStore.edit { preferences ->
            preferences[startFullscreenKey] = stored.startFullscreen
            preferences[keepScreenAwakeKey] = stored.keepScreenAwake
            preferences[preferLandscapeKey] = stored.preferLandscapeWhilePlaying
            preferences[scaleModeKey] = stored.scaleModeName
            preferences[receiverNameKey] = stored.receiverName
        }
    }
}
