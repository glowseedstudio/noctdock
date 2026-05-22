package com.glowseed.noctdock.sender

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glowseed.noctdock.core.AccentTheme
import com.glowseed.noctdock.core.AppearanceSettings
import com.glowseed.noctdock.core.BackgroundAmbiencePolicy
import com.glowseed.noctdock.core.BackgroundMotionMode
import com.glowseed.noctdock.core.CodecCapability
import com.glowseed.noctdock.core.ConnectionRecommendation
import com.glowseed.noctdock.core.ConnectionTestEvaluator
import com.glowseed.noctdock.core.ConnectionTestPacket
import com.glowseed.noctdock.core.ConnectionTestResult
import com.glowseed.noctdock.core.ConnectionTestStageSummary
import com.glowseed.noctdock.core.ConsoleModeState
import com.glowseed.noctdock.core.DeviceBuildInfo
import com.glowseed.noctdock.core.DeviceCapabilityCache
import com.glowseed.noctdock.core.DeviceCapabilityDetector
import com.glowseed.noctdock.core.DeviceCapabilityProfile
import com.glowseed.noctdock.core.DeviceCapabilityProfiles
import com.glowseed.noctdock.core.DiagnosticsSnapshot
import com.glowseed.noctdock.core.DiscoveredReceiver
import com.glowseed.noctdock.core.DiscoveryLifecycleReducer
import com.glowseed.noctdock.core.DiscoveryLifecycleState
import com.glowseed.noctdock.core.DiscoverySorter
import com.glowseed.noctdock.core.DiscoveryState
import com.glowseed.noctdock.core.DisplayRefreshRateHelper
import com.glowseed.noctdock.core.EncoderCapabilitySummary
import com.glowseed.noctdock.core.LatencyPriority
import com.glowseed.noctdock.core.LocalLibraryApp
import com.glowseed.noctdock.core.ManualConnectionValidator
import com.glowseed.noctdock.core.NebulaTheme
import com.glowseed.noctdock.core.NoctConstants
import com.glowseed.noctdock.core.NoctDockAzaharContract
import com.glowseed.noctdock.core.NoctDockAzaharLaunchDetails
import com.glowseed.noctdock.core.NoctDockAzaharPreflightResult
import com.glowseed.noctdock.core.NoctError
import com.glowseed.noctdock.core.NoctLog
import com.glowseed.noctdock.core.NoctSupportReportMetadata
import com.glowseed.noctdock.core.NsdRestartBackoff
import com.glowseed.noctdock.core.NsdServiceInfoMapper
import com.glowseed.noctdock.core.PacketCodec
import com.glowseed.noctdock.core.PacketType
import com.glowseed.noctdock.core.PairingPacket
import com.glowseed.noctdock.core.PairingState
import com.glowseed.noctdock.core.PerformanceSettings
import com.glowseed.noctdock.core.ProtocolVersion
import com.glowseed.noctdock.core.ReceiverCapabilitiesPacket
import com.glowseed.noctdock.core.ReceiverIdentity
import com.glowseed.noctdock.core.ReceiverVideoCapabilities
import com.glowseed.noctdock.core.RefreshRateHelperStatus
import com.glowseed.noctdock.core.ScreenCloakMode
import com.glowseed.noctdock.core.ScreenCloakStatus
import com.glowseed.noctdock.core.SenderVideoCapabilities
import com.glowseed.noctdock.core.Smooth60HzMode
import com.glowseed.noctdock.core.SoundMode
import com.glowseed.noctdock.core.StreamMetrics
import com.glowseed.noctdock.core.StreamNegotiator
import com.glowseed.noctdock.core.StreamProfile
import com.glowseed.noctdock.core.StreamProfiles
import com.glowseed.noctdock.core.StreamQualityConfig
import com.glowseed.noctdock.core.StreamSessionState
import com.glowseed.noctdock.core.TrustedReceiver
import com.glowseed.noctdock.core.UiDensity
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private val Context.senderDataStore by preferencesDataStore(name = "noctdock_sender")

data class SenderUiState(
    val discoveryState: DiscoveryState = DiscoveryState.Idle,
    val receivers: List<DiscoveredReceiver> = emptyList(),
    val trustedReceiver: TrustedReceiver? = null,
    val selectedReceiver: DiscoveredReceiver? = null,
    val pairingState: PairingState = PairingState.NotRequired,
    val pairingTarget: DiscoveredReceiver? = null,
    val pairingMessage: String = "",
    val manualHost: String = "",
    val manualPort: String = NoctConstants.DEFAULT_DISCOVERY_PORT.toString(),
    val manualExpanded: Boolean = false,
    val consoleModeState: ConsoleModeState = ConsoleModeState.Searching,
    val streamState: StreamSessionState = StreamSessionState.Idle,
    val metrics: StreamMetrics = StreamMetrics(),
    val lastError: NoctError? = null,
    val encoderName: String = "Not selected",
    val performanceSettings: PerformanceSettings = PerformanceSettings(),
    val deviceProfile: DeviceCapabilityProfile = DeviceCapabilityProfiles.UnknownAndroidHandheld,
    val encoderCapabilitySummary: EncoderCapabilitySummary = EncoderCapabilitySummary(),
    val appearanceSettings: AppearanceSettings = AppearanceSettings(),
    val libraryApps: List<LibraryAppItem> = emptyList(),
    val libraryAddCandidates: List<LibraryAppItem> = emptyList(),
    val libraryQuery: String = "",
    val pendingLaunchApp: LocalLibraryApp? = null,
    val azaharStatus: AzaharIntegrationStatus = AzaharIntegrationStatus(),
    val azaharLaunchDiagnostics: AzaharLaunchDiagnostics = AzaharLaunchDiagnostics(),
    val diagnosticsCopied: Boolean = false,
    val connectionTestRunning: Boolean = false,
    val connectionTestResult: ConnectionTestResult? = null,
    val foregroundPackage: String? = null,
    val status: String = "Looking for a screen...",
    val screenCloakStatus: ScreenCloakStatus = ScreenCloakStatus(),
    val discoveryLifecycleState: DiscoveryLifecycleState = DiscoveryLifecycleState.WAITING,
    val discoveryStateLabel: String = "Idle",
    val discoveryRefreshing: Boolean = false,
    val refreshRateHelperStatus: RefreshRateHelperStatus = RefreshRateHelperStatus(),
) {
    val defaultReceiver: DiscoveredReceiver? = selectedReceiver ?: receivers.firstOrNull()
}

data class LibraryAppItem(val model: LocalLibraryApp, val icon: Drawable)

data class AzaharIntegrationStatus(val installed: Boolean = false, val label: String = "NoctDock Azahar", val message: String = "NoctDock Azahar is not installed.")

data class AzaharLaunchDiagnostics(
    val packageInstalled: Boolean = false,
    val receiverAddressPassed: String = "Not sent",
    val selectedCodec: String = "Not selected",
    val soundMode: String = "Not selected",
    val launchIntentSent: Boolean = false,
)

private data class RuntimeDeviceCapability(val profile: DeviceCapabilityProfile, val encoderSummary: EncoderCapabilitySummary)

class SenderViewModel(private val app: Application) : AndroidViewModel(app) {
    private val json = Json { ignoreUnknownKeys = true }
    private val trustStore = SenderTrustStore(app, json)
    private val libraryStore = SenderLibraryStore(app, json)
    private val settingsStore = SenderSettingsStore(app, json)
    private val nsdManager = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val receivers = linkedMapOf<String, DiscoveredReceiver>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var pairingJob: Job? = null
    private var discoveryRefreshJob: Job? = null
    private var isAppForeground: Boolean = true

    private val _uiState = MutableStateFlow(SenderUiState())
    val uiState: StateFlow<SenderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val trusted = trustStore.trustedReceiver()
            val capability = loadDeviceCapability()
            val savedSettings = settingsStore.settingsOrNull()
            val settings = savedSettings ?: DeviceCapabilityDetector.recommendedSettings(capability.profile)
            if (savedSettings == null) settingsStore.save(settings)
            val appearance = settingsStore.appearance()
            val savedConnectionTest = settingsStore.connectionTestResult()
            _uiState.value =
                _uiState.value.copy(
                    trustedReceiver = trusted,
                    pairingState = if (trusted != null) PairingState.Trusted else PairingState.NotRequired,
                    performanceSettings = settings,
                    deviceProfile = capability.profile,
                    encoderCapabilitySummary = capability.encoderSummary,
                    appearanceSettings = appearance,
                    connectionTestResult = savedConnectionTest,
                )
            refreshLibrary()
            if (isAppForeground) startDiscovery()
        }
        viewModelScope.launch {
            StreamSessionController.state.collect { stream ->
                _uiState.value =
                    _uiState.value.copy(
                        streamState = stream.state,
                        consoleModeState =
                        when (stream.state) {
                            StreamSessionState.Active -> ConsoleModeState.Streaming

                            StreamSessionState.Failed -> ConsoleModeState.Error

                            StreamSessionState.Stopped -> ConsoleModeState.Idle

                            StreamSessionState.StartingService,
                            StreamSessionState.CheckingEncoder,
                            StreamSessionState.ConnectingReceiver,
                            -> ConsoleModeState.Starting

                            else -> _uiState.value.consoleModeState
                        },
                        metrics = stream.metrics,
                        encoderName = stream.encoderName,
                        status =
                        when (stream.state) {
                            StreamSessionState.Active -> stream.error ?: "${stream.receiverName} ready"
                            StreamSessionState.Stopped -> stream.error ?: "Console Mode stopped"
                            StreamSessionState.Failed -> stream.error ?: "Console Mode stopped unexpectedly"
                            else -> _uiState.value.status
                        },
                        screenCloakStatus = stream.screenCloakStatus,
                    )
                publishDiscoveryLifecycle()
                if (stream.state == StreamSessionState.Active) launchPendingAfterConsoleStart()
                if (
                    stream.state == StreamSessionState.Stopped ||
                    stream.state == StreamSessionState.Failed
                ) {
                    refreshDiscovery()
                }
            }
        }
    }

    private suspend fun loadDeviceCapability(): RuntimeDeviceCapability {
        val buildInfo = currentBuildInfo()
        val appVersion = BuildConfig.VERSION_NAME
        val cacheFingerprint =
            buildInfo.fingerprint.ifBlank {
                listOf(buildInfo.manufacturer, buildInfo.model, buildInfo.device, buildInfo.product)
                    .joinToString("|")
            }
        settingsStore.deviceCapabilityCache(appVersion, cacheFingerprint)?.let { cache ->
            return RuntimeDeviceCapability(cache.profile, cache.encoderSummary)
        }

        val encoderSummary = probeEncoderCapabilities()
        val profile = DeviceCapabilityDetector.detect(buildInfo, encoderSummary)
        settingsStore.saveDeviceCapabilityCache(
            DeviceCapabilityCache(
                appVersion = appVersion,
                deviceFingerprint = cacheFingerprint,
                profile = profile,
                encoderSummary = encoderSummary,
            ),
        )
        return RuntimeDeviceCapability(profile, encoderSummary)
    }

    private fun currentBuildInfo(): DeviceBuildInfo = DeviceBuildInfo(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        product = Build.PRODUCT.orEmpty(),
        hardware = Build.HARDWARE.orEmpty(),
        socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else "",
        socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else "",
        fingerprint = Build.FINGERPRINT.orEmpty(),
    )

    private suspend fun probeEncoderCapabilities(): EncoderCapabilitySummary = withContext(Dispatchers.Default) {
        val candidates =
            runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                    .filter { info ->
                        info.isEncoder &&
                            info.supportedTypes.any { type ->
                                type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) ||
                                    type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)
                            }
                    }
                    .mapNotNull { info -> encoderSummaryFor(info) }
            }.getOrDefault(emptyList())

        candidates
            .sortedWith(
                compareByDescending<EncoderCapabilitySummary> { it.hardwareAccelerated }
                    .thenByDescending { it.supports1080p60 }
                    .thenByDescending { it.maxBitrateMbps },
            )
            .firstOrNull()
            ?: EncoderCapabilitySummary()
    }

    private fun encoderSummaryFor(info: MediaCodecInfo): EncoderCapabilitySummary? = runCatching {
        val supportsAvc = info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
        val supportsHevc = info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) }
        val caps = info.getCapabilitiesForType(if (supportsAvc) MediaFormat.MIMETYPE_VIDEO_AVC else MediaFormat.MIMETYPE_VIDEO_HEVC)
        val videoCaps = caps.videoCapabilities ?: return null
        val hardwareAccelerated = info.isHardwareAccelerated
        val supports1080p60 =
            runCatching { videoCaps.areSizeAndRateSupported(1920, 1080, 60.0) }.getOrDefault(false)
        val maxWidth = runCatching { videoCaps.supportedWidths.upper }.getOrDefault(1280)
        val maxHeight = runCatching { videoCaps.supportedHeights.upper }.getOrDefault(720)
        val maxBitrateMbps = runCatching { videoCaps.bitrateRange.upper / 1_000_000 }.getOrDefault(8)
        val supportsLowLatency =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency) }
                    .getOrDefault(false)
            } else {
                false
            }
        EncoderCapabilitySummary(
            encoderName = info.name,
            hardwareAccelerated = hardwareAccelerated,
            supportsAvc = supportsAvc,
            supportsHevc = supportsHevc,
            hevcEncoderName = if (supportsHevc) info.name else "Unknown",
            supports1080p60 = supports1080p60,
            supportsLowLatency = supportsLowLatency,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxFps = if (supports1080p60) 60 else 30,
            maxBitrateMbps = maxBitrateMbps,
        )
    }.getOrNull()

    fun refreshDiscovery() {
        if (!isAppForeground) return
        if (_uiState.value.streamState == StreamSessionState.Active) return
        discoveryRefreshJob?.cancel()
        discoveryRefreshJob =
            viewModelScope.launch {
                publishDiscoveryLifecycle(refreshing = true)
                stopDiscovery()
                delay(NsdRestartBackoff.delayForAttempt(0))
                if (_uiState.value.streamState != StreamSessionState.Active && isAppForeground) {
                    startDiscovery()
                }
                publishDiscoveryLifecycle(refreshing = false)
            }
    }

    fun startDiscovery() {
        if (!isAppForeground) return
        if (discoveryListener != null) return
        stopDiscovery()
        receivers.clear()
        publishReceivers(DiscoveryState.Scanning)
        multicastLock =
            wifiManager.createMulticastLock("NoctDockDiscovery").apply {
                setReferenceCounted(false)
                acquire()
            }

        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    publishReceivers(DiscoveryState.Scanning)
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType != NoctConstants.NSD_SERVICE_TYPE) return
                    resolveDiscoveredService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                _uiState.value = _uiState.value.copy(status = "Screen faded out. Looking again.")
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val receiver =
                                    runCatching { NsdServiceInfoMapper.toDiscoveredReceiver(info, System.currentTimeMillis()) }
                                        .getOrNull()
                                        ?.takeIf { it.hostAddress.isNotBlank() && it.port > 0 }
                                        ?: return
                                receivers[receiver.identity.id] = receiver
                                publishReceivers(DiscoveryState.ReceiverFound)
                            }
                        },
                    )
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val matching = receivers.entries.firstOrNull { it.value.serviceName == serviceInfo.serviceName } ?: return
                    receivers[matching.key] = matching.value.copy(isOnline = false)
                    publishReceivers(DiscoveryState.ReceiverLost)
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    _uiState.value = _uiState.value.copy(discoveryState = DiscoveryState.Idle)
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    stopDiscovery()
                    _uiState.value =
                        _uiState.value.copy(
                            discoveryState = DiscoveryState.Failed,
                            status = "Discovery failed: $errorCode",
                        )
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    _uiState.value = _uiState.value.copy(status = "Discovery stop failed: $errorCode")
                }
            }
        discoveryListener = listener
        nsdManager.discoverServices(NoctConstants.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        if (foreground) {
            if (_uiState.value.streamState != StreamSessionState.Active) {
                refreshDiscovery()
            }
        } else {
            stopDiscovery()
            publishDiscoveryLifecycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveDiscoveredService(serviceInfo: NsdServiceInfo, listener: NsdManager.ResolveListener) {
        nsdManager.resolveService(serviceInfo, listener)
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener -> runCatching { nsdManager.stopServiceDiscovery(listener) } }
        discoveryListener = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    fun select(receiver: DiscoveredReceiver) {
        _uiState.value = _uiState.value.copy(selectedReceiver = receiver)
    }

    fun connectDefault() {
        val receiver = _uiState.value.defaultReceiver
        if (receiver == null) {
            _uiState.value = _uiState.value.copy(status = "Looking for a screen...")
            return
        }
        connect(receiver)
    }

    fun connect(receiver: DiscoveredReceiver) {
        select(receiver)
        pairingJob?.cancel()
        pairingJob =
            viewModelScope.launch {
                _uiState.value =
                    _uiState.value.copy(
                        pairingState = if (receiver.pairingRequired) PairingState.Required else PairingState.NotRequired,
                        pairingTarget = receiver,
                        status = "Waking ${receiver.displayName}",
                    )
                val trusted = _uiState.value.trustedReceiver?.takeIf { it.identity.id == receiver.identity.id }
                val result =
                    withContext(Dispatchers.IO) {
                        sendAndReceive(
                            receiver.hostAddress,
                            receiver.port,
                            PairingPacket.PairingRequest(
                                receiverIdentityId = receiver.identity.id,
                                senderName = Build.MODEL ?: "Android handheld",
                                trustedSenderToken = trusted?.trustedSenderToken,
                            ),
                        )
                    }
                when (result) {
                    is PairingPacket.PairingResult ->
                        if (result.accepted) {
                            trust(receiver, "${receiver.displayName} ready", result.trustedSenderToken)
                        } else {
                            failPairing(result.reason)
                        }

                    is PairingPacket.PairingChallenge ->
                        _uiState.value =
                            _uiState.value.copy(
                                pairingState = PairingState.AwaitingCode,
                                pairingMessage = "Enter the 4-digit code shown on ${receiver.displayName}.",
                            )

                    else -> failPairing("NoctDock Receiver did not answer.")
                }
            }
    }

    fun startConsoleMode(permissionGranted: Boolean) {
        val receiver = _uiState.value.defaultReceiver
        val trusted = _uiState.value.trustedReceiver
        when {
            receiver == null -> {
                setError(NoctError.NoReceiverFound)
                startDiscovery()
            }

            !receiver.isOnline -> setError(NoctError.ReceiverOffline)

            trusted?.identity?.id != receiver.identity.id -> connect(receiver)

            !permissionGranted -> {
                _uiState.value =
                    _uiState.value.copy(
                        consoleModeState = ConsoleModeState.Starting,
                        streamState = StreamSessionState.PermissionRequired,
                        status = "NoctDock is ready to mirror your handheld.",
                    )
            }

            else -> {
                _uiState.value =
                    _uiState.value.copy(
                        consoleModeState = ConsoleModeState.Starting,
                        streamState = StreamSessionState.PermissionRequired,
                        status = "NoctDock is ready to mirror your handheld.",
                    )
            }
        }
    }

    fun startConsoleMode(resultCode: Int, resultData: Intent) {
        val receiver = _uiState.value.defaultReceiver
        val trusted = _uiState.value.trustedReceiver
        when {
            receiver == null -> {
                setError(NoctError.NoReceiverFound)
                startDiscovery()
            }

            !receiver.isOnline -> setError(NoctError.ReceiverOffline)

            trusted?.identity?.id != receiver.identity.id -> connect(receiver)

            else -> beginLocalStreamSession(receiver, resultCode, resultData)
        }
    }

    fun stopConsoleMode() {
        app.stopService(Intent(app, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        _uiState.value =
            _uiState.value.copy(
                consoleModeState = ConsoleModeState.Idle,
                streamState = StreamSessionState.Stopped,
                metrics = StreamMetrics(),
                status = "Console Mode stopped",
                refreshRateHelperStatus = RefreshRateHelperStatus(),
            )
        refreshDiscovery()
    }

    fun applySmooth60HzIfNeeded(activity: android.app.Activity): RefreshRateHelperStatus {
        val mode = _uiState.value.performanceSettings.smooth60HzMode
        if (!DisplayRefreshRateHelper.shouldRequestOnConsoleStart(mode)) {
            return RefreshRateHelperStatus()
        }
        val status = DisplayRefreshRateHelper.applyToActivity(activity, mode)
        _uiState.value = _uiState.value.copy(refreshRateHelperStatus = status)
        return status
    }

    fun clearSmooth60Hz(activity: android.app.Activity) {
        DisplayRefreshRateHelper.clearWindow(activity.window)
        _uiState.value = _uiState.value.copy(refreshRateHelperStatus = RefreshRateHelperStatus())
    }

    fun updateSmooth60HzMode(mode: Smooth60HzMode) = saveSettings(_uiState.value.performanceSettings.copy(smooth60HzMode = mode))

    private fun beginLocalStreamSession(receiver: DiscoveredReceiver, resultCode: Int, resultData: Intent) {
        val senderCapabilities = senderVideoCapabilities()
        if (!senderCapabilities.supportsAvcEncode) {
            setError(NoctError.EncoderUnavailable)
            return
        }
        val profile = streamProfileForApp(_uiState.value.pendingLaunchApp)
        val negotiated = StreamNegotiator.negotiate(profile, senderCapabilities, receiver.videoCapabilities)
        val quality = streamQualityConfig()
        val settings = _uiState.value.performanceSettings
        val bitrate = (settings.manualBitrateMbps ?: negotiated.bitrateMbps) * 1_000_000
        val queueSize =
            when (profile.latencyPriority) {
                LatencyPriority.Lowest -> 1
                LatencyPriority.Balanced -> 1
                LatencyPriority.Quality -> settings.maxQueueSize
            }
        val serviceIntent =
            Intent(app, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RECEIVER_NAME, receiver.displayName)
                putExtra(ScreenCaptureService.EXTRA_HOST, receiver.hostAddress)
                putExtra(ScreenCaptureService.EXTRA_PORT, receiver.port)
                putExtra(ScreenCaptureService.EXTRA_WIDTH, negotiated.width)
                putExtra(ScreenCaptureService.EXTRA_HEIGHT, negotiated.height)
                putExtra(ScreenCaptureService.EXTRA_FPS, negotiated.fps)
                putExtra(ScreenCaptureService.EXTRA_BITRATE, bitrate)
                putExtra(ScreenCaptureService.EXTRA_CODEC_MIME, negotiated.codec.mime)
                putExtra(ScreenCaptureService.EXTRA_FALLBACK_AVC_BITRATE, profile.bitrateFor(VideoCodec.AVC) * 1_000_000)
                putExtra(ScreenCaptureService.EXTRA_ADAPTIVE_FLOOR_MBPS, negotiated.adaptiveFloorMbps)
                putExtra(ScreenCaptureService.EXTRA_ADAPTIVE_CEILING_MBPS, negotiated.adaptiveCeilingMbps)
                putExtra(ScreenCaptureService.EXTRA_FALLBACK_AVC_FLOOR_MBPS, profile.adaptiveFloorFor(VideoCodec.AVC))
                putExtra(ScreenCaptureService.EXTRA_FALLBACK_AVC_CEILING_MBPS, profile.adaptiveCeilingFor(VideoCodec.AVC))
                putExtra(ScreenCaptureService.EXTRA_KEYFRAME_INTERVAL, settings.keyframeIntervalSeconds)
                putExtra(ScreenCaptureService.EXTRA_LOW_LATENCY, settings.preferLowLatencyCodec)
                putExtra(ScreenCaptureService.EXTRA_LATENCY_PRIORITY, profile.latencyPriority.name)
                putExtra(ScreenCaptureService.EXTRA_MAX_QUEUE_SIZE, queueSize)
                putExtra(ScreenCaptureService.EXTRA_DROP_OLDEST_FRAMES, settings.allowFrameDropping)
                putExtra(ScreenCaptureService.EXTRA_ADAPTIVE_BITRATE, quality.adaptiveBitrateEnabled)
                putExtra(ScreenCaptureService.EXTRA_BATTERY_SAVER, quality.batterySaverMode)
                putExtra(ScreenCaptureService.EXTRA_PACKET_PACING, quality.packetPacingEnabled)
                putExtra(ScreenCaptureService.EXTRA_THERMAL_PROTECTION, quality.thermalProtectionEnabled)
                putExtra(ScreenCaptureService.EXTRA_SOUND_MODE, settings.soundMode.name)
                putExtra(ScreenCaptureService.EXTRA_LOWER_HANDHELD_SOUND, settings.lowerHandheldInTvSound)
                putExtra(ScreenCaptureService.EXTRA_SCREEN_CLOAK_MODE, _uiState.value.appearanceSettings.screenCloakMode.name)
                putExtra(
                    ScreenCaptureService.EXTRA_SCREEN_CLOAK_OVERLAY_DISABLED,
                    _uiState.value.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue,
                )
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, resultData)
                putExtra(ScreenCaptureService.EXTRA_PROFILE_TITLE, profile.title)
            }
        ContextCompat.startForegroundService(app, serviceIntent)
        _uiState.value =
            _uiState.value.copy(
                consoleModeState = ConsoleModeState.Starting,
                streamState = StreamSessionState.StartingService,
                encoderName = if (negotiated.codec ==
                    VideoCodec.HEVC
                ) {
                    senderCapabilities.hevcEncoderName
                } else {
                    senderCapabilities.avcEncoderName
                },
                lastError = null,
                status = negotiated.warning ?: "Starting Console Mode on ${receiver.displayName}…",
            )
    }

    private fun senderVideoCapabilities(): SenderVideoCapabilities {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
        fun encoderFor(mime: String): MediaCodecInfo? {
            val candidates = codecs.filter { info -> info.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) } }
            return candidates.firstOrNull { it.isHardwareAccelerated }
                ?: candidates.firstOrNull { !it.name.contains("google", ignoreCase = true) }
                ?: candidates.firstOrNull()
        }
        val avc = encoderFor(MediaFormat.MIMETYPE_VIDEO_AVC)
        val hevc = encoderFor(MediaFormat.MIMETYPE_VIDEO_HEVC)
        val maxWidth = if (avc != null || hevc != null) 1920 else 1280
        val maxHeight = if (avc != null || hevc != null) 1080 else 720
        return SenderVideoCapabilities(
            supportsAvcEncode = avc != null,
            supportsHevcEncode = hevc != null,
            avcEncoderName = avc?.name ?: "Unavailable",
            hevcEncoderName = hevc?.name ?: "Unavailable",
            maxEncodeWidth = maxWidth,
            maxEncodeHeight = maxHeight,
            maxBitrateMbps = 55,
            lowLatencyEncodeSupported = true,
        )
    }

    fun submitPairingCode(code: String) {
        val receiver = _uiState.value.pairingTarget ?: return
        pairingJob?.cancel()
        pairingJob =
            viewModelScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        sendAndReceive(
                            receiver.hostAddress,
                            receiver.port,
                            PairingPacket.PairingCode(receiver.identity.id, code.filter(Char::isDigit).take(4)),
                        )
                    }
                if (result is PairingPacket.PairingResult && result.accepted) {
                    trust(receiver, "${receiver.displayName} paired", result.trustedSenderToken)
                } else {
                    failPairing("Pairing code did not match.")
                }
            }
    }

    fun updateManualHost(value: String) {
        _uiState.value = _uiState.value.copy(manualHost = value)
    }

    fun updateManualPort(value: String) {
        _uiState.value = _uiState.value.copy(manualPort = value.filter(Char::isDigit).take(5))
    }

    fun setManualExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(manualExpanded = expanded)
    }

    fun connectManual() {
        val state = _uiState.value
        val host = state.manualHost.trim()
        val port = state.manualPort.trim()
        if (!ManualConnectionValidator.isValidHost(host) || !ManualConnectionValidator.isValidPort(port)) {
            _uiState.value = state.copy(status = "Manual connection needs a valid local host and port.")
            return
        }
        pairingJob =
            viewModelScope.launch {
                val response =
                    withContext(Dispatchers.IO) {
                        sendAndReceive(
                            host,
                            port.toInt(),
                            PairingPacket.PairingRequest("*", Build.MODEL ?: "Android handheld"),
                        )
                    }
                val challenge = response as? PairingPacket.PairingChallenge
                if (challenge == null) {
                    failPairing("Manual receiver did not answer.")
                    return@launch
                }
                val capabilities = probeReceiverVideoCapabilities(host, port.toInt())
                val supportedCodecs =
                    buildList {
                        if (capabilities.supportsAvc) add(CodecCapability.H264)
                        if (capabilities.supportsHevc) add(CodecCapability.H265)
                    }.ifEmpty { listOf(CodecCapability.H264) }
                val maxResolution = "${capabilities.maxWidth}x${capabilities.maxHeight}"
                val manualReceiver =
                    DiscoveredReceiver(
                        identity = ReceiverIdentity(challenge.receiverIdentityId, challenge.receiverIdentityKey),
                        deviceName = "Manual NoctDock Receiver",
                        serviceName = "Manual NoctDock Receiver",
                        hostAddress = host,
                        port = port.toInt(),
                        protocolVersion = ProtocolVersion.Current,
                        receiverAppVersion = "unknown",
                        supportedCodecs = supportedCodecs,
                        supportedMaxResolution = maxResolution,
                        videoCapabilities = capabilities,
                        pairingRequired = true,
                    )
                _uiState.value =
                    _uiState.value.copy(
                        selectedReceiver = manualReceiver,
                        pairingTarget = manualReceiver,
                        pairingState = PairingState.AwaitingCode,
                        pairingMessage = "Enter the 4-digit code shown on the receiver.",
                    )
            }
    }

    fun testConnection() {
        val receiver = _uiState.value.defaultReceiver ?: return
        if (_uiState.value.connectionTestRunning) return
        _uiState.value = _uiState.value.copy(connectionTestRunning = true, status = "Testing local screen connection...")
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    DatagramSocket().use { socket ->
                        socket.soTimeout = 900
                        val address = InetAddress.getByName(receiver.hostAddress)
                        val streamId = (System.nanoTime() and Int.MAX_VALUE.toLong()).toInt()
                        val testId = (System.currentTimeMillis() and Int.MAX_VALUE.toLong()).toInt()
                        val allowSharpTier =
                            receiver.videoCapabilities.maxWidth >= StreamProfiles.Sharp.width &&
                                DeviceCapabilityDetector.allowsHevc(_uiState.value.deviceProfile, _uiState.value.encoderCapabilitySummary)
                        val allowFullHdTier =
                            receiver.videoCapabilities.maxWidth >= StreamProfiles.Cinema.width &&
                                _uiState.value.encoderCapabilitySummary.supports1080p60 &&
                                _uiState.value.encoderCapabilitySummary.hardwareAccelerated
                        val stages = ConnectionTestEvaluator.rates(includeSharpTier = allowSharpTier, includeFullHdTier = allowFullHdTier)
                        val summaries = mutableListOf<ConnectionTestStageSummary>()
                        stages.forEach { stage ->
                            val intervalNs = com.glowseed.noctdock.core.PacketPacingPolicy.packetIntervalNs(
                                stage.payloadSizeBytes,
                                stage.targetMbps,
                            )
                            val packetCount = ((stage.durationMs * 1_000_000L) / intervalNs.coerceAtLeast(1L)).toInt().coerceAtLeast(8)
                            repeat(packetCount) { index ->
                                val sentAtUs = System.nanoTime() / 1_000L
                                val bytes =
                                    PacketCodec.encodeConnectionTest(
                                        ConnectionTestPacket(
                                            streamId = streamId,
                                            testId = testId,
                                            stageIndex = stage.stageIndex,
                                            sequenceNumber = index.toLong(),
                                            sentAtUs = sentAtUs,
                                            echo = false,
                                            stageComplete = index == packetCount - 1,
                                            targetMbps = stage.targetMbps,
                                            payloadSize = stage.payloadSizeBytes,
                                            expectedPackets = packetCount,
                                        ),
                                    )
                                socket.send(DatagramPacket(bytes, bytes.size, address, receiver.port))
                                if (index < packetCount - 1) {
                                    val deadline = System.nanoTime() + intervalNs
                                    while (System.nanoTime() < deadline) {
                                        Thread.onSpinWait()
                                    }
                                }
                            }
                            val receiveStartedUs = System.nanoTime() / 1_000L
                            val buffer = ByteArray(PacketCodec.MAX_DATAGRAM_SIZE)
                            val response = DatagramPacket(buffer, buffer.size)
                            socket.receive(response)
                            val echo = PacketCodec.decodeConnectionTest(PacketCodec.decode(response.data, response.length))
                            val roundTripMs = (((System.nanoTime() / 1_000L) - receiveStartedUs) / 1_000L).toInt().coerceAtLeast(0)
                            summaries +=
                                ConnectionTestStageSummary(
                                    stage = stage,
                                    expectedPackets = echo.expectedPackets,
                                    receivedPackets = echo.receivedPackets,
                                    missingPackets = echo.missingPackets,
                                    jitterMs = (echo.jitterUs / 1_000).coerceAtLeast(0),
                                    roundTripMs = roundTripMs,
                                    receiverTransport = com.glowseed.noctdock.core.ReceiverTransportKind.entries.getOrElse(
                                        echo.receiverTransport,
                                    ) {
                                        com.glowseed.noctdock.core.ReceiverTransportKind.UNKNOWN
                                    },
                                )
                        }
                        val raw = ConnectionTestEvaluator.summarize(summaries)
                        val clampedRecommendation =
                            when {
                                raw.recommendation == ConnectionRecommendation.CINEMA && !allowFullHdTier -> ConnectionRecommendation.SHARP
                                raw.recommendation == ConnectionRecommendation.SHARP && !allowSharpTier -> ConnectionRecommendation.QUALITY
                                else -> raw.recommendation
                            }
                        raw.copy(overrideRecommendation = clampedRecommendation)
                    }
                }.getOrElse {
                    ConnectionTestResult(
                        0,
                        100,
                        999,
                        999,
                        receiverResponsive = false,
                        overrideRecommendation = ConnectionRecommendation.PERFORMANCE,
                    )
                }
            settingsStore.saveConnectionTestResult(result)
            withContext(Dispatchers.Main) {
                _uiState.value =
                    _uiState.value.copy(
                        connectionTestRunning = false,
                        connectionTestResult = result,
                        status = result.friendlyLabel,
                    )
            }
        }
    }

    private suspend fun trust(receiver: DiscoveredReceiver, status: String, trustedSenderToken: String? = null) {
        val existingToken = _uiState.value.trustedReceiver?.takeIf { it.identity.id == receiver.identity.id }?.trustedSenderToken
        val trusted =
            TrustedReceiver(
                identity = receiver.identity,
                displayName = receiver.displayName,
                lastHostAddress = receiver.hostAddress,
                port = receiver.port,
                trustedAtMillis = System.currentTimeMillis(),
                trustedSenderToken = trustedSenderToken ?: existingToken,
            )
        viewModelScope.launch { trustStore.save(trusted) }
        _uiState.value =
            _uiState.value.copy(
                trustedReceiver = trusted,
                selectedReceiver = receiver,
                pairingState = PairingState.Trusted,
                pairingTarget = null,
                pairingMessage = "",
                status = status,
            )
        publishReceivers(_uiState.value.discoveryState)
    }

    private fun failPairing(reason: String) {
        _uiState.value =
            _uiState.value.copy(
                pairingState = PairingState.Failed,
                pairingMessage = reason,
                consoleModeState = ConsoleModeState.Error,
                lastError = NoctError.PairingFailed,
                status = reason,
            )
    }

    private fun publishDiscoveryLifecycle(refreshing: Boolean = _uiState.value.discoveryRefreshing) {
        val state =
            DiscoveryLifecycleReducer.senderState(
                discoveryState = _uiState.value.discoveryState,
                streamState = _uiState.value.streamState,
                pairingState = _uiState.value.pairingState,
                refreshing = refreshing,
            )
        _uiState.value =
            _uiState.value.copy(
                discoveryLifecycleState = state,
                discoveryRefreshing = refreshing,
                discoveryStateLabel = state.name.replace('_', ' '),
            )
    }

    /** Keeps [PairingState.Trusted] when a saved screen is back on the network — avoids re-prompting for a code. */
    private fun pairingStateForSelectedReceiver(trusted: TrustedReceiver?, selected: DiscoveredReceiver?, current: PairingState): PairingState {
        if (current == PairingState.AwaitingCode || current == PairingState.Failed) return current
        if (trusted != null && selected != null && trusted.identity.id == selected.identity.id) {
            return PairingState.Trusted
        }
        return current
    }

    private fun publishReceivers(discoveryState: DiscoveryState) {
        val trustedId =
            _uiState.value.trustedReceiver?.identity?.id
                ?.takeIf { _uiState.value.appearanceSettings.rememberLastReceiver || _uiState.value.appearanceSettings.autoReconnect }
        val sorted = DiscoverySorter.sort(receivers.values, trustedId)
        val selected =
            when {
                _uiState.value.appearanceSettings.autoReconnect && trustedId != null -> sorted.firstOrNull { it.identity.id == trustedId }

                _uiState.value.selectedReceiver != null -> _uiState.value.selectedReceiver?.takeIf { current ->
                    sorted.any {
                        it.identity.id ==
                            current.identity.id
                    }
                }

                _uiState.value.appearanceSettings.rememberLastReceiver && trustedId != null -> sorted.firstOrNull {
                    it.identity.id ==
                        trustedId
                }

                else -> sorted.firstOrNull()
            }
        _uiState.value =
            _uiState.value.copy(
                discoveryState = discoveryState,
                receivers = sorted,
                selectedReceiver = selected,
                pairingState =
                pairingStateForSelectedReceiver(
                    trusted = _uiState.value.trustedReceiver,
                    selected = selected,
                    current = _uiState.value.pairingState,
                ),
                consoleModeState =
                when {
                    _uiState.value.consoleModeState == ConsoleModeState.Streaming -> ConsoleModeState.Streaming
                    sorted.isEmpty() -> ConsoleModeState.Searching
                    _uiState.value.trustedReceiver?.identity?.id == sorted.firstOrNull()?.identity?.id -> ConsoleModeState.Ready
                    else -> ConsoleModeState.Pairing
                },
                status =
                if (sorted.isEmpty()) {
                    "Looking for a screen..."
                } else {
                    "${sorted.size} screen${if (sorted.size == 1) "" else "s"} nearby"
                },
            )
        publishDiscoveryLifecycle()
    }

    fun updateLibraryQuery(value: String) {
        _uiState.value = _uiState.value.copy(libraryQuery = value)
        refreshLibrary()
    }

    fun refreshLibrary(clearIconCache: Boolean = false) {
        if (clearIconCache) SenderAppIconCache.clear()
        viewModelScope.launch(Dispatchers.IO) {
            val previousIcons = _uiState.value.libraryApps.associateBy { it.model.packageName }
            val favourites = libraryStore.favourites()
            val recent = libraryStore.recent()
            val manualApps = libraryStore.manualApps()
            val profileOverrides = libraryStore.profileOverrides()
            val packageManager = app.packageManager
            val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos =
                if (Build.VERSION.SDK_INT >= 33) {
                    packageManager.queryIntentActivities(launchIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.queryIntentActivities(launchIntent, 0)
                }
            val apps =
                resolveInfos
                    .mapNotNull { info ->
                        val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                        val label = info.loadLabel(packageManager).toString().takeIf { it.isNotBlank() } ?: packageName
                        val lastLaunch = recent[packageName] ?: 0L
                        LibraryAppItem(
                            model =
                            LocalLibraryApp(
                                packageName = packageName,
                                label = label,
                                isFavourite = packageName in favourites,
                                lastLaunchedAtMillis = lastLaunch,
                                profileOverrideId = profileOverrides[packageName],
                            ),
                            icon =
                            previousIcons[packageName]?.icon?.takeIf {
                                SenderAppIconCache.canReuseDrawable(packageManager, packageName, it)
                            } ?: info.loadIcon(packageManager).also {
                                SenderAppIconCache.trackLoadedIcon(packageManager, packageName)
                            },
                        )
                    }
            val azaharPackageName = resolveInstalledAzaharPackage(apps.map { it.model.packageName })
            val azaharInstalled = azaharPackageName != null
            val query = _uiState.value.libraryQuery
            val visibleModels =
                apps
                    .map { it.model }
                    .filter { model -> model.isAutoEmulator() || model.packageName in manualApps }
            val candidateModels =
                apps
                    .map { it.model }
                    .filter { model -> !model.isAutoEmulator() && model.packageName !in manualApps }
            val byPackage = apps.associateBy { it.model.packageName }
            val sortedVisible = com.glowseed.noctdock.core.AppLibrarySorter.filter(visibleModels, query).mapNotNull {
                byPackage[it.packageName]
            }
            val sortedCandidates = com.glowseed.noctdock.core.AppLibrarySorter.filter(candidateModels, query).mapNotNull {
                byPackage[it.packageName]
            }.take(12)
            _uiState.value =
                _uiState.value.copy(
                    libraryApps = sortedVisible,
                    libraryAddCandidates = sortedCandidates,
                    azaharStatus =
                    AzaharIntegrationStatus(
                        installed = azaharInstalled,
                        message = if (azaharInstalled) "3DS Mode available" else "NoctDock Azahar is not installed.",
                    ),
                )
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            app.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(packageName, 0)
        }
        true
    }.getOrDefault(false)

    private fun resolveInstalledAzaharPackage(visiblePackages: Collection<String> = emptyList()): String? = NoctDockAzaharContract.PACKAGE_CANDIDATES.firstOrNull { packageName ->
        packageName in visiblePackages || isPackageInstalled(packageName)
    }

    fun addLibraryApp(app: LocalLibraryApp) {
        viewModelScope.launch {
            libraryStore.setManualApp(app.packageName, true)
            refreshLibrary()
        }
    }

    fun removeLibraryApp(app: LocalLibraryApp) {
        viewModelScope.launch {
            libraryStore.setManualApp(app.packageName, false)
            refreshLibrary()
        }
    }

    fun toggleFavourite(app: LocalLibraryApp) {
        viewModelScope.launch {
            libraryStore.setFavourite(app.packageName, !app.isFavourite)
            refreshLibrary()
        }
    }

    fun setAppProfileOverride(packageName: String, profileId: String?) {
        viewModelScope.launch {
            libraryStore.setAppProfileOverride(packageName, profileId)
            refreshLibrary()
        }
    }

    fun libraryAppForPackage(packageName: String): LocalLibraryApp? = _uiState.value.libraryApps.firstOrNull { it.model.packageName == packageName }?.model

    private fun streamProfileForApp(app: LocalLibraryApp?): StreamProfile {
        val settings = _uiState.value.performanceSettings
        val overrideId = app?.profileOverrideId
        if (!overrideId.isNullOrBlank()) {
            return StreamProfiles.all.firstOrNull { it.id == overrideId } ?: settings.selectedProfile
        }
        return settings.selectedProfile
    }

    fun launchOnly(app: LocalLibraryApp) {
        launchApp(app, startConsole = false)
    }

    fun launchInConsoleMode(app: LocalLibraryApp, permissionGranted: Boolean) {
        _uiState.value = _uiState.value.copy(pendingLaunchApp = app)
        if (_uiState.value.streamState == StreamSessionState.Active) {
            launchApp(app, startConsole = true)
            return
        }
        startConsoleMode(permissionGranted)
        if (_uiState.value.lastError == null && _uiState.value.streamState == StreamSessionState.Active) {
            launchApp(app, startConsole = true)
        }
    }

    fun launchPendingAfterConsoleStart() {
        val app = _uiState.value.pendingLaunchApp ?: return
        if (_uiState.value.streamState == StreamSessionState.Active) {
            _uiState.value = _uiState.value.copy(pendingLaunchApp = null)
            launchApp(app, startConsole = true)
        }
    }

    /**
     * Normal Azahar launch: start Console Mode like other “Launch on Screen” apps, then open Azahar.
     * @return true when the UI should show screen-capture consent (stream not active yet).
     */
    fun launchAzahar(): Boolean {
        val packageName = resolveInstalledAzaharPackage()
        if (packageName == null) {
            _uiState.value = _uiState.value.copy(status = "NoctDock Azahar is not installed.")
            updateAzaharDiagnostics(packageInstalled = false, launchSent = false)
            return false
        }
        val app = libraryAppForPackage(packageName)
        if (app == null) {
            _uiState.value = _uiState.value.copy(status = "NoctDock Azahar is not in your library yet. Refresh Library in Settings.")
            updateAzaharDiagnostics(packageInstalled = true, launchSent = false)
            return false
        }
        val streamActive = _uiState.value.streamState == StreamSessionState.Active
        launchInConsoleMode(app, permissionGranted = streamActive)
        return !streamActive
    }

    fun launchAzahar3dsMode() {
        val receiver = _uiState.value.defaultReceiver
        val trusted = _uiState.value.trustedReceiver
        val azaharPackageName = resolveInstalledAzaharPackage()
        val azaharInstalled = _uiState.value.azaharStatus.installed || azaharPackageName != null
        when (
            NoctDockAzaharContract.preflight(
                azaharInstalled = azaharInstalled,
                receiverSelected = receiver != null,
                receiverOnline = receiver?.isOnline == true,
                receiverTrusted = receiver != null && trusted?.identity?.id == receiver.identity.id,
            )
        ) {
            NoctDockAzaharPreflightResult.AZAHAR_MISSING -> {
                _uiState.value = _uiState.value.copy(status = "NoctDock Azahar is not installed.")
                updateAzaharDiagnostics(packageInstalled = false, launchSent = false)
                return
            }

            NoctDockAzaharPreflightResult.RECEIVER_REQUIRED -> {
                setError(NoctError.NoReceiverFound)
                updateAzaharDiagnostics(packageInstalled = true, launchSent = false)
                startDiscovery()
                return
            }

            NoctDockAzaharPreflightResult.RECEIVER_NOT_READY -> {
                _uiState.value = _uiState.value.copy(status = "Your screen is not ready yet.")
                updateAzaharDiagnostics(packageInstalled = true, receiver = receiver, launchSent = false)
                return
            }

            NoctDockAzaharPreflightResult.RECEIVER_NOT_TRUSTED -> {
                requireNotNull(receiver)
                connect(receiver)
                _uiState.value = _uiState.value.copy(status = "Pair with this screen before starting 3DS Mode.")
                updateAzaharDiagnostics(packageInstalled = true, receiver = receiver, launchSent = false)
                return
            }

            NoctDockAzaharPreflightResult.READY -> Unit
        }

        val activeReceiver = requireNotNull(receiver)
        val details = azaharLaunchDetails(activeReceiver)
        stopConsoleMode()
        val launchPackageName = azaharPackageName ?: resolveInstalledAzaharPackage()
        val intent = launchPackageName?.let {
            Intent(NoctDockAzaharContract.ACTION_THREE_DS_MODE)
                .setComponent(ComponentName(it, "org.citra.citra_emu.ui.main.MainActivity"))
        }
        if (intent == null) {
            _uiState.value = _uiState.value.copy(status = "NoctDock Azahar is not installed.")
            updateAzaharDiagnostics(packageInstalled = false, receiver = activeReceiver, details = details, launchSent = false)
            return
        }
        intent
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(NoctDockAzaharContract.EXTRA_MODE, details.mode)
            .putExtra(NoctDockAzaharContract.EXTRA_RECEIVER_NAME, details.receiverName)
            .putExtra(NoctDockAzaharContract.EXTRA_RECEIVER_ADDRESS, details.receiverAddress)
            .putExtra(NoctDockAzaharContract.EXTRA_RECEIVER_PORT, details.receiverPort)
            .putExtra(NoctDockAzaharContract.EXTRA_PREFERRED_CODEC, details.preferredCodecValue)
            .putExtra(NoctDockAzaharContract.EXTRA_SOUND_MODE, details.soundModeValue)
            .putExtra(NoctDockAzaharContract.EXTRA_PROMPT_USER, details.promptUser)
        runCatching { app.startActivity(intent) }
            .onSuccess {
                updateAzaharDiagnostics(packageInstalled = true, receiver = activeReceiver, details = details, launchSent = true)
                _uiState.value =
                    _uiState.value.copy(
                        status = "NoctDock Azahar opened. Top Screen to Screen is ready.",
                        foregroundPackage = launchPackageName,
                        lastError = null,
                    )
            }
            .onFailure {
                setError(NoctError.AppLaunchFailed)
                updateAzaharDiagnostics(packageInstalled = true, receiver = activeReceiver, details = details, launchSent = false)
            }
    }

    private fun azaharLaunchDetails(receiver: DiscoveredReceiver): NoctDockAzaharLaunchDetails {
        val settings = _uiState.value.performanceSettings
        val profile = settings.selectedProfile
        val preferredCodec =
            NoctDockAzaharContract.resolvePreferredCodec(
                profile = profile,
                sender = senderVideoCapabilities(),
                receiver = receiver.videoCapabilities,
                hevcAllowedByDevice =
                DeviceCapabilityDetector.allowsHevc(
                    _uiState.value.deviceProfile,
                    _uiState.value.encoderCapabilitySummary,
                ),
            )
        return NoctDockAzaharLaunchDetails(
            receiverName = receiver.displayName,
            receiverAddress = receiver.hostAddress,
            receiverPort = receiver.port,
            preferredCodec = preferredCodec,
            soundMode = settings.soundMode,
        )
    }

    private fun updateAzaharDiagnostics(packageInstalled: Boolean, receiver: DiscoveredReceiver? = null, details: NoctDockAzaharLaunchDetails? = null, launchSent: Boolean) {
        _uiState.value =
            _uiState.value.copy(
                azaharLaunchDiagnostics =
                AzaharLaunchDiagnostics(
                    packageInstalled = packageInstalled,
                    receiverAddressPassed = details?.receiverAddress ?: receiver?.hostAddress ?: "Not sent",
                    selectedCodec = details?.preferredCodecValue ?: "Not selected",
                    soundMode = details?.soundModeValue ?: "Not selected",
                    launchIntentSent = launchSent,
                ),
            )
    }

    private fun launchApp(appModel: LocalLibraryApp, startConsole: Boolean) {
        launchPackage(appModel.packageName, appModel)
        if (startConsole && _uiState.value.streamState == StreamSessionState.Active) {
            _uiState.value =
                _uiState.value.copy(
                    consoleModeState = ConsoleModeState.Streaming,
                    foregroundPackage = appModel.packageName,
                )
        }
    }

    private fun launchPackage(packageName: String, appModel: LocalLibraryApp? = null) {
        val intent = app.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            setError(NoctError.AppLaunchFailed)
            return
        }
        if (appModel != null) {
            viewModelScope.launch {
                libraryStore.recordLaunch(appModel.packageName, System.currentTimeMillis())
                refreshLibrary()
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
            .onFailure { setError(NoctError.AppLaunchFailed) }
    }

    fun selectProfile(profileId: String) {
        val next = _uiState.value.performanceSettings.copy(selectedProfileId = profileId)
        saveSettings(next)
    }

    fun updateOverlay(enabled: Boolean) = saveSettings(_uiState.value.performanceSettings.copy(showStreamOverlay = enabled))

    fun updateLowLatency(enabled: Boolean) = saveSettings(_uiState.value.performanceSettings.copy(preferLowLatencyCodec = enabled))

    fun updateKeyframeInterval(value: Int) = saveSettings(_uiState.value.performanceSettings.copy(keyframeIntervalSeconds = value.coerceIn(1, 5)))

    fun updateQueueSize(value: Int) = saveSettings(_uiState.value.performanceSettings.copy(maxQueueSize = value.coerceIn(1, 8)))

    fun updateFrameDropping(enabled: Boolean) = saveSettings(_uiState.value.performanceSettings.copy(allowFrameDropping = enabled))

    fun updateManualBitrate(value: Int?) = saveSettings(_uiState.value.performanceSettings.copy(manualBitrateMbps = value?.coerceIn(4, 55)))

    fun updateBatterySaver(enabled: Boolean) = saveSettings(_uiState.value.performanceSettings.copy(batterySaverMode = enabled))

    fun updateAdaptiveBitrate(enabled: Boolean) = saveSettings(_uiState.value.performanceSettings.copy(adaptiveBitrateEnabled = enabled))

    fun updateSoundMode(mode: SoundMode) = saveSettings(_uiState.value.performanceSettings.copy(soundMode = mode))

    fun updateLowerHandheldInTvSound(enabled: Boolean) = saveSettings(_uiState.value.performanceSettings.copy(lowerHandheldInTvSound = enabled))

    private fun saveSettings(settings: PerformanceSettings) {
        _uiState.value = _uiState.value.copy(performanceSettings = settings)
        viewModelScope.launch { settingsStore.save(settings) }
    }

    fun updateReducedMotion(enabled: Boolean) = saveAppearance(_uiState.value.appearanceSettings.copy(reducedMotion = enabled))

    fun updateBackgroundTheme(theme: NebulaTheme) = saveAppearance(_uiState.value.appearanceSettings.copy(backgroundTheme = theme))

    fun updateBackgroundMotionMode(mode: BackgroundMotionMode) = saveAppearance(_uiState.value.appearanceSettings.copy(backgroundMotionMode = mode))

    fun updateAccentTheme(theme: AccentTheme) = saveAppearance(_uiState.value.appearanceSettings.copy(accentTheme = theme))

    fun updateDensity(density: UiDensity) = saveAppearance(_uiState.value.appearanceSettings.copy(uiDensity = density))

    fun updateHaptics(enabled: Boolean) = saveAppearance(_uiState.value.appearanceSettings.copy(hapticsEnabled = enabled))
    fun updateScreenCloakMode(mode: ScreenCloakMode) = saveAppearance(_uiState.value.appearanceSettings.copy(screenCloakMode = mode)).also {
        pushScreenCloakUpdate(mode = mode)
    }

    fun updateScreenCloakOverlayBlocked(disabled: Boolean) = saveAppearance(_uiState.value.appearanceSettings.copy(screenCloakOverlayDisabledDueToTvPictureIssue = disabled)).also {
        pushScreenCloakUpdate(overlayDisabled = disabled)
    }

    fun refreshScreenCloakTest() {
        if (_uiState.value.streamState != StreamSessionState.Active) return
        app.startService(
            Intent(app, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_TEST_SCREEN_CLOAK
                putExtra(ScreenCaptureService.EXTRA_SCREEN_CLOAK_MODE, _uiState.value.appearanceSettings.screenCloakMode.name)
                putExtra(
                    ScreenCaptureService.EXTRA_SCREEN_CLOAK_OVERLAY_DISABLED,
                    _uiState.value.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue,
                )
            },
        )
    }

    fun updateAutoReconnect(enabled: Boolean) {
        saveAppearance(_uiState.value.appearanceSettings.copy(autoReconnect = enabled))
        publishReceivers(_uiState.value.discoveryState)
    }

    fun updateRememberLastReceiver(enabled: Boolean) {
        saveAppearance(_uiState.value.appearanceSettings.copy(rememberLastReceiver = enabled))
        if (!enabled) viewModelScope.launch { trustStore.clear() }
        publishReceivers(_uiState.value.discoveryState)
    }

    private fun saveAppearance(settings: AppearanceSettings) {
        _uiState.value = _uiState.value.copy(appearanceSettings = settings)
        viewModelScope.launch { settingsStore.saveAppearance(settings) }
    }

    private fun pushScreenCloakUpdate(
        mode: ScreenCloakMode = _uiState.value.appearanceSettings.screenCloakMode,
        overlayDisabled: Boolean = _uiState.value.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue,
    ) {
        if (_uiState.value.streamState != StreamSessionState.Active) return
        app.startService(
            Intent(app, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_UPDATE_SCREEN_CLOAK
                putExtra(ScreenCaptureService.EXTRA_SCREEN_CLOAK_MODE, mode.name)
                putExtra(ScreenCaptureService.EXTRA_SCREEN_CLOAK_OVERLAY_DISABLED, overlayDisabled)
            },
        )
    }

    fun diagnosticsSnapshot(): DiagnosticsSnapshot = DiagnosticsSnapshot(
        receiverName = _uiState.value.defaultReceiver?.displayName ?: "None",
        connectionState = _uiState.value.consoleModeState,
        streamState = _uiState.value.streamState,
        encoderName = _uiState.value.encoderName,
        decoderFeedback = "No screen feedback",
        metrics = _uiState.value.metrics,
        soundMode = _uiState.value.performanceSettings.soundMode,
        deviceProfile = _uiState.value.deviceProfile.displayName,
        deviceTier = _uiState.value.deviceProfile.tier,
        handheldTier = _uiState.value.deviceProfile.handheldTier,
        deviceSupportLevel = _uiState.value.deviceProfile.supportLevel,
        recommendedProfile =
        _uiState.value.deviceProfile.recommendedNoctDockProfile.friendlyLabel,
        encoderCapability = _uiState.value.encoderCapabilitySummary,
        backgroundMode =
        BackgroundAmbiencePolicy.effectiveModeLabel(
            motionMode = _uiState.value.appearanceSettings.backgroundMotionMode,
            reducedMotion = _uiState.value.appearanceSettings.reducedMotion,
        ),
        reducedMotion = _uiState.value.appearanceSettings.reducedMotion,
        batterySaverMode = _uiState.value.performanceSettings.batterySaverMode,
        selectedCodec = VideoCodec.fromMime(_uiState.value.metrics.codecMime),
        requestedResolution = "${_uiState.value.metrics.requestedWidth}x${_uiState.value.metrics.requestedHeight}",
        actualEncoderResolution = "${_uiState.value.metrics.actualEncoderWidth}x${_uiState.value.metrics.actualEncoderHeight}",
        virtualDisplayResolution = "${_uiState.value.metrics.virtualDisplayWidth}x${_uiState.value.metrics.virtualDisplayHeight}",
        configuredBitrateMbps = _uiState.value.metrics.configuredBitrateMbps,
        receiverDecoderMime = _uiState.value.metrics.receiverDecoderMime.ifBlank { "Unknown" },
        receiverSurfaceResolution = "${_uiState.value.metrics.receiverSurfaceWidth}x${_uiState.value.metrics.receiverSurfaceHeight}",
        connectionTestResult = _uiState.value.connectionTestResult,
        lastError = _uiState.value.lastError,
        screenCloakStatus =
        _uiState.value.screenCloakStatus.copy(
            mode = _uiState.value.appearanceSettings.screenCloakMode,
            disabledDueToTvPictureIssue = _uiState.value.appearanceSettings.screenCloakOverlayDisabledDueToTvPictureIssue,
        ),
        discoveryLifecycleState = _uiState.value.discoveryLifecycleState,
        broadcasting = _uiState.value.discoveryState == DiscoveryState.Scanning,
        lastBroadcastRestartLabel = if (_uiState.value.discoveryRefreshing) "Refreshing" else "Active",
        discoveryStateLabel = _uiState.value.discoveryStateLabel,
        refreshRateHelperStatus = _uiState.value.refreshRateHelperStatus,
    )

    fun streamQualityConfig(): StreamQualityConfig = StreamQualityConfig(
        adaptiveBitrateEnabled = _uiState.value.performanceSettings.adaptiveBitrateEnabled,
        batterySaverMode = _uiState.value.performanceSettings.batterySaverMode,
        packetPacingEnabled = true,
        thermalProtectionEnabled = true,
        diagnosticsVerbose = false,
    )

    fun supportReportText(): String = formatSupportReport(
        metadata =
        NoctSupportReportMetadata(
            role = "Sender",
            appVersion = BuildConfig.VERSION_NAME,
            buildType = BuildConfig.BUILD_TYPE,
            debugLogsEnabled = BuildConfig.NOCT_DEBUG_LOGS,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT,
        ),
        diagnosticsSection = diagnosticsSnapshot().exportText(),
        recentLogs = NoctLog.recentEntries(),
    )

    fun copySupportReportToClipboard() {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("NoctDock support report", supportReportText()))
        _uiState.value = _uiState.value.copy(diagnosticsCopied = true)
    }

    fun copyDiagnosticsToClipboard() = copySupportReportToClipboard()

    fun setError(error: NoctError) {
        NoctLog.warn("Sender", error.message)
        _uiState.value =
            _uiState.value.copy(
                consoleModeState = ConsoleModeState.Error,
                streamState = StreamSessionState.Failed,
                lastError = error,
                status = error.message,
            )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(lastError = null)
    }

    private suspend fun probeReceiverVideoCapabilities(host: String, port: Int): ReceiverVideoCapabilities = withContext(Dispatchers.IO) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 900
                val query =
                    ReceiverCapabilitiesPacket(
                        streamId = 0,
                        capabilities = ReceiverVideoCapabilities(),
                    )
                val bytes = PacketCodec.encodeReceiverCapabilities(query)
                socket.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), port))
                val buffer = ByteArray(PacketCodec.MAX_DATAGRAM_SIZE)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                require(PacketCodec.isNoctDockPacket(response.data, response.length)) { "Not a NoctDock packet" }
                val fragment = PacketCodec.decode(response.data, response.length)
                require(fragment.header.type == PacketType.RECEIVER_CAPABILITIES) { "Not a capabilities response" }
                PacketCodec.decodeReceiverCapabilities(fragment).capabilities
            }
        }.getOrElse {
            ReceiverVideoCapabilities(
                supportsAvc = true,
                supportsHevc = false,
                maxWidth = 1280,
                maxHeight = 720,
                maxFps = 60,
            )
        }
    }

    private fun sendAndReceive(host: String, port: Int, packet: PairingPacket): PairingPacket? = runCatching {
        DatagramSocket().use { socket ->
            socket.soTimeout = 2500
            val payload = json.encodeToString(packet).toByteArray()
            socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(host), port))
            val buffer = ByteArray(2048)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            json.decodeFromString<PairingPacket>(String(response.data, response.offset, response.length))
        }
    }.getOrNull()

    override fun onCleared() {
        stopDiscovery()
        pairingJob?.cancel()
        super.onCleared()
    }
}

private fun LocalLibraryApp.isAutoEmulator(): Boolean {
    val text = searchableText
    return listOf(
        "retroarch",
        "azahar",
        "dolphin",
        "ppsspp",
        "aethersx2",
        "nether",
        "duckstation",
        "citra",
        "yuzu",
        "sudachi",
        "emulator",
    ).any { it in text }
}

class SenderTrustStore(private val context: Context, private val json: Json) {
    private val trustedKey = stringPreferencesKey("trusted_receiver")

    suspend fun trustedReceiver(): TrustedReceiver? = context.senderDataStore.data
        .map { preferences -> preferences[trustedKey]?.let { json.decodeFromString<TrustedReceiver>(it) } }
        .first()

    suspend fun save(receiver: TrustedReceiver) {
        context.senderDataStore.edit { preferences ->
            preferences[trustedKey] = json.encodeToString(receiver)
        }
    }

    suspend fun clear() {
        context.senderDataStore.edit { preferences ->
            preferences.remove(trustedKey)
        }
    }
}

class SenderLibraryStore(private val context: Context, private val json: Json) {
    private val favouritesKey = stringSetPreferencesKey("library_favourites")
    private val manualAppsKey = stringSetPreferencesKey("library_manual_apps")
    private val recentKey = stringPreferencesKey("library_recent")
    private val profileOverridesKey = stringPreferencesKey("library_profile_overrides")

    suspend fun favourites(): Set<String> = context.senderDataStore.data.map { it[favouritesKey] ?: emptySet() }.first()

    suspend fun manualApps(): Set<String> = context.senderDataStore.data.map { it[manualAppsKey] ?: emptySet() }.first()

    suspend fun recent(): Map<String, Long> = context.senderDataStore.data
        .map { preferences -> preferences[recentKey]?.let { json.decodeFromString<Map<String, Long>>(it) } ?: emptyMap() }
        .first()

    suspend fun profileOverrides(): Map<String, String> = context.senderDataStore.data
        .map { preferences ->
            preferences[profileOverridesKey]?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
        }
        .first()

    suspend fun setAppProfileOverride(packageName: String, profileId: String?) {
        context.senderDataStore.edit { preferences ->
            val current =
                preferences[profileOverridesKey]?.let { json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
            val next = current.toMutableMap()
            if (profileId.isNullOrBlank()) {
                next.remove(packageName)
            } else {
                next[packageName] = profileId
            }
            preferences[profileOverridesKey] = json.encodeToString(next)
        }
    }

    suspend fun setFavourite(packageName: String, favourite: Boolean) {
        context.senderDataStore.edit { preferences ->
            val next = (preferences[favouritesKey] ?: emptySet()).toMutableSet()
            if (favourite) next += packageName else next -= packageName
            preferences[favouritesKey] = next
        }
    }

    suspend fun setManualApp(packageName: String, added: Boolean) {
        context.senderDataStore.edit { preferences ->
            val next = (preferences[manualAppsKey] ?: emptySet()).toMutableSet()
            if (added) next += packageName else next -= packageName
            preferences[manualAppsKey] = next
        }
    }

    suspend fun recordLaunch(packageName: String, launchedAtMillis: Long) {
        context.senderDataStore.edit { preferences ->
            val current = preferences[recentKey]?.let { json.decodeFromString<Map<String, Long>>(it) } ?: emptyMap()
            preferences[recentKey] =
                json.encodeToString(
                    (current + (packageName to launchedAtMillis))
                        .entries
                        .sortedByDescending { it.value }
                        .take(30)
                        .associate { it.key to it.value },
                )
        }
    }
}

class SenderSettingsStore(private val context: Context, private val json: Json) {
    private val settingsKey = stringPreferencesKey("performance_settings")
    private val appearanceKey = stringPreferencesKey("appearance_settings")
    private val capabilityCacheKey = stringPreferencesKey("device_capability_cache")
    private val connectionTestKey = stringPreferencesKey("connection_test_result")

    suspend fun settings(): PerformanceSettings = context.senderDataStore.data
        .map { preferences ->
            preferences[settingsKey]?.let { json.decodeFromString<PerformanceSettings>(it) } ?: PerformanceSettings()
        }
        .first()

    suspend fun settingsOrNull(): PerformanceSettings? = context.senderDataStore.data
        .map { preferences -> preferences[settingsKey]?.let { json.decodeFromString<PerformanceSettings>(it) } }
        .first()

    suspend fun save(settings: PerformanceSettings) {
        context.senderDataStore.edit { preferences ->
            preferences[settingsKey] = json.encodeToString(settings)
        }
    }

    suspend fun appearance(): AppearanceSettings = context.senderDataStore.data
        .map { preferences ->
            preferences[appearanceKey]?.let { json.decodeFromString<AppearanceSettings>(it) } ?: AppearanceSettings()
        }
        .first()

    suspend fun saveAppearance(settings: AppearanceSettings) {
        context.senderDataStore.edit { preferences ->
            preferences[appearanceKey] = json.encodeToString(settings)
        }
    }

    suspend fun deviceCapabilityCache(appVersion: String, deviceFingerprint: String): DeviceCapabilityCache? = context.senderDataStore.data
        .map { preferences ->
            preferences[capabilityCacheKey]
                ?.let { runCatching { json.decodeFromString<DeviceCapabilityCache>(it) }.getOrNull() }
                ?.takeIf { it.isValid(appVersion, deviceFingerprint) }
        }
        .first()

    suspend fun saveDeviceCapabilityCache(cache: DeviceCapabilityCache) {
        context.senderDataStore.edit { preferences ->
            preferences[capabilityCacheKey] = json.encodeToString(cache)
        }
    }

    suspend fun connectionTestResult(): ConnectionTestResult? = context.senderDataStore.data
        .map { preferences -> preferences[connectionTestKey]?.let { json.decodeFromString<ConnectionTestResult>(it) } }
        .first()

    suspend fun saveConnectionTestResult(result: ConnectionTestResult) {
        context.senderDataStore.edit { preferences ->
            preferences[connectionTestKey] = json.encodeToString(result)
        }
    }
}
